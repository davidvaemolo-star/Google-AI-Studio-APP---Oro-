#include "external_audio.h"
#include "audio_i2s.h"
#include <Adafruit_SPIFlash.h>

#if defined(EXTERNAL_FLASH_USE_QSPI)
  static Adafruit_FlashTransport_QSPI s_xport;
#elif defined(EXTERNAL_FLASH_USE_SPI)
  static Adafruit_FlashTransport_SPI s_xport(EXTERNAL_FLASH_USE_CS, EXTERNAL_FLASH_USE_SPI);
#else
  #error "External flash transport not defined by the board variant"
#endif

static Adafruit_SPIFlash s_flash(&s_xport);

// XIAO nRF52840 Sense Plus ships with a Puya P25Q16H (2 MB QSPI).
// Older Adafruit_SPIFlash releases don't have it in their default device list,
// so flash.begin() can't auto-detect. Pass the descriptor explicitly.
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
  // The Puya P25Q16H keeps its Quad-Enable (QE) bit in Status Register-2, so it
  // is NOT a single-status-byte chip. Marking it as one stops the library from
  // ever setting QE, which makes quad reads return garbage (0x88...) — the chip
  // reads back as "bad magic" even when the prompts are present. Must be false.
  .single_status_byte          = false,
  .is_fram                     = false,
};
static const SPIFlash_Device_t XIAO_FLASH_DEVICES[] = { P25Q16H_DEVICE };

ExternalAudio externalAudio;

static const uint32_t BLOB_BASE = 0x000000;
static const uint32_t HEADER_SIZE = 12;
static const uint32_t ENTRY_SIZE = 12;

bool ExternalAudio::_read(uint32_t addr, void* dst, uint32_t len) {
  uint32_t got = s_flash.readBuffer(addr, (uint8_t*)dst, len);
  return got == len;
}

bool ExternalAudio::begin() {
  _ready = false;
  _count = 0;

  if (!s_flash.begin(XIAO_FLASH_DEVICES, 1)) {
    Serial.println("[extAudio] flash.begin() failed");
    return false;
  }
  Serial.print("[extAudio] JEDEC 0x"); Serial.println(s_flash.getJEDECID(), HEX);

  uint8_t header[HEADER_SIZE];
  if (!_read(BLOB_BASE, header, HEADER_SIZE)) {
    Serial.println("[extAudio] header read failed");
    return false;
  }
  if (!(header[0]=='O' && header[1]=='R' && header[2]=='O' && header[3]=='A')) {
    Serial.print("[extAudio] bad magic: ");
    for (int i=0;i<4;i++) { Serial.print((char)header[i]); }
    Serial.println();
    return false;
  }
  uint16_t version = (uint16_t)header[4] | ((uint16_t)header[5] << 8);
  uint16_t count   = (uint16_t)header[6] | ((uint16_t)header[7] << 8);
  if (version != 1) {
    Serial.print("[extAudio] unsupported version "); Serial.println(version);
    return false;
  }
  if (count == 0 || count > EXT_AUDIO_MAX_PROMPTS) {
    Serial.print("[extAudio] bad count "); Serial.println(count);
    return false;
  }

  if (!_read(BLOB_BASE + HEADER_SIZE, _entries, ENTRY_SIZE * count)) {
    Serial.println("[extAudio] index read failed");
    return false;
  }
  _count = count;
  _ready = true;
  Serial.print("[extAudio] ready, prompts="); Serial.println(_count);
  for (uint16_t i = 0; i < _count; i++) {
    Serial.print("  0x"); Serial.print(_entries[i].eventId, HEX);
    Serial.print("  off=0x"); Serial.print(_entries[i].offset, HEX);
    Serial.print("  samples="); Serial.println(_entries[i].sampleCount);
  }
  return true;
}

const ExternalAudioEntry* ExternalAudio::_find(uint8_t eventId) const {
  for (uint16_t i = 0; i < _count; i++) {
    if (_entries[i].eventId == eventId) return &_entries[i];
  }
  return nullptr;
}

namespace {
struct ReadCtx {
  Adafruit_SPIFlash* flash;
  uint32_t addr;
  uint32_t samplesRemaining;
  bool failed;
};

uint32_t fillCb(int16_t* dst, uint32_t maxSamples, void* userdata) {
  ReadCtx* c = static_cast<ReadCtx*>(userdata);
  if (c->failed || c->samplesRemaining == 0) return 0;
  uint32_t want = (c->samplesRemaining < maxSamples) ? c->samplesRemaining : maxSamples;
  uint32_t bytes = want * 2;
  uint32_t got = c->flash->readBuffer(c->addr, (uint8_t*)dst, bytes);
  if (got != bytes) { c->failed = true; return 0; }
  c->addr += bytes;
  c->samplesRemaining -= want;
  return want;
}
} // anon

bool ExternalAudio::playClip(uint8_t audioEventId, uint8_t volume, AudioI2S& player) {
  if (!_ready) {
    Serial.println("[extAudio] not ready");
    return false;
  }
  const ExternalAudioEntry* e = _find(audioEventId);
  if (e == nullptr) {
    Serial.print("[extAudio] no entry for 0x"); Serial.println(audioEventId, HEX);
    return false;
  }
  if (e->sampleRateCode != 0) {
    Serial.print("[extAudio] unsupported rate code "); Serial.println(e->sampleRateCode);
    return false;
  }

  ReadCtx ctx { &s_flash, e->offset, e->sampleCount, false };
  bool played = player.playStreamCallback(&fillCb, &ctx, volume);
  if (!played || ctx.failed) {
    Serial.println("[extAudio] stream read failed mid-playback");
    return false;
  }
  return true;
}
