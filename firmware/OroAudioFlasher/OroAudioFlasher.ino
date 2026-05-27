/*
 * Oro Audio Flasher — one-time loader for Session Summary voice prompts.
 *
 * Upload this sketch to a Seeed XIAO nRF52840 Sense, then run
 *     python firmware/flash_audio.py --port COMx
 * on the PC. After the upload completes, re-flash OroHapticFirmware.ino.
 *
 * Serial protocol (host -> device, host waits for "OK" line after each step):
 *   Line "PING\n"                 -> device replies "OROFLASHER v1\n"
 *   Line "ERASE\n"                -> device chip-erases QSPI, replies "ERASED\n"
 *   Line "WRITE <length>\n"       -> device replies "READY\n", host sends <length> bytes,
 *                                   device replies "WROTE <length>\n"
 *   Line "VERIFY <length>\n"      -> device reads first <length> bytes back,
 *                                   replies "MAGIC OROA\n" or "MAGIC BAD <hex>\n"
 *   Line "DONE\n"                 -> device replies "BYE\n"
 *
 * Hardware: Seeed XIAO nRF52840 Sense, on-board P25Q16H QSPI (2 MB).
 * Library:  Adafruit_SPIFlash (built into the Seeed BSP / install via Library Manager).
 */
#include <Adafruit_SPIFlash.h>

#if defined(EXTERNAL_FLASH_USE_QSPI)
  Adafruit_FlashTransport_QSPI flashTransport;
#elif defined(EXTERNAL_FLASH_USE_SPI)
  Adafruit_FlashTransport_SPI  flashTransport(EXTERNAL_FLASH_USE_CS, EXTERNAL_FLASH_USE_SPI);
#else
  #error "External flash transport not defined by the board variant"
#endif

Adafruit_SPIFlash flash(&flashTransport);

// XIAO nRF52840 Sense Plus ships with a Puya P25Q16H (2 MB QSPI).
// Older Adafruit_SPIFlash releases don't have this chip in their default
// device list, so flash.begin() can't auto-detect it. Pass the descriptor
// explicitly. JEDEC: 85 60 15.
static const SPIFlash_Device_t P25Q16H_DEVICE = {
  .total_size                  = (1UL << 21),  // 2 MB
  .start_up_time_us            = 10000,
  .manufacturer_id             = 0x85,
  .memory_type                 = 0x60,
  .capacity                    = 0x15,
  .max_clock_speed_mhz         = 55,
  .quad_enable_bit_mask        = 0x02,
  .has_sector_protection       = false,
  .supports_fast_read          = true,
  .supports_qspi               = true,
  .supports_qspi_writes        = true,
  .write_status_register_split = false,
  .single_status_byte          = true,
  .is_fram                     = false,
};
static const SPIFlash_Device_t XIAO_FLASH_DEVICES[] = { P25Q16H_DEVICE };

static const uint32_t FLASH_BASE = 0x000000;

// Fixed char-buffer line reader. Reads bytes into `buf` until \n, returns
// length (excluding terminator). Skips \r. Truncates at maxLen-1.
static size_t readLineFixed(char* buf, size_t maxLen) {
  size_t n = 0;
  while (true) {
    while (!Serial.available()) { /* spin */ }
    int b = Serial.read();
    if (b < 0) continue;
    char c = (char)b;
    if (c == '\n') { buf[n] = 0; return n; }
    if (c == '\r') continue;
    if (n + 1 < maxLen) buf[n++] = c;
  }
}

// Parse a decimal uint32_t from a C string. Returns true on success.
static bool parseU32(const char* s, uint32_t& out) {
  if (!s || !*s) return false;
  uint32_t v = 0;
  for (; *s; s++) {
    if (*s < '0' || *s > '9') return false;
    v = v * 10 + (uint32_t)(*s - '0');
  }
  out = v;
  return true;
}

static void sendLine(const char* msg) {
  Serial.println(msg);
  Serial.flush();
}

void setup() {
  Serial.begin(115200);
  while (!Serial) { delay(10); }
  sendLine("OROFLASHER v1");

  if (!flash.begin(XIAO_FLASH_DEVICES, 1)) {
    sendLine("ERR flash.begin failed");
    while (1) { delay(1000); }
  }
  Serial.print("JEDEC ID 0x"); Serial.println(flash.getJEDECID(), HEX);
  Serial.print("Size bytes "); Serial.println(flash.size());
  Serial.flush();
}

void loop() {
  char line[64];
  size_t n = readLineFixed(line, sizeof(line));

  // Diagnostic echo so the host can see exactly what line we parsed.
  Serial.print("DBG got '"); Serial.print(line); Serial.print("' len=");
  Serial.println((unsigned)n);
  Serial.flush();

  if (strcmp(line, "PING") == 0) {
    sendLine("OROFLASHER v1");
  }
  else if (strcmp(line, "ERASE") == 0) {
    if (!flash.eraseChip()) { sendLine("ERR erase"); return; }
    flash.waitUntilReady();
    sendLine("ERASED");
  }
  else if (strncmp(line, "WRITE ", 6) == 0) {
    uint32_t len = 0;
    if (!parseU32(line + 6, len)) { sendLine("ERR write parse"); return; }
    if (len == 0 || len > flash.size()) {
      Serial.print("ERR length "); Serial.println(len);
      Serial.flush();
      return;
    }
    sendLine("READY");
    uint32_t addr = FLASH_BASE;
    uint8_t buf[256];
    uint32_t remaining = len;
    while (remaining > 0) {
      uint32_t chunk = remaining > sizeof(buf) ? sizeof(buf) : remaining;
      uint32_t got = 0;
      uint32_t last = millis();
      while (got < chunk) {
        int b = Serial.read();
        if (b < 0) {
          if (millis() - last > 5000) {
            Serial.print("ERR rx timeout at 0x"); Serial.println(addr, HEX);
            Serial.flush();
            return;
          }
          continue;
        }
        buf[got++] = (uint8_t)b;
        last = millis();
      }
      if (flash.writeBuffer(addr, buf, chunk) != chunk) {
        Serial.print("ERR write at 0x"); Serial.println(addr, HEX);
        Serial.flush();
        return;
      }
      addr += chunk;
      remaining -= chunk;
    }
    flash.waitUntilReady();
    Serial.print("WROTE "); Serial.println(len);
    Serial.flush();
  }
  else if (strncmp(line, "VERIFY ", 7) == 0) {
    uint8_t magic[4];
    flash.readBuffer(FLASH_BASE, magic, 4);
    if (magic[0]=='O' && magic[1]=='R' && magic[2]=='O' && magic[3]=='A') {
      sendLine("MAGIC OROA");
    } else {
      Serial.print("MAGIC BAD ");
      for (int i=0;i<4;i++) { if (magic[i]<16) Serial.print('0'); Serial.print(magic[i], HEX); }
      Serial.println();
      Serial.flush();
    }
  }
  else if (strcmp(line, "DONE") == 0) {
    sendLine("BYE");
  }
  else if (n > 0) {
    Serial.print("ERR unknown '"); Serial.print(line); Serial.println("'");
    Serial.flush();
  }
}
