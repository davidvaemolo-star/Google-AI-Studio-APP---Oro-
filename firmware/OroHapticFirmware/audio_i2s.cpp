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

#ifndef I2S_CONFIG_RATIO_RATIO_64X
#define I2S_CONFIG_RATIO_RATIO_64X 2UL
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
    NRF_I2S->PSEL.SCK   = I2S_SCK_PIN;      // GPIO 3 (D1)
    NRF_I2S->PSEL.LRCK  = I2S_LRCK_PIN;     // GPIO 28 (D2)
    NRF_I2S->PSEL.SDOUT = I2S_SDOUT_PIN;    // GPIO 2 (D0)
    // SDIN stays disconnected (no microphone input)

    // Configure I2S mode - Mono configuration for MAX98357A
    NRF_I2S->CONFIG.MODE     = I2S_CONFIG_MODE_MODE_Master;
    NRF_I2S->CONFIG.SWIDTH   = I2S_CONFIG_SWIDTH_SWIDTH_16Bit;
    NRF_I2S->CONFIG.ALIGN    = I2S_CONFIG_ALIGN_ALIGN_Left;
    NRF_I2S->CONFIG.FORMAT   = I2S_CONFIG_FORMAT_FORMAT_I2S;
    NRF_I2S->CONFIG.CHANNELS = I2S_CONFIG_CHANNELS_CHANNELS_Left;  // Mono - left channel only
    NRF_I2S->CONFIG.MCKEN    = I2S_CONFIG_MCKEN_MCKEN_Enabled;
    NRF_I2S->CONFIG.TXEN     = I2S_CONFIG_TXEN_TXEN_Enabled;
    NRF_I2S->CONFIG.RXEN     = I2S_CONFIG_RXEN_RXEN_Disabled;

    // Configure master clock and ratio: 32MHz/32 = 1 MHz MCK, RATIO 64 => ~15.6 kHz LRCK
    NRF_I2S->CONFIG.MCKFREQ = I2S_CONFIG_MCKFREQ_MCKFREQ_32MDIV32;
    NRF_I2S->CONFIG.RATIO = I2S_CONFIG_RATIO_RATIO_64X;

    // Enable I2S
    NRF_I2S->ENABLE = 1;

    // Allow peripheral to stabilize before first use
    delay(10);

    Serial.println("I2S configured with GPIO pin numbers (3, 28, 2) and Master mode");
    Serial.print("CONFIG.TXEN: ");
    Serial.println(NRF_I2S->CONFIG.TXEN);
    Serial.print("CONFIG.ALIGN: ");
    Serial.println(NRF_I2S->CONFIG.ALIGN);
}

void AudioI2S::generateTone(uint16_t frequency, uint16_t samples, uint8_t volume) {
    // Clamp volume to 0-100
    volume = constrain(volume, 0, 100);

    // Map volume to amplitude - MAXIMUM setting for loudest output
    // Max int16_t = 32767, using FULL RANGE for maximum volume
    int16_t amplitude = map(volume, 0, 100, 0, 32767);

    // Debug output for first call
    static bool firstCall = true;
    if (firstCall) {
        Serial.println("\n=== AUDIO GENERATION DEBUG ===");
        Serial.print("Volume: "); Serial.print(volume); Serial.println("%");
        Serial.print("Target amplitude: "); Serial.println(amplitude);
        Serial.print("Max possible: 32767 (using "); Serial.print((amplitude*100)/32767); Serial.println("%)");
        firstCall = false;
    }

    // Generate sine wave samples and pack as stereo (L+R identical for mono source)
    int16_t peakSample = 0;
    for (uint16_t i = 0; i < samples && i < AUDIO_BUFFER_SIZE; i++) {
        float t = (float)i / SAMPLE_RATE;
        float angle = 2.0 * PI * frequency * t;
        int16_t sample = (int16_t)(amplitude * sin(angle));

        // Track peak for debugging
        if (abs(sample) > abs(peakSample)) {
            peakSample = sample;
        }

        // Pack sample directly for mono left channel
        // No bit shifting needed in mono mode
        audioBuffer[i] = (uint16_t)sample;

        if (i < 4) {
            Serial.print("Sample index ");
            Serial.print(i);
            Serial.print(" raw=0x");
            Serial.print((uint16_t)sample, HEX);
            Serial.print(" ("); Serial.print(sample); Serial.print(")");
            Serial.print(" packed=0x");
            Serial.println(audioBuffer[i], HEX);
        }
    }

    Serial.print("Peak sample: "); Serial.print(peakSample);
    Serial.print(" ("); Serial.print((abs(peakSample)*100)/32767); Serial.println("% of max)");
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

    // Play tone in chunks to avoid buffer overflow
    while (totalSamples > 0) {
        uint16_t chunkSize = min(totalSamples, (uint32_t)AUDIO_BUFFER_SIZE);

        // Generate tone samples
        generateTone(frequency, chunkSize, volume);

        // Start I2S transfer
        startTransfer(chunkSize);

        // Wait for completion
        waitForCompletion(chunkSize);

        totalSamples -= chunkSize;
    }

    playing = false;
}

void AudioI2S::playMelody(const uint16_t* frequencies, const uint16_t* durations, uint8_t count, uint8_t volume) {
    for (uint8_t i = 0; i < count; i++) {
        playTone(frequencies[i], durations[i], volume);

        // Small gap between notes
        delay(20);
    }
}

void AudioI2S::startTransfer(uint16_t sampleCount) {
    // Set buffer pointer
    NRF_I2S->TXD.PTR = (uint32_t)audioBuffer;

    // Set transmit/receive shared length register (counts 32-bit words)
    NRF_I2S->RXTXD.MAXCNT = sampleCount;

    // Debug: Uncomment to see I2S transfer details
    Serial.print("Starting I2S transfer: ");
    Serial.print(sampleCount);
    Serial.print(" samples, buffer @ 0x");
    Serial.println((uint32_t)audioBuffer, HEX);

    // Clear events
    NRF_I2S->EVENTS_TXPTRUPD = 0;
    NRF_I2S->EVENTS_STOPPED = 0;

    // Start I2S transfer
    NRF_I2S->TASKS_START = 1;
}

void AudioI2S::waitForCompletion(uint16_t sampleCount) {
    // Estimate duration of this chunk and block until it should be finished
    uint32_t expectedDurationMs = (static_cast<uint32_t>(sampleCount) * 1000UL) / SAMPLE_RATE;
    if (expectedDurationMs == 0) {
        expectedDurationMs = 1;  // ensure we wait at least a millisecond
    }

    // Allow DMA to fetch buffer pointer
    uint32_t timeout = millis() + 50;
    while (NRF_I2S->EVENTS_TXPTRUPD == 0) {
        if (millis() > timeout) {
            Serial.println("ERROR: I2S TXPTRUPD timeout!");
            // Verify I2S is still enabled
            Serial.print("I2S ENABLE: ");
            Serial.println(NRF_I2S->ENABLE);
            return;
        }
        yield();
    }
    NRF_I2S->EVENTS_TXPTRUPD = 0;

    delay(expectedDurationMs + 1);

    Serial.print("Chunk playback ms: ");
    Serial.println(expectedDurationMs);

    // Stop I2S and wait for STOPPED event
    NRF_I2S->TASKS_STOP = 1;

    timeout = millis() + 100;
    while (NRF_I2S->EVENTS_STOPPED == 0) {
        if (millis() > timeout) {
            Serial.println("ERROR: I2S STOPPED timeout!");
            // Verify I2S is still enabled
            Serial.print("I2S ENABLE: ");
            Serial.println(NRF_I2S->ENABLE);
            return;
        }
        yield();
    }

    NRF_I2S->EVENTS_STOPPED = 0;

    Serial.println("I2S chunk complete");
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
