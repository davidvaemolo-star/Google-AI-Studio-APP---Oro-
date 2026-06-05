/*
 * Audio I2S Driver Implementation
 *
 * This implementation uses nRF52840's I2S peripheral with direct register access
 * Compatible with Arduino/Adafruit nRF52 BSP
 */

#include "audio_i2s.h"
#include <nrf.h>
#include <math.h>
#include <nrf_clock.h>

#ifndef I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV32
#define I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV32 0x70000000UL
#endif

#ifndef I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV21
#define I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV21 0x54000000UL
#endif

#ifndef I2S_CONFIG_RATIO_RATIO_48X
#define I2S_CONFIG_RATIO_RATIO_48X 1UL
#endif

#ifndef I2S_CONFIG_RATIO_RATIO_64X
#define I2S_CONFIG_RATIO_RATIO_64X 2UL
#endif

#ifndef I2S_CONFIG_RATIO_RATIO_96X
#define I2S_CONFIG_RATIO_RATIO_96X 3UL
#endif

#ifndef I2S_CONFIG_RATIO_RATIO_1024X
#define I2S_CONFIG_RATIO_RATIO_1024X 10UL
#endif

#ifndef I2S_CONFIG_ALIGN_ALIGN_Right
#define I2S_CONFIG_ALIGN_ALIGN_Right 1UL
#endif

// I2S register access (available in Adafruit nRF52 core)
#define NRF_I2S_BASE 0x40025000
#define I2S ((NRF_I2S_Type*)NRF_I2S_BASE)

bool AudioI2S::begin() {
    if (initialized) {
        Serial.println("I2S already initialized");
        return true;
    }

    Serial.println("Configuring I2S peripheral...");

    // Initialize double buffering
    currentBuffer = audioBuffer0;
    currentBufferIndex = 0;

    // CRITICAL: Do NOT call pinMode() on I2S pins - this prevents the I2S peripheral
    // from taking control of the pins. The I2S peripheral will configure them automatically
    // when we set the PSEL registers and enable the peripheral.

    // Configure I2S peripheral (this will take control of the pins)
    configureI2S();

    // Optional: Configure SD_MODE pin for power control
    #ifdef SD_MODE_PIN
    pinMode(SD_MODE_PIN, OUTPUT);
    digitalWrite(SD_MODE_PIN, HIGH);  // Enable MAX98357A
    delay(10);  // Allow MAX98357A to start up
    Serial.print("SD_MODE pin (D6) state: ");
    Serial.println(digitalRead(SD_MODE_PIN) ? "HIGH (amplifier enabled)" : "LOW (amplifier disabled!)");
    #else
    Serial.println("WARNING: SD_MODE_PIN not defined - amplifier may be disabled!");
    #endif

    initialized = true;
    Serial.println("I2S initialized successfully");
    Serial.print("Sample Rate: ");
    Serial.print(SAMPLE_RATE);
    Serial.println(" Hz");

    return true;
}

void AudioI2S::configureI2S() {
    // Ensure 32 MHz HFCLK is running for precise audio clocks
    NRF_CLOCK->EVENTS_HFCLKSTARTED = 0;
    NRF_CLOCK->TASKS_HFCLKSTART = 1;
    while (NRF_CLOCK->EVENTS_HFCLKSTARTED == 0) {
        // wait for high-frequency clock to stabilize
    }
    NRF_CLOCK->EVENTS_HFCLKSTARTED = 0;

    // Disable I2S first
    NRF_I2S->ENABLE = 0;
    delay(10);  // Allow peripheral to fully disable

    // CRITICAL: Explicitly disconnect all pins first to ensure clean state
    NRF_I2S->PSEL.SCK = 0xFFFFFFFF;
    NRF_I2S->PSEL.LRCK = 0xFFFFFFFF;
    NRF_I2S->PSEL.SDOUT = 0xFFFFFFFF;
    NRF_I2S->PSEL.SDIN = 0xFFFFFFFF;

    // Clear all events before configuration
    NRF_I2S->EVENTS_RXPTRUPD = 0;
    NRF_I2S->EVENTS_TXPTRUPD = 0;
    NRF_I2S->EVENTS_STOPPED = 0;

    // Disable all interrupts
    NRF_I2S->INTENCLR = 0xFFFFFFFF;

    delay(10);  // Small delay before reconnecting

    // Configure pins - Use DIRECT GPIO numbers (NO bit shifting)
    // nRF52 I2S PSEL registers expect raw GPIO pin numbers
    // XIAO nRF52840 Sense PLUS - Castellated I2S pins on back of board
    NRF_I2S->PSEL.SCK   = I2S_SCK_PIN;      // GPIO 19 (P0.19 castellated) - BCLK
    NRF_I2S->PSEL.LRCK  = I2S_LRCK_PIN;     // GPIO 33 (P1.01 castellated) - LRCLK
    NRF_I2S->PSEL.SDOUT = I2S_SDOUT_PIN;    // GPIO 15 (P0.15 castellated) - DIN
    // SDIN stays disconnected (no microphone input)

    // Configure I2S mode - STEREO mode (MAX98357A needs full I2S frames)
    NRF_I2S->CONFIG.MODE     = I2S_CONFIG_MODE_MODE_Master;
    NRF_I2S->CONFIG.SWIDTH   = I2S_CONFIG_SWIDTH_SWIDTH_16Bit;
    NRF_I2S->CONFIG.ALIGN    = I2S_CONFIG_ALIGN_ALIGN_Right;  // RIGHT alignment
    NRF_I2S->CONFIG.FORMAT   = I2S_CONFIG_FORMAT_FORMAT_I2S;
    NRF_I2S->CONFIG.CHANNELS = I2S_CONFIG_CHANNELS_CHANNELS_Stereo;  // STEREO - duplicate to both channels
    NRF_I2S->CONFIG.MCKEN    = I2S_CONFIG_MCKEN_MCKEN_Enabled;
    NRF_I2S->CONFIG.TXEN     = I2S_CONFIG_TXEN_TXEN_Enabled;
    NRF_I2S->CONFIG.RXEN     = I2S_CONFIG_RXEN_RXEN_Disabled;

    // Configure master clock and ratio: 32MHz/21 = 1.524 MHz MCK, RATIO 48 => 31.746 kHz LRCK (0.79% deviation from 32kHz)
    NRF_I2S->CONFIG.MCKFREQ = I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV21;
    NRF_I2S->CONFIG.RATIO = I2S_CONFIG_RATIO_RATIO_48X;

    // Enable I2S
    NRF_I2S->ENABLE = 1;

    // Allow peripheral to stabilize before first use
    delay(10);

    Serial.println("I2S configured with GPIO pin numbers (19, 33, 15) - Castellated I2S pins");
    Serial.print("CONFIG.TXEN: ");
    Serial.println(NRF_I2S->CONFIG.TXEN);
    Serial.print("CONFIG.ALIGN: ");
    Serial.println(NRF_I2S->CONFIG.ALIGN);
}

void AudioI2S::generateTone(uint16_t frequency, uint16_t samples, uint8_t volume,
                            uint32_t globalOffset, uint32_t totalSamples) {
    // Clamp volume to 0-100, map to full-range amplitude (max int16 = 32767).
    volume = constrain(volume, 0, 100);
    int16_t amplitude = map(volume, 0, 100, 0, 32767);

    // Raised-cosine fade in/out kills the click at each tone's start and end. Cap the fade at
    // half the tone so very short tones still ramp symmetrically and never overlap.
    uint32_t fade = TONE_FADE_SAMPLES;
    if (totalSamples > 0 && fade > totalSamples / 2) fade = totalSamples / 2;

    // Phase advances continuously across chunks (tonePhase persists), so there is no waveform
    // discontinuity at the ~8ms buffer boundaries — that mid-tone phase reset was the buzz.
    const float phaseInc = 2.0f * PI * (float)frequency / (float)SAMPLE_RATE;

    for (uint16_t i = 0; i < samples && i < AUDIO_BUFFER_SIZE; i++) {
        uint32_t g = globalOffset + i;  // position within the whole tone

        float env = 1.0f;
        if (fade > 0) {
            if (g < fade) {
                env = 0.5f - 0.5f * cos(PI * (float)g / (float)fade);                 // fade in
            } else if (g >= totalSamples - fade) {
                env = 0.5f - 0.5f * cos(PI * (float)(totalSamples - g) / (float)fade); // fade out
            }
        }

        int16_t sample = (int16_t)(amplitude * env * sin(tonePhase));

        tonePhase += phaseInc;
        if (tonePhase >= 2.0f * PI) tonePhase -= 2.0f * PI;  // wrap to keep float precision

        // Pack sample for STEREO mode (duplicate mono source to both channels).
        uint16_t sampleU16 = (uint16_t)sample;
        currentBuffer[i] = ((uint32_t)sampleU16 << 16) | sampleU16;
    }
}

void AudioI2S::playTone(uint16_t frequency, uint16_t duration_ms, uint8_t volume) {
    if (!initialized) {
        Serial.println("ERROR: I2S not initialized");
        return;
    }

    // Clamp duration to prevent excessive blocking
    duration_ms = constrain(duration_ms, 1, MAX_TONE_DURATION_MS);

    // Calculate total samples needed
    uint32_t totalSamples = ((uint32_t)SAMPLE_RATE * duration_ms) / 1000;

    Serial.print("Playing tone: ");
    Serial.print(frequency);
    Serial.print(" Hz for ");
    Serial.print(duration_ms);
    Serial.print(" ms at volume ");
    Serial.println(volume);

    playing = true;
    tonePhase = 0.0f;                       // start each tone at zero phase (fade-in hides any jump)
    const uint32_t toneTotalSamples = totalSamples;  // keep the full length for the envelope
    uint32_t played = 0;                    // samples emitted so far (global offset into the tone)

    // Play tone in chunks with proper double buffering
    // STEREO mode: each sample uses 1 buffer word (L+R packed), so max samples = AUDIO_BUFFER_SIZE

    // Prepare first chunk
    uint16_t chunkSize = min(totalSamples, (uint32_t)AUDIO_BUFFER_SIZE);
    generateTone(frequency, chunkSize, volume, played, toneTotalSamples);
    startTransfer(chunkSize, true);  // Start I2S
    totalSamples -= chunkSize;
    played += chunkSize;

    while (totalSamples > 0) {
        // Wait for DMA to latch current buffer
        waitForBufferLatch();

        // Swap to other buffer and prepare next chunk WHILE current chunk is playing
        swapBuffers();
        chunkSize = min(totalSamples, (uint32_t)AUDIO_BUFFER_SIZE);
        generateTone(frequency, chunkSize, volume, played, toneTotalSamples);

        // Queue next buffer (will be latched when current buffer finishes)
        startTransfer(chunkSize, false);

        totalSamples -= chunkSize;
        played += chunkSize;
    }

    // Wait for last chunk to finish
    waitForFinalChunk(chunkSize);

    // Stop I2S after all chunks are done
    stop();

    playing = false;
}

void AudioI2S::playMelody(const uint16_t* frequencies, const uint16_t* durations, uint8_t count, uint8_t volume) {
    for (uint8_t i = 0; i < count; i++) {
        playTone(frequencies[i], durations[i], volume);

        // Small gap between notes
        delay(20);
    }
}

void AudioI2S::playBuffer(const int16_t* buffer, uint32_t sampleCount, uint8_t volume) {
    if (!initialized) {
        Serial.println("ERROR: I2S not initialized!");
        return;
    }

    if (buffer == nullptr || sampleCount == 0) {
        Serial.println("ERROR: Invalid buffer or sample count!");
        return;
    }

    // Clamp volume to 0-100
    volume = constrain(volume, 0, 100);

    // Calculate volume scaling factor (0.0 to 1.0)
    float volumeScale = volume / 100.0f;

    Serial.print("Playing buffer: ");
    Serial.print(sampleCount);
    Serial.print(" samples (");
    Serial.print((sampleCount * 1000.0f) / SAMPLE_RATE, 1);
    Serial.print(" ms) at volume ");
    Serial.println(volume);

    playing = true;

    uint32_t samplesRemaining = sampleCount;
    uint32_t bufferOffset = 0;

    // Helper lambda to load chunk into current buffer
    auto loadChunk = [&](uint16_t chunkSize) {
        for (uint16_t i = 0; i < chunkSize; i++) {
            int16_t sample = buffer[bufferOffset + i];
            int16_t scaledSample = (int16_t)(sample * volumeScale);
            uint16_t sampleU16 = (uint16_t)scaledSample;
            currentBuffer[i] = ((uint32_t)sampleU16 << 16) | sampleU16;  // Both channels
        }
        bufferOffset += chunkSize;
    };

    // Prepare and start first chunk
    uint16_t chunkSize = min(samplesRemaining, (uint32_t)AUDIO_BUFFER_SIZE);
    loadChunk(chunkSize);
    startTransfer(chunkSize, true);  // Start I2S
    samplesRemaining -= chunkSize;

    while (samplesRemaining > 0) {
        // Wait for DMA to latch current buffer
        waitForBufferLatch();

        // Swap to other buffer and prepare next chunk WHILE current chunk is playing
        swapBuffers();
        chunkSize = min(samplesRemaining, (uint32_t)AUDIO_BUFFER_SIZE);
        loadChunk(chunkSize);

        // Queue next buffer (will be latched when current buffer finishes)
        startTransfer(chunkSize, false);

        samplesRemaining -= chunkSize;
    }

    // Wait for last chunk to finish
    waitForFinalChunk(chunkSize);

    // Stop I2S after all chunks are done
    stop();

    playing = false;
    Serial.println("Buffer playback complete");
}

bool AudioI2S::playStreamCallback(
    uint32_t (*fill)(int16_t* dst, uint32_t maxSamples, void* userdata),
    void* userdata,
    uint8_t volume) {
    if (!initialized || fill == nullptr) return false;

    // Clamp volume to 0-100
    volume = constrain(volume, 0, 100);

    // Volume scaling factor, matching playBuffer exactly.
    float volumeScale = volume / 100.0f;

    // We sample-double 16 kHz -> 32 kHz: each source int16 sample becomes TWO
    // identical 32 kHz samples in the I2S buffer.  The I2S is configured for
    // STEREO mode, so each 32-bit word holds one sample duplicated into both the
    // high (left) and low (right) 16-bit halves — matching the word packing in
    // playBuffer exactly.
    //
    // Each I2S buffer holds AUDIO_BUFFER_SIZE words.  With doubling, we need
    // AUDIO_BUFFER_SIZE / 2 source samples to fill it completely (each source
    // sample expands to 2 words).  AUDIO_BUFFER_SIZE = 256, so srcSamplesPerBuffer
    // = 128, and 128 * 2 = 256 == AUDIO_BUFFER_SIZE (fits exactly, never overflows).
    const uint32_t srcSamplesPerBuffer = AUDIO_BUFFER_SIZE / 2;

    int16_t srcBuf[srcSamplesPerBuffer];

    bool firstChunk = true;
    bool playedAnything = false;
    uint16_t lastChunkWords = 0;

    playing = true;

    while (true) {
        uint32_t produced = fill(srcBuf, srcSamplesPerBuffer, userdata);
        if (produced == 0) break;

        // On subsequent iterations waitForBufferLatch() + swapBuffers() has
        // already updated currentBuffer to point at the idle buffer.  On the
        // first iteration currentBuffer still points at audioBuffer0 (set by
        // begin()), which is correct — mirrors playBuffer's loadChunk pattern.
        if (!firstChunk) {
            waitForBufferLatch();
            swapBuffers();
        }

        // Fill currentBuffer with sample-doubled, volume-scaled words.
        // Word packing mirrors playBuffer: sample duplicated into both channels.
        uint16_t outWords = (uint16_t)(produced * 2);
        for (uint32_t i = 0; i < produced; i++) {
            int16_t scaledSample = (int16_t)(srcBuf[i] * volumeScale);
            uint16_t sampleU16 = (uint16_t)scaledSample;
            uint32_t word = ((uint32_t)sampleU16 << 16) | sampleU16;  // Both channels
            currentBuffer[2 * i]     = word;
            currentBuffer[2 * i + 1] = word;
        }

        // outWords is already doubled (2 I2S words per source sample); pass as the I2S word count.
        startTransfer(outWords, firstChunk);
        firstChunk = false;
        playedAnything = true;
        lastChunkWords = outWords;
    }

    if (playedAnything) {
        waitForFinalChunk(lastChunkWords);
        stop();
    }

    playing = false;
    return playedAnything;
}

void AudioI2S::startTransfer(uint16_t sampleCount, bool isFirstChunk) {
    // Set buffer pointer to current buffer
    NRF_I2S->TXD.PTR = (uint32_t)currentBuffer;

    // Set transmit/receive shared length register (counts 32-bit words)
    // STEREO mode: 1 word per sample (L+R packed in 32 bits)
    NRF_I2S->RXTXD.MAXCNT = sampleCount;

    // Per-chunk debug print silenced (ADR-0020): it fired ~125x/sec during playback, flooding the
    // serial link (and blocking when a monitor is attached, skewing timing).

    // Clear events
    NRF_I2S->EVENTS_TXPTRUPD = 0;

    // Only START I2S on first chunk - subsequent chunks just update the buffer pointer
    if (isFirstChunk) {
        NRF_I2S->EVENTS_STOPPED = 0;
        NRF_I2S->TASKS_START = 1;
        Serial.println("I2S peripheral started");
    }

    // Buffer swapping is now done AFTER waitForCompletion() in the playback loop
}

void AudioI2S::swapBuffers() {
    currentBufferIndex = 1 - currentBufferIndex;
    currentBuffer = (currentBufferIndex == 0) ? audioBuffer0 : audioBuffer1;
    // Per-chunk debug print silenced (ADR-0020) — see startTransfer note.
}

void AudioI2S::setServiceCallback(void (*cb)()) {
    serviceCb = cb;
}

void AudioI2S::serviceDuringAudio() {
    if (serviceCb) serviceCb();
}

void AudioI2S::waitForBufferLatch() {
    // Wait for DMA to latch the buffer pointer
    uint32_t timeout = millis() + 50;
    while (NRF_I2S->EVENTS_TXPTRUPD == 0) {
        if (millis() > timeout) {
            Serial.println("ERROR: I2S TXPTRUPD timeout!");
            return;
        }
        serviceDuringAudio();   // keep stroke detection alive between chunks (ADR-0020)
        yield();
    }
    NRF_I2S->EVENTS_TXPTRUPD = 0;

    // Small delay to ensure DMA has fully switched to the new buffer
    // before we start modifying the old buffer (500μs = ~8 samples at 16kHz)
    delayMicroseconds(500);

    // Per-chunk "Buffer latched by DMA" print silenced (ADR-0020) — it flooded the serial link.
}

void AudioI2S::waitForFinalChunk(uint16_t sampleCount) {
    // Wait for the final chunk to finish playing
    uint32_t expectedDurationMs = (static_cast<uint32_t>(sampleCount) * 1000UL) / SAMPLE_RATE;
    if (expectedDurationMs == 0) {
        expectedDurationMs = 1;
    }

    Serial.print("Waiting for final chunk to complete: ");
    Serial.print(expectedDurationMs);
    Serial.println(" ms");

    // Serviced wait (ADR-0020): same duration as the old delay, but keep stroke detection alive.
    uint32_t finalUntil = millis() + expectedDurationMs + 5;  // small margin
    while ((int32_t)(millis() - finalUntil) < 0) {
        serviceDuringAudio();
        yield();
    }
}

void AudioI2S::stop() {
    if (!initialized) return;

    // Stop I2S transfer
    NRF_I2S->TASKS_STOP = 1;

    // Wait for stop
    while (NRF_I2S->EVENTS_STOPPED == 0) {
        yield();
    }

    playing = false;
}

void AudioI2S::suspend() {
    if (!initialized) return;

    Serial.println("Suspending I2S for power saving");

    // Stop any active transfer
    stop();

    // Disable I2S peripheral
    NRF_I2S->ENABLE = 0;

    // Optional: Power down MAX98357A
    #ifdef SD_MODE_PIN
    digitalWrite(SD_MODE_PIN, LOW);
    #endif
}

void AudioI2S::resume() {
    if (!initialized) return;

    Serial.println("Resuming I2S");

    // Optional: Power up MAX98357A
    #ifdef SD_MODE_PIN
    digitalWrite(SD_MODE_PIN, HIGH);
    delay(10);  // Startup time
    #endif

    // Re-enable I2S peripheral
    NRF_I2S->ENABLE = 1;
}

bool AudioI2S::isPlaying() {
    return playing;
}
