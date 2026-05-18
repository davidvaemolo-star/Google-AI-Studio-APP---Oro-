/*
 * Audio I2S Driver for Oro Haptic Paddle
 *
 * Hardware: nRF52840 + MAX98357A I2S Audio Amplifier
 *
 * Pin Configuration (XIAO nRF52840 Sense PLUS - Castellated I2S Pins):
 * - P0.19 (GPIO 19) → BCLK (Bit Clock)
 * - P1.01 (GPIO 33) → LRCLK (Left/Right Clock / Word Select)
 * - P0.15 (GPIO 15) → DIN (Data Input)
 * - D6 (GPIO P1.11 = 43) → SD (Shutdown, active-high)
 *
 * MAX98357A Setup:
 * - GAIN: Tie to GND for 9dB gain (recommended), or VDD for 15dB
 * - SD_MODE: Tie HIGH to enable (can use GPIO for power control)
 * - Speaker: 4Ω or 8Ω, 1W max
 *
 * Sample Rate: 32kHz (configurable)
 * Bit Depth: 16-bit per channel
 * Channels: Stereo (mono source in left channel, right channel silent)
 * Alignment: Left-aligned (standard I2S format)
 */

#ifndef AUDIO_I2S_H
#define AUDIO_I2S_H

#include <Arduino.h>

// Pin definitions - Use ACTUAL GPIO NUMBERS from nRF52840 chip
// XIAO nRF52840 Sense PLUS - Dedicated I2S pins on CASTELLATED HOLES (back of board)
// P1.01 = Port 1, Pin 1 = GPIO 33 (Port 1 pins = 32 + pin number)
#define I2S_SCK_PIN   19  // P0.19 (castellated) - Bit Clock (BCLK)
#define I2S_LRCK_PIN  33  // P1.01 (castellated) - Word Select (LRCLK/WS)
#define I2S_SDOUT_PIN 15  // P0.15 (castellated) - Data Out (DIN to MAX98357A)

// SD_MODE control pin for power management (use Arduino pin number for pinMode/digitalWrite)
#define SD_MODE_PIN   D6  // D6 Arduino pin - Shutdown mode control (HIGH = enabled)

// Audio configuration
#define SAMPLE_RATE 32000           // 32kHz sample rate
#define AUDIO_BUFFER_SIZE 256       // Sample buffer size (adjust for memory vs latency)
#define MAX_TONE_DURATION_MS 2000   // Maximum tone duration to prevent blocking

class AudioI2S {
public:
    /**
     * Initialize I2S peripheral and configure pins
     * @return true if initialization successful, false otherwise
     */
    bool begin();

    /**
     * Generate and play a sine wave tone
     * @param frequency Frequency in Hz (20-16000 recommended for 32kHz sample rate)
     * @param duration_ms Duration in milliseconds
     * @param volume Volume level (0-100)
     */
    void playTone(uint16_t frequency, uint16_t duration_ms, uint8_t volume);

    /**
     * Play multiple tones in sequence (melody/beep pattern)
     * @param frequencies Array of frequencies in Hz
     * @param durations Array of durations in ms (parallel to frequencies)
     * @param count Number of tones to play
     * @param volume Volume level (0-100)
     */
    void playMelody(const uint16_t* frequencies, const uint16_t* durations, uint8_t count, uint8_t volume);

    /**
     * Play pre-recorded audio from flash memory (PROGMEM)
     * @param buffer Pointer to audio samples in flash (PROGMEM)
     * @param sampleCount Number of samples in buffer
     * @param volume Volume level (0-100)
     */
    void playBuffer(const int16_t* buffer, uint32_t sampleCount, uint8_t volume);

    /**
     * Stop I2S playback and disable peripheral
     */
    void stop();

    /**
     * Suspend I2S for power saving (can be resumed later)
     */
    void suspend();

    /**
     * Resume I2S after suspend
     */
    void resume();

    /**
     * Check if audio is currently playing
     * @return true if playing, false otherwise
     */
    bool isPlaying();

private:
    uint32_t audioBuffer0[AUDIO_BUFFER_SIZE];  // Double buffer 0
    uint32_t audioBuffer1[AUDIO_BUFFER_SIZE];  // Double buffer 1
    uint32_t* currentBuffer;                   // Pointer to current buffer
    uint8_t currentBufferIndex = 0;            // 0 or 1
    bool initialized = false;
    bool playing = false;

    /**
     * Configure nRF52840 I2S peripheral registers
     */
    void configureI2S();

    /**
     * Generate sine wave samples in buffer
     * @param frequency Frequency in Hz
     * @param samples Number of samples to generate
     * @param volume Volume (0-100)
     */
    void generateTone(uint16_t frequency, uint16_t samples, uint8_t volume);

    /**
     * Start I2S DMA transfer
     * @param sampleCount Number of samples to transfer
     * @param isFirstChunk True if this is the first chunk (will call TASKS_START)
     */
    void startTransfer(uint16_t sampleCount, bool isFirstChunk = true);

    /**
     * Wait for DMA to latch the buffer pointer
     */
    void waitForBufferLatch();

    /**
     * Wait for the final chunk to finish playing
     */
    void waitForFinalChunk(uint16_t sampleCount);

    /**
     * Swap between double buffers
     */
    void swapBuffers();
};

#endif // AUDIO_I2S_H
