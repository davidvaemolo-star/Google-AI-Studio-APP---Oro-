/*
 * external_audio — Session Summary voice prompts stored on external QSPI flash.
 *
 * The blob layout is documented in
 *   docs/superpowers/plans/2026-05-27-session-summary-prompts-external-flash.md
 *
 * Usage:
 *   externalAudio.begin();                         // call once in setup()
 *   externalAudio.playClip(audioEventId, vol, audioPlayer);  // returns false on failure
 *                                                            // caller falls back to a tone/chime
 */
#ifndef EXTERNAL_AUDIO_H
#define EXTERNAL_AUDIO_H

#include <Arduino.h>

#define EXT_AUDIO_MAX_PROMPTS 64  // 20 Crew Roll-Call (ADR-0016) + 3 session (ADR-0017) + 22 Canoe Speed (ADR-0018) = 45, rounded up for headroom. Must be >= the blob's clip count or begin() rejects the whole blob.

class AudioI2S;  // forward decl

struct ExternalAudioEntry {
  uint8_t  eventId;
  uint8_t  sampleRateCode;  // 0 = 16 kHz
  uint16_t reserved;
  uint32_t offset;
  uint32_t sampleCount;
};

class ExternalAudio {
public:
  // Mount external QSPI, read & validate index header.
  // Returns true on success. Safe to call multiple times.
  bool begin();

  // True if begin() succeeded and at least one entry is loaded.
  bool ready() const { return _ready; }

  // Play the clip matching `audioEventId` through `player`.
  // Returns false if begin() not ready, eventId not found, or any read fails.
  bool playClip(uint8_t audioEventId, uint8_t volume, AudioI2S& player);

private:
  bool _ready = false;
  uint16_t _count = 0;
  ExternalAudioEntry _entries[EXT_AUDIO_MAX_PROMPTS];

  // Read `len` bytes from QSPI offset `addr` into `dst`. True on success.
  bool _read(uint32_t addr, void* dst, uint32_t len);

  // Look up an entry by eventId. Returns nullptr if not found.
  const ExternalAudioEntry* _find(uint8_t eventId) const;
};

extern ExternalAudio externalAudio;

#endif // EXTERNAL_AUDIO_H
