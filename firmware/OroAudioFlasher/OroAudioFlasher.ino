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

static void waitForLine(String &out) {
  out = "";
  while (true) {
    while (!Serial.available()) { /* spin */ }
    char c = (char)Serial.read();
    if (c == '\n') return;
    if (c != '\r') out += c;
  }
}

void setup() {
  Serial.begin(115200);
  while (!Serial) { delay(10); }
  Serial.println("OROFLASHER v1");

  if (!flash.begin(XIAO_FLASH_DEVICES, 1)) {
    Serial.println("ERR flash.begin failed");
    while (1) { delay(1000); }
  }
  Serial.print("JEDEC ID 0x"); Serial.println(flash.getJEDECID(), HEX);
  Serial.print("Size bytes "); Serial.println(flash.size());
}

void loop() {
  String line;
  waitForLine(line);

  if (line == "PING") {
    Serial.println("OROFLASHER v1");
  }
  else if (line == "ERASE") {
    if (!flash.eraseChip()) { Serial.println("ERR erase"); return; }
    flash.waitUntilReady();
    Serial.println("ERASED");
  }
  else if (line.startsWith("WRITE ")) {
    uint32_t len = (uint32_t)line.substring(6).toInt();
    if (len == 0 || len > flash.size()) {
      Serial.print("ERR length "); Serial.println(len);
      return;
    }
    Serial.println("READY");
    uint32_t addr = FLASH_BASE;
    uint8_t buf[256];
    uint32_t remaining = len;
    while (remaining > 0) {
      uint32_t chunk = remaining > sizeof(buf) ? sizeof(buf) : remaining;
      uint32_t got = 0;
      while (got < chunk) {
        int b = Serial.read();
        if (b < 0) continue;
        buf[got++] = (uint8_t)b;
      }
      if (flash.writeBuffer(addr, buf, chunk) != (int)chunk) {
        Serial.print("ERR write at 0x"); Serial.println(addr, HEX);
        return;
      }
      addr += chunk;
      remaining -= chunk;
    }
    flash.waitUntilReady();
    Serial.print("WROTE "); Serial.println(len);
  }
  else if (line.startsWith("VERIFY ")) {
    uint32_t len = (uint32_t)line.substring(7).toInt();
    if (len < 4) len = 4;
    uint8_t magic[4];
    flash.readBuffer(FLASH_BASE, magic, 4);
    if (magic[0]=='O' && magic[1]=='R' && magic[2]=='O' && magic[3]=='A') {
      Serial.println("MAGIC OROA");
    } else {
      Serial.print("MAGIC BAD ");
      for (int i=0;i<4;i++) { if (magic[i]<16) Serial.print('0'); Serial.print(magic[i], HEX); }
      Serial.println();
    }
  }
  else if (line == "DONE") {
    Serial.println("BYE");
  }
  else if (line.length() > 0) {
    Serial.print("ERR unknown "); Serial.println(line);
  }
}
