/*
 * Audio I2S Driver for Oro Haptic Paddle
 *
 * Hardware: nRF52840 + MAX98357A I2S Audio Amplifier
 *
 * Pin Configuration (XIAO nRF52840):
 * - D1 (GPIO P0.03) → BCLK (Bit Clock)
 * - D2 (GPIO P0.28) → LRCLK (Left/Right Clock / Word Select)
 * - D0 (GPIO P0.02) → DIN (Data Input)
 * - D6 (GPIO P1.11 = 43) → SD (Shutdown, active-high)
 *
 * MAX98357A Setup:
 * - GAIN: Tie to GND for 9dB gain (recommended), or VDD for 15dB
 * - SD_MODE: Tie HIGH to enable (can use GPIO for power control)
 * - Speaker: 4Ω or 8Ω, 1W max
 *
 * Sample Rate: 16kHz (configurable)
 * Bit Depth: 16-bit
 * Channels: Stereo (mono signal duplicated to both channels)
 */

#ifndef AUDIO_I2S_H
#define AUDIO_I2S_H

#include <Arduino.h>

// Pin definitions - Use ACTUAL GPIO NUMBERS from nRF52840 chip
// XIAO nRF52840 pin mapping: D0=GPIO2, D1=GPIO3, D2=GPIO28
// These must be the ACTUAL GPIO numbers, NOT Arduino D-numbers!
#define I2S_SCK_PIN   3   // D1 = GPIO P0.03 - Bit Clock (BCLK)
#define I2S_LRCK_PIN  28  // D2 = GPIO P0.28 - Left/Right Clock (LRCLK)
#define I2S_SDOUT_PIN 2   // D0 = GPIO P0.02 - Data Out (DIN to MAX98357A)

// SD_MODE control pin for power management (use Arduino pin number for pinMode/digitalWrite)
#define SD_MODE_PIN   D6  // D6 Arduino pin - Shutdown mode control (HIGH = enabled)

// Audio configuration
#define SAMPLE_RATE 16000           // 16kHz sample rate
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
     * @param frequency Frequency in Hz (20-8000 recommended for 16kHz sample rate)
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
    uint32_t audioBuffer[AUDIO_BUFFER_SIZE];  // 32-bit stereo samples (L+R channels)
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
     */
    void startTransfer(uint16_t sampleCount);

    /**
     * Wait for I2S transfer to complete
     */
    void waitForCompletion(uint16_t sampleCount);
};

#endif // AUDIO_I2S_H
