/*
 * QSPI Flash Audio Driver - Implementation
 */

#include "audio_qspi.h"

AudioQSPI qspiAudio;

// ── Event → QSPI index mapping ───────────────────────────────────────────────
// Indices match the prompt order written by generate_qspi_binary.py:
//   0  power_on          1  last_set            2  next_set_low
//   3  next_set_medium   4  next_set_high        5-16  summary matrix
// 0xFF means "no stored audio" (synthesised tone event).

static const uint8_t kEventToIndex[] = {
    0xFF, // 0x00 – unused
    0,    // 0x01 – AUDIO_POWER_ON
    0xFF, // 0x02 – AUDIO_SESSION_START_BEEP  (tone)
    0xFF, // 0x03 – AUDIO_SET_CHANGEOVER_BEEP (tone)
    1,    // 0x04 – AUDIO_LAST_SET
    2,    // 0x05 – AUDIO_NEXT_SET_LOW
    3,    // 0x06 – AUDIO_NEXT_SET_MEDIUM
    4,    // 0x07 – AUDIO_NEXT_SET_HIGH
    5,    // 0x08 – AUDIO_SUMMARY_POOR_LIGHT
    6,    // 0x09 – AUDIO_SUMMARY_POOR_MODERATE
    7,    // 0x0A – AUDIO_SUMMARY_POOR_STRONG
    8,    // 0x0B – AUDIO_SUMMARY_POOR_MAXIMUM
    9,    // 0x0C – AUDIO_SUMMARY_GOOD_LIGHT
    10,   // 0x0D – AUDIO_SUMMARY_GOOD_MODERATE
    11,   // 0x0E – AUDIO_SUMMARY_GOOD_STRONG
    12,   // 0x0F – AUDIO_SUMMARY_GOOD_MAXIMUM
    13,   // 0x10 – AUDIO_SUMMARY_EXCELLENT_LIGHT
    14,   // 0x11 – AUDIO_SUMMARY_EXCELLENT_MODERATE
    15,   // 0x12 – AUDIO_SUMMARY_EXCELLENT_STRONG
    16,   // 0x13 – AUDIO_SUMMARY_EXCELLENT_MAXIMUM
};

int8_t audioEventToQSPIIndex(uint8_t audioEvent) {
    if (audioEvent >= sizeof(kEventToIndex)) return -1;
    uint8_t idx = kEventToIndex[audioEvent];
    return (idx == 0xFF) ? -1 : (int8_t)idx;
}

// ── AudioQSPI::begin ─────────────────────────────────────────────────────────

bool AudioQSPI::begin() {
    if (_initialized) return _valid;
    _initialized = true;

    // Construct transport with explicit pin numbers so this compiles even
    // when the BSP's PIN_QSPI_* fallback values differ from the defaults.
    _transport = Adafruit_FlashTransport_QSPI(
        PIN_QSPI_SCK, PIN_QSPI_CS,
        PIN_QSPI_IO0, PIN_QSPI_IO1,
        PIN_QSPI_IO2, PIN_QSPI_IO3
    );
    _flash = Adafruit_SPIFlash(&_transport);

    if (!_flash.begin()) {
        Serial.println("QSPI: flash init failed – check wiring / BSP pin definitions");
        return false;
    }

    Serial.print("QSPI: flash detected, size = ");
    Serial.print(_flash.size() / 1024);
    Serial.println(" KB");

    // Read and validate the 16-byte header
    uint8_t hdr[16];
    if (_flash.readBuffer(QSPI_HEADER_OFFSET, hdr, 16) != 16) {
        Serial.println("QSPI: header read failed");
        return false;
    }

    uint32_t magic   = (uint32_t)hdr[0] | ((uint32_t)hdr[1] << 8)
                     | ((uint32_t)hdr[2] << 16) | ((uint32_t)hdr[3] << 24);
    uint16_t version = (uint16_t)hdr[4] | ((uint16_t)hdr[5] << 8);
    uint8_t  count   = hdr[6];

    if (magic != QSPI_AUDIO_MAGIC || version != QSPI_AUDIO_VERSION
            || count != QSPI_PROMPT_COUNT) {
        Serial.println("QSPI: audio not provisioned (run generate_qspi_binary.py and flash audio_qspi.bin)");
        Serial.print("  magic=0x");   Serial.print(magic, HEX);
        Serial.print("  version=0x"); Serial.print(version, HEX);
        Serial.print("  count=");     Serial.println(count);
        return false;
    }

    // Read directory table: 17 × 8 bytes
    uint8_t dirBuf[QSPI_PROMPT_COUNT * 8];
    if (_flash.readBuffer(QSPI_DIR_OFFSET, dirBuf, sizeof(dirBuf)) != sizeof(dirBuf)) {
        Serial.println("QSPI: directory read failed");
        return false;
    }

    for (uint8_t i = 0; i < QSPI_PROMPT_COUNT; i++) {
        const uint8_t* e = dirBuf + (i * 8);
        _directory[i].byteOffset  = (uint32_t)e[0] | ((uint32_t)e[1] << 8)
                                  | ((uint32_t)e[2] << 16) | ((uint32_t)e[3] << 24);
        _directory[i].sampleCount = (uint32_t)e[4] | ((uint32_t)e[5] << 8)
                                  | ((uint32_t)e[6] << 16) | ((uint32_t)e[7] << 24);
    }

    _valid = true;
    Serial.println("QSPI: audio ready (17 prompts, 1.70 MB)");

    // Release QSPI pins after init so I2S can take P0.19 (shared SCK/BCLK)
    NRF_QSPI->TASKS_DEACTIVATE = 1;

    return true;
}

// ── AudioQSPI::getPromptInfo ─────────────────────────────────────────────────

bool AudioQSPI::getPromptInfo(uint8_t index, QSPIPromptEntry& entry) const {
    if (!_valid || index >= QSPI_PROMPT_COUNT) return false;
    entry = _directory[index];
    return true;
}

// ── AudioQSPI::readSamples ───────────────────────────────────────────────────

bool AudioQSPI::readSamples(uint32_t byteOffset, int16_t* buf, uint16_t count) {
    if (!_valid || buf == nullptr || count == 0) return false;

    uint32_t byteLen = (uint32_t)count * 2;
    bool ok = (_flash.readBuffer(byteOffset, reinterpret_cast<uint8_t*>(buf), byteLen) == byteLen);

    // Deactivate QSPI peripheral after each read to release shared pin P0.19
    // (shared with I2S BCLK on the XIAO nRF52840 castellated pads).
    NRF_QSPI->TASKS_DEACTIVATE = 1;

    return ok;
}
