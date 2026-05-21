/*
 * QSPI Flash Audio Driver for Oro Haptic Paddle
 *
 * Stores 17 voice prompts (1.70 MB raw PCM) on the Seeed XIAO nRF52840's
 * onboard 2 MB P25Q16H QSPI flash chip.
 *
 * Flash layout:
 *   0x000000  Header (16 bytes): magic, version, prompt count
 *   0x000010  Directory table (17 × 8 bytes): byte offset + sample count per prompt
 *   0x000100  Audio data start (256-byte aligned)
 *             17 × raw int16_t PCM @ 32 kHz mono, little-endian
 *
 * Requires: Adafruit_SPIFlash library
 *   Arduino Library Manager → search "Adafruit SPIFlash"
 *
 * Pin note: The XIAO nRF52840's QSPI SCK (P0.19) is shared with the
 * castellated I2S BCLK pad. AudioI2S::playStream() stops I2S before
 * each QSPI read and deactivates QSPI before each I2S burst to avoid
 * pin contention.
 */

#ifndef AUDIO_QSPI_H
#define AUDIO_QSPI_H

#include <Arduino.h>
#include <Adafruit_SPIFlash.h>
#include <nrf.h>

// Binary image identification
#define QSPI_AUDIO_MAGIC    0x4F524F41UL  // "AORO" in little-endian
#define QSPI_AUDIO_VERSION  0x0001
#define QSPI_PROMPT_COUNT   17

// Flash layout offsets (bytes)
#define QSPI_HEADER_OFFSET    0x000000
#define QSPI_DIR_OFFSET       0x000010
#define QSPI_DATA_OFFSET      0x000100

// Fallback QSPI pin definitions for Seeed XIAO nRF52840 (P25Q16H flash).
// The Seeed BSP should already define these via PIN_QSPI_* in variant.h.
// If you get a compile error referencing PIN_QSPI_*, verify you have the
// Seeed nRF52 BSP installed and the correct board selected.
#ifndef PIN_QSPI_SCK
  #define PIN_QSPI_SCK  19   // P0.19
  #define PIN_QSPI_CS   25   // P0.25
  #define PIN_QSPI_IO0  20   // P0.20
  #define PIN_QSPI_IO1  21   // P0.21
  #define PIN_QSPI_IO2  22   // P0.22
  #define PIN_QSPI_IO3  23   // P0.23
#endif

struct QSPIPromptEntry {
    uint32_t byteOffset;   // Absolute byte address in QSPI flash
    uint32_t sampleCount;  // Number of int16_t samples
};

class AudioQSPI {
public:
    bool begin();
    bool isValid() const { return _valid; }

    // Retrieve stored offset + size for prompt index 0-16
    bool getPromptInfo(uint8_t index, QSPIPromptEntry& entry) const;

    // Read 'count' int16_t samples from flash; deactivates QSPI on return
    bool readSamples(uint32_t byteOffset, int16_t* buf, uint16_t count);

private:
    Adafruit_FlashTransport_QSPI _transport;
    Adafruit_SPIFlash            _flash;
    bool                         _initialized = false;
    bool                         _valid       = false;
    QSPIPromptEntry              _directory[QSPI_PROMPT_COUNT];
};

// Singleton used by firmware and AudioI2S
extern AudioQSPI qspiAudio;

// Map an AudioEvent enum value to a QSPI directory index (0-16).
// Returns -1 for events that are synthesised tones (no stored audio).
int8_t audioEventToQSPIIndex(uint8_t audioEvent);

#endif // AUDIO_QSPI_H
