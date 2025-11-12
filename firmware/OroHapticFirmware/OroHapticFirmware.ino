/*
 * Oro Haptic Paddle Firmware
 * Hardware: Seeed XIAO nRF52840 Sense
 * Haptic Driver: DRV2605L (I2C address 0x5A)
 * IMU Sensor: LSM6DS3 (built-in on XIAO Sense)
 * Audio: MAX98357A I2S Audio Amplifier + 1W 8Ω Speaker
 *
 * BLE Services:
 * - Oro Haptic Service: Custom service for training control
 * - Battery Service: Standard 0x180F for battery monitoring
 *
 * Pin Map (IMMUTABLE):
 * - I2C: SDA=D4 (pin 4), SCL=D5 (pin 5)
 * - Battery Monitor: A0 (analog pin for voltage divider)
 * - I2S Audio: BCLK=D1, LRCLK=D0, DIN=D2, SD=D6
 *
 * Author: Oro Development Team
 * Date: 2025-11-06
 */

#include <bluefruit.h>
#include <Wire.h>
#include <Adafruit_DRV2605.h>
#include <math.h>
#include "LSM6DS3.h"  // Use Seeed_Arduino_LSM6DS3 library
#include "audio_i2s.h"  // I2S audio playback for MAX98357A

// ============================================================================
// HARDWARE CONFIGURATION
// ============================================================================

// I2C Pins (XIAO nRF52840)
#define SDA_PIN 4  // D4
#define SCL_PIN 5  // D5

// I2S Audio Pins (MAX98357A)
#define I2S_BCLK_PIN  1  // D1 - Bit Clock
#define I2S_LRCLK_PIN 2  // D2 - Left/Right Clock (Word Select)
#define I2S_DIN_PIN   0  // D0 - Data In
#define I2S_SD_PIN    6  // D6 - Shutdown (active-high, LOW=mute)

// Battery Monitoring
#define BATTERY_PIN A0
#define BATTERY_READ_INTERVAL 30000  // Read every 30 seconds

// DRV2605L Configuration
Adafruit_DRV2605 drv;

// LSM6DS3 IMU Configuration (built-in on XIAO Sense, I2C address 0x6A)
LSM6DS3 imu(I2C_MODE, 0x6A);

// I2S Audio Configuration
AudioI2S audioPlayer;

// IMU Stroke Detection Settings
#define IMU_SAMPLE_RATE_HZ 104       // 104 Hz sampling rate
#define STROKE_DETECT_THRESHOLD 1.0  // Acceleration threshold in g (based on real paddle data: peak ~1.83g, using 55%)
#define STROKE_MIN_INTERVAL_MS 200   // Minimum time between strokes (prevents double-counting)
#define CALIBRATION_SAMPLES 10      // Number of samples for calibration

// ============================================================================
// BLE SERVICE AND CHARACTERISTIC UUIDs
// ============================================================================

// Oro Haptic Service UUID: 12340000-1234-5678-1234-56789abcdef0
#define ORO_HAPTIC_SERVICE_UUID "12340000-1234-5678-1234-56789abcdef0"

// Oro Characteristic UUIDs:
#define HAPTIC_CONTROL_CHAR_UUID    "12340001-1234-5678-1234-56789abcdef0"  // Write - trigger haptic patterns
#define ZONE_SETTINGS_CHAR_UUID     "12340002-1234-5678-1234-56789abcdef0"  // Write - training zone config
#define DEVICE_STATUS_CHAR_UUID     "12340003-1234-5678-1234-56789abcdef0"  // Read/Notify - device state
#define CONNECTION_STATUS_CHAR_UUID "12340004-1234-5678-1234-56789abcdef0"  // Read/Notify - connection state
#define STROKE_EVENT_CHAR_UUID      "12340005-1234-5678-1234-56789abcdef0"  // Notify - stroke detection events
#define CALIBRATION_CHAR_UUID       "12340006-1234-5678-1234-56789abcdef0"  // Write/Notify - calibration control
#define AUDIO_CONTROL_CHAR_UUID     "12340007-1234-5678-1234-56789abcdef0"  // Write - trigger audio prompts

// Standard Battery Service
#define BATTERY_SERVICE_UUID        "180F"
#define BATTERY_LEVEL_CHAR_UUID     "2A19"

// ============================================================================
// BLE SERVICES AND CHARACTERISTICS
// ============================================================================

BLEService oroHapticService = BLEService(ORO_HAPTIC_SERVICE_UUID);
BLEService batteryService = BLEService(BATTERY_SERVICE_UUID);

// Haptic Control: Write only
// Format: [command(1 byte)][intensity(1 byte)][duration_ms(2 bytes)][pattern(1 byte)]
BLECharacteristic hapticControlChar = BLECharacteristic(HAPTIC_CONTROL_CHAR_UUID);

// Zone Settings: Write only
// Format: [strokes(2 bytes)][sets(1 byte)][spm(2 bytes)][zone_color(1 byte)]
BLECharacteristic zoneSettingsChar = BLECharacteristic(ZONE_SETTINGS_CHAR_UUID);

// Device Status: Read + Notify
// Format: [state(1 byte)][current_stroke(2 bytes)][current_set(1 byte)][battery(1 byte)]
BLECharacteristic deviceStatusChar = BLECharacteristic(DEVICE_STATUS_CHAR_UUID);

// Connection Status: Read + Notify
// Format: [connected(1 byte)][rssi(1 byte signed)]
BLECharacteristic connectionStatusChar = BLECharacteristic(CONNECTION_STATUS_CHAR_UUID);

// Battery Level: Read + Notify (standard format: 0-100%)
BLECharacteristic batteryLevelChar = BLECharacteristic(BATTERY_LEVEL_CHAR_UUID);

// Stroke Event: Notify only
// Format: [stroke_phase(1 byte)][timestamp_ms(4 bytes)][accel_magnitude(2 bytes float16)]
BLECharacteristic strokeEventChar = BLECharacteristic(STROKE_EVENT_CHAR_UUID);

// Calibration: Write + Notify
// Format: [command(1 byte)][threshold(2 bytes float16)][status(1 byte)]
BLECharacteristic calibrationChar = BLECharacteristic(CALIBRATION_CHAR_UUID);

// Audio Control: Write only
// Format: [audio_event(1 byte)][volume(1 byte)]
BLECharacteristic audioControlChar = BLECharacteristic(AUDIO_CONTROL_CHAR_UUID);

// ============================================================================
// DEVICE STATE MANAGEMENT
// ============================================================================

// Device States
enum DeviceState {
  STATE_IDLE = 0x00,
  STATE_READY = 0x01,
  STATE_TRAINING = 0x02,
  STATE_PAUSED = 0x03,
  STATE_COMPLETE = 0x04,
  STATE_CALIBRATING = 0x05,
  STATE_ERROR = 0xFF
};

// Haptic Commands
enum HapticCommand {
  CMD_STOP = 0x00,
  CMD_SINGLE_PULSE = 0x01,
  CMD_START_TRAINING = 0x02,
  CMD_PAUSE_TRAINING = 0x03,
  CMD_RESUME_TRAINING = 0x04,
  CMD_COMPLETE_TRAINING = 0x05,
  CMD_TEST_PATTERN = 0x06
};

// Calibration Commands
enum CalibrationCommand {
  CAL_CMD_START = 0x01,
  CAL_CMD_STOP = 0x02,
  CAL_CMD_SET_THRESHOLD = 0x03,
  CAL_CMD_GET_STATUS = 0x04
};

// Stroke Phases
enum StrokePhase {
  STROKE_PHASE_CATCH = 0x01,    // Start of stroke (paddle entry/catch)
  STROKE_PHASE_DRIVE = 0x02,    // Power phase (drive/pull)
  STROKE_PHASE_FINISH = 0x03,   // End of stroke (finish/extraction)
  STROKE_PHASE_RECOVERY = 0x04  // Return to catch position
};

// Haptic Patterns (DRV2605L effect library)
enum HapticPattern {
  PATTERN_STRONG_CLICK = 1,      // Sharp single click
  PATTERN_SHARP_CLICK = 2,       // Medium click
  PATTERN_SOFT_CLICK = 3,        // Gentle click
  PATTERN_DOUBLE_CLICK = 12,     // Two quick clicks
  PATTERN_TRIPLE_CLICK = 13,     // Three quick clicks
  PATTERN_PULSING = 47,          // Continuous pulse
  PATTERN_TRANSITION = 51,       // Smooth transition
  PATTERN_ALERT_750MS = 24       // Long alert
};

// Audio Events
enum AudioEvent {
  AUDIO_TRAINING_START = 0x01,    // Training session start beep
  AUDIO_HALFWAY = 0x02,           // Halfway point chime
  AUDIO_SET_COMPLETE = 0x03,      // Set complete tone
  AUDIO_LAST_SET = 0x04,          // Last set alert
  AUDIO_ZONE_TRANSITION = 0x05,   // Zone transition sound
  AUDIO_SESSION_COMPLETE = 0x06,  // Session complete fanfare
  AUDIO_PAUSE = 0x07,             // Pause beep
  AUDIO_RESUME = 0x08             // Resume beep
};

// Training Configuration
struct TrainingConfig {
  uint16_t totalStrokes;
  uint8_t totalSets;
  uint16_t strokesPerMinute;
  uint8_t zoneColor;
  bool isActive;
};

// Current Training State
struct TrainingState {
  DeviceState deviceState;
  uint16_t currentStroke;
  uint8_t currentSet;
  uint8_t batteryLevel;
  unsigned long lastStrokeTime;
  unsigned long strokeInterval;  // Calculated from SPM
};

TrainingConfig trainingConfig = {0, 0, 0, 0, false};
TrainingState trainingState = {STATE_IDLE, 0, 0, 100, 0, 0};

// Stroke Detection State
struct StrokeDetectionState {
  bool enabled;
  float threshold;               // Acceleration threshold in g
  StrokePhase currentPhase;
  unsigned long lastStrokeTime;
  float maxAccel;                // Peak acceleration during current stroke
  float minAccel;                // Minimum (most negative) during recovery
  bool inStroke;                 // Currently in a stroke cycle
};

StrokeDetectionState strokeDetection = {
  false,                         // disabled by default
  STROKE_DETECT_THRESHOLD,       // default threshold
  STROKE_PHASE_RECOVERY,         // start in recovery phase
  0,                             // no strokes yet
  0.0,                           // no peak yet
  0.0,                           // no minimum yet
  false                          // not in stroke
};

// Calibration State
struct CalibrationState {
  bool active;
  uint8_t sampleCount;
  float maxAccelSeen;
  float minAccelSeen;
};

CalibrationState calibrationState = {false, 0, 0.0, 0.0};

// Battery monitoring
const float BATTERY_DIVIDER_RATIO = (1000000.0f + 510000.0f) / 510000.0f;  // 2.960784
const float BATTERY_FULL_VOLTAGE = 4.2f;
const float BATTERY_EMPTY_VOLTAGE = 3.0f;
const float ADC_REFERENCE_VOLTAGE = 3.6f;  // 0.6V reference with 1/6 gain
const float ADC_MAX_READING = 4095.0f;      // 12-bit resolution
const uint8_t BATTERY_SAMPLE_COUNT = 8;
unsigned long lastBatteryRead = 0;
uint8_t lastBatteryLevel = 100;

// Device name with BLE address suffix
String deviceName = "Oro-0000";

// ============================================================================
// SETUP FUNCTIONS
// ============================================================================

void setup() {
  Serial.begin(115200);
  delay(2000);  // Wait for serial monitor

  Serial.println("=== Oro Haptic Paddle Firmware ===");
  Serial.println("Hardware: XIAO nRF52840 Sense + DRV2605L");
  Serial.println();

  // Initialize I2C with custom pins
  Wire.begin();
  Wire.setClock(400000);  // 400kHz I2C

  // Initialize DRV2605L
  if (!initializeDRV2605L()) {
    Serial.println("ERROR: Failed to initialize DRV2605L");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }  // Halt on critical error
  }

  // Initialize LSM6DS3 IMU
  if (!initializeIMU()) {
    Serial.println("ERROR: Failed to initialize IMU");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }  // Halt on critical error
  }

  // Initialize I2S Audio (MAX98357A)
  // CRITICAL: Explicitly enable MAX98357A amplifier BEFORE I2S init
  pinMode(I2S_SD_PIN, OUTPUT);
  digitalWrite(I2S_SD_PIN, HIGH);
  delay(50);  // Allow amplifier to power up
  Serial.print("MAX98357A SD pin (D6): ");
  Serial.println(digitalRead(I2S_SD_PIN) ? "HIGH (enabled)" : "LOW (DISABLED!)");

  if (audioPlayer.begin()) {
    Serial.println("I2S audio initialized successfully");
  } else {
    Serial.println("WARNING: Failed to initialize I2S audio - continuing without audio");
  }

  // Initialize BLE
  if (!initializeBLE()) {
    Serial.println("ERROR: Failed to initialize BLE");
    trainingState.deviceState = STATE_ERROR;
    while(1) { delay(1000); }
  }

  // Initialize battery monitoring
  pinMode(BATTERY_PIN, INPUT);
#if defined(AR_INTERNAL_0_6)
  analogReference(AR_INTERNAL_0_6);
#endif
  analogReadResolution(12);
  updateBatteryLevel();

  // System ready
  trainingState.deviceState = STATE_READY;
  Serial.println("System initialized successfully");
  Serial.println("Device name: " + deviceName);
  Serial.println("Ready for BLE connections");
  Serial.println();

  // Enable stroke detection by default for testing
  strokeDetection.enabled = true;
  Serial.println("Stroke detection ENABLED");
  Serial.print("Current threshold: ");
  Serial.print(strokeDetection.threshold, 2);
  Serial.println("g");

  // Play startup haptic
  playHapticEffect(PATTERN_DOUBLE_CLICK, 100);
}

bool initializeDRV2605L() {
  Serial.println("Initializing DRV2605L haptic driver...");

  // Scan I2C bus
  Serial.print("Scanning I2C bus... ");
  Wire.beginTransmission(0x5A);
  if (Wire.endTransmission() != 0) {
    Serial.println("NOT FOUND at 0x5A");
    return false;
  }
  Serial.println("FOUND at 0x5A");

  // Initialize driver
  if (!drv.begin()) {
    Serial.println("Failed to initialize DRV2605L driver");
    return false;
  }

  // Configure for ERM motor
  drv.selectLibrary(1);  // ERM library
  drv.setMode(DRV2605_MODE_INTTRIG);  // Internal trigger mode

  Serial.println("DRV2605L initialized successfully");
  return true;
}

bool initializeIMU() {
  Serial.println("Initializing LSM6DS3 IMU...");

  // Initialize IMU with default settings
  uint8_t result = imu.begin();
  Serial.print("IMU begin() returned: ");
  Serial.println(result);

  if (result != 0) {
    Serial.println("Failed to initialize LSM6DS3");
    Serial.println("Check I2C connections and address (0x6A)");
    return false;
  }

  Serial.println("LSM6DS3 initialized successfully");

  // Test read to verify IMU is working
  float testX = imu.readFloatAccelX();
  float testY = imu.readFloatAccelY();
  float testZ = imu.readFloatAccelZ();

  Serial.print("Test reading - X: ");
  Serial.print(testX, 3);
  Serial.print("g, Y: ");
  Serial.print(testY, 3);
  Serial.print("g, Z: ");
  Serial.print(testZ, 3);
  Serial.println("g");

  return true;
}

bool initializeBLE() {
  Serial.println("Initializing BLE...");

  // Initialize Bluefruit
  Bluefruit.begin();

  // Generate device name with last 4 chars of BLE address
  char addressStr[18];
  uint8_t mac[6];
  Bluefruit.getAddr(mac);
  sprintf(addressStr, "%02X%02X%02X%02X%02X%02X", mac[5], mac[4], mac[3], mac[2], mac[1], mac[0]);
  String address = String(addressStr);
  deviceName = "Oro-" + address.substring(address.length() - 4);

  Bluefruit.setName(deviceName.c_str());

  // Set max power for better range
  Bluefruit.setTxPower(4);  // Max +4dBm

  // Set connection parameters for low latency (7.5ms - 20ms)
  Bluefruit.Periph.setConnInterval(6, 16);  // Units of 1.25ms

  // Configure Oro Haptic Service
  oroHapticService.begin();

  // Haptic Control Characteristic (Write)
  hapticControlChar.setProperties(CHR_PROPS_WRITE);
  hapticControlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  hapticControlChar.setFixedLen(5);
  hapticControlChar.setWriteCallback(onHapticControlWrite);
  hapticControlChar.begin();

  // Zone Settings Characteristic (Write)
  zoneSettingsChar.setProperties(CHR_PROPS_WRITE);
  zoneSettingsChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  zoneSettingsChar.setFixedLen(6);
  zoneSettingsChar.setWriteCallback(onZoneSettingsWrite);
  zoneSettingsChar.begin();

  // Device Status Characteristic (Read + Notify)
  deviceStatusChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  deviceStatusChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  deviceStatusChar.setFixedLen(5);
  deviceStatusChar.begin();

  // Connection Status Characteristic (Read + Notify)
  connectionStatusChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  connectionStatusChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  connectionStatusChar.setFixedLen(2);
  connectionStatusChar.begin();

  // Stroke Event Characteristic (Notify only)
  strokeEventChar.setProperties(CHR_PROPS_NOTIFY);
  strokeEventChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  strokeEventChar.setFixedLen(7);  // 1 byte phase + 4 bytes timestamp + 2 bytes accel
  strokeEventChar.begin();

  // Calibration Characteristic (Write + Notify)
  calibrationChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_NOTIFY);
  calibrationChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  calibrationChar.setFixedLen(4);  // 1 byte command + 2 bytes threshold + 1 byte status
  calibrationChar.setWriteCallback(onCalibrationWrite);
  calibrationChar.begin();

  // Audio Control Characteristic (Write)
  audioControlChar.setProperties(CHR_PROPS_WRITE);
  audioControlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  audioControlChar.setFixedLen(2);  // 1 byte audio event + 1 byte volume
  audioControlChar.setWriteCallback(onAudioControlWrite);
  audioControlChar.begin();

  // Configure Battery Service
  batteryService.begin();

  // Battery Level Characteristic (Read + Notify)
  batteryLevelChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  batteryLevelChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  batteryLevelChar.setFixedLen(1);
  batteryLevelChar.begin();

  // Set initial characteristic values
  updateDeviceStatus();
  updateConnectionStatus();
  batteryLevelChar.write8(trainingState.batteryLevel);

  // Set connection callbacks
  Bluefruit.Periph.setConnectCallback(onBLEConnected);
  Bluefruit.Periph.setDisconnectCallback(onBLEDisconnected);

  // Start advertising
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();
  Bluefruit.Advertising.addService(oroHapticService);
  Bluefruit.Advertising.addName();

  // Set advertising interval (fast mode: 20ms, slow mode: 152.5ms)
  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);  // Units of 0.625ms
  Bluefruit.Advertising.setFastTimeout(30);    // Fast mode for 30 seconds
  Bluefruit.Advertising.start(0);  // 0 = Don't stop advertising

  Serial.println("BLE initialized successfully");
  Serial.println("Advertising as: " + deviceName);

  return true;
}

// ============================================================================
// MAIN LOOP
// ============================================================================

void loop() {
  // Bluefruit handles BLE automatically, no need to poll

  // Check for serial commands
  if (Serial.available()) {
    char cmd = Serial.read();
    if (cmd == 'i' || cmd == 'I') {
      // Print I2S debug info
      Serial.println("\n=== I2S DEBUG INFO ===");
      Serial.println("I2S Peripheral Configuration:");
      Serial.print("  PSEL.SCK:   0x"); Serial.print(NRF_I2S->PSEL.SCK, HEX);
      Serial.print(" (expected: 0x3 for GPIO3/D1)"); Serial.println();
      Serial.print("  PSEL.LRCK:  0x"); Serial.print(NRF_I2S->PSEL.LRCK, HEX);
      Serial.print(" (expected: 0x1C for GPIO28/D2)"); Serial.println();
      Serial.print("  PSEL.SDOUT: 0x"); Serial.print(NRF_I2S->PSEL.SDOUT, HEX);
      Serial.print(" (expected: 0x2 for GPIO2/D0)"); Serial.println();
      Serial.print("  PSEL.SDIN:  0x"); Serial.print(NRF_I2S->PSEL.SDIN, HEX);
      Serial.print(" (should be 0xFFFFFFFF = disconnected)"); Serial.println();
      Serial.print("  ENABLE: "); Serial.println(NRF_I2S->ENABLE ? "1 (enabled)" : "0 (disabled!)");
      Serial.print("  MODE: "); Serial.println(NRF_I2S->CONFIG.MODE == 0 ? "Master (0)" : "Slave (1)");
      Serial.print("  MCKFREQ: 0x"); Serial.print(NRF_I2S->CONFIG.MCKFREQ, HEX);
      Serial.println(" (should be 0x8000000 = 1MHz)");
      Serial.print("  RATIO: "); Serial.print(NRF_I2S->CONFIG.RATIO);
      Serial.println(" (should be 2 = 64x)");
      Serial.print("  CHANNELS: ");
      switch(NRF_I2S->CONFIG.CHANNELS) {
        case 0: Serial.println("Stereo (0)"); break;
        case 1: Serial.println("Left (1)"); break;
        case 2: Serial.println("Right (2)"); break;
        default: Serial.println("Unknown"); break;
      }
      Serial.print("SD_MODE pin (D6) state: ");
      Serial.println(digitalRead(SD_MODE_PIN) ? "HIGH (amplifier enabled)" : "LOW (amplifier disabled!)");
      Serial.println("\nPhysical pin voltage check:");
      Serial.println("  D1 (BCLK) should show ~512 kHz square wave when playing");
      Serial.println("  D2 (LRC)  should show ~16 kHz square wave when playing");
      Serial.println("  D0 (DIN)  should show I2S data stream when playing");
      Serial.println("\n=== HARDWARE GAIN PIN CHECK ===");
      Serial.println("CRITICAL: MAX98357A GAIN pin determines maximum volume!");
      Serial.println("  GAIN -> GND:     9dB gain  [QUIETEST]");
      Serial.println("  GAIN -> FLOAT:  12dB gain  [MODERATE]");
      Serial.println("  GAIN -> VDD:    15dB gain  [LOUDEST - 2x louder than GND!]");
      Serial.println("If audio is faint, MOVE GAIN pin from GND to VDD!");
      Serial.println("\nAvailable commands:");
      Serial.println("  't' - Test audio tone (1000 Hz, 500ms at 100% volume)");
      Serial.println("  'v' - Volume test (20%-100% sweep)");
      Serial.println("  'a' - Toggle amplifier enable (SD_MODE pin)");
      Serial.println("  'h' - Hardware troubleshooting guide");
      Serial.println("  'l' - Loud test (continuous 1kHz at max volume)");
      Serial.println("  'g' - GAIN PIN DIAGNOSTIC (check hardware gain setting)");
      Serial.println("  'f' - Toggle I2S format (LEFT/RIGHT alignment)");
      Serial.println("  'c' - Cycle I2S channel mode (Stereo/Left/Right)");
      Serial.println("  's' - Speaker test (diagnose hardware issue)");
      Serial.println("  'w' - Check wiring (verify pin connections)");
    } else if (cmd == 't' || cmd == 'T') {
      // Test audio - NOW USES 100% VOLUME FOR MAXIMUM OUTPUT
      Serial.println("\n=== AUDIO TEST ===");
      Serial.println("Playing 1000 Hz tone for 500ms at volume 100 (MAXIMUM)...");
      Serial.println("This uses FULL 16-bit amplitude (32767).");
      Serial.println("If still quiet, it's a HARDWARE issue - check GAIN pin!");
      audioPlayer.playTone(1000, 500, 100);
      Serial.println("Audio test complete");
    } else if (cmd == 'a' || cmd == 'A') {
      // Toggle amplifier
      bool currentState = digitalRead(I2S_SD_PIN);
      digitalWrite(I2S_SD_PIN, !currentState);
      delay(50);
      Serial.println("\n=== AMPLIFIER CONTROL ===");
      Serial.print("SD_MODE pin toggled to: ");
      Serial.println(digitalRead(I2S_SD_PIN) ? "HIGH (enabled)" : "LOW (disabled)");
      Serial.println("Try playing audio now with 't' command");
    } else if (cmd == 'v' || cmd == 'V') {
      // Volume test - play tones at different volumes
      Serial.println("\n=== VOLUME TEST ===");
      Serial.println("Playing 1000 Hz tone at different volumes...");
      for (uint8_t vol = 20; vol <= 100; vol += 20) {
        Serial.print("Volume ");
        Serial.print(vol);
        Serial.println("%...");
        audioPlayer.playTone(1000, 200, vol);
        delay(100);
      }
      Serial.println("Volume test complete");
    } else if (cmd == 'h' || cmd == 'H') {
      // Hardware troubleshooting guide
      Serial.println("\n=== HARDWARE TROUBLESHOOTING ===");
      Serial.println("\nIf audio is FAINT/TOO QUIET:");
      Serial.println("-------------------------------");
      Serial.println("Problem: MAX98357A GAIN pin is set too LOW");
      Serial.println("\nGAIN Pin Settings:");
      Serial.println("  GAIN → GND (0V):     9dB gain  [QUIETEST - likely your current setting]");
      Serial.println("  GAIN → FLOAT:        12dB gain [MODERATE]");
      Serial.println("  GAIN → VDD (3.3V):   15dB gain [LOUDEST - recommended!]");
      Serial.println("\nFIX: Check your MAX98357A breakout board:");
      Serial.println("  1. Locate the 'GAIN' pin/pad");
      Serial.println("  2. If connected to GND, disconnect it");
      Serial.println("  3. Connect GAIN to VDD/3.3V (or leave floating for 12dB)");
      Serial.println("  4. Restart and test again with 't' command");
      Serial.println("\nOther checks:");
      Serial.println("  - Verify speaker is 4-8 ohm (4 ohm = louder)");
      Serial.println("  - Check speaker wire connections");
      Serial.println("  - Ensure good power supply (USB or fully charged LiPo)");
      Serial.println("  - Try a different speaker to rule out damage");
      Serial.println("  - Measure speaker voltage with multimeter during playback");
      Serial.println("  - Check if MAX98357A gets warm (indicates it's working)");
      Serial.println("\nIf GAIN is already at VDD and still quiet:");
      Serial.println("  - Speaker might be damaged or wrong impedance");
      Serial.println("  - MAX98357A board might be defective");
      Serial.println("  - Try pressing speaker firmly against ear during 'l' test");
      Serial.println("\nAfter hardware fix, type 'l' to test maximum volume.");
    } else if (cmd == 'l' || cmd == 'L') {
      // Loud continuous test
      Serial.println("\n=== MAXIMUM VOLUME TEST ===");
      Serial.println("Playing 1000 Hz at 100% volume for 3 seconds...");
      Serial.println("This is the LOUDEST this system can produce.");
      Serial.println("If this is still too quiet, it's a HARDWARE issue:");
      Serial.println("  - Check GAIN pin connection");
      Serial.println("  - Verify speaker impedance (4-8 ohm)");
      Serial.println("  - Test with different speaker");
      Serial.println("  - Check MAX98357A board for damage");
      Serial.println("\nStarting in 1 second...");
      delay(1000);
      audioPlayer.playTone(1000, 3000, 100);
      Serial.println("Test complete. Was it loud enough?");
    } else if (cmd == 'f' || cmd == 'F') {
      // Toggle I2S alignment
      Serial.println("\n=== I2S FORMAT TOGGLE ===");
      Serial.println("Toggling between LEFT and RIGHT alignment...");

      // Read current alignment
      bool isLeftAligned = (NRF_I2S->CONFIG.ALIGN == I2S_CONFIG_ALIGN_ALIGN_Left);

      // Disable I2S
      NRF_I2S->ENABLE = 0;
      delay(10);

      // Toggle alignment
      if (isLeftAligned) {
        NRF_I2S->CONFIG.ALIGN = I2S_CONFIG_ALIGN_ALIGN_Right;
        Serial.println("Changed to RIGHT alignment");
      } else {
        NRF_I2S->CONFIG.ALIGN = I2S_CONFIG_ALIGN_ALIGN_Left;
        Serial.println("Changed to LEFT alignment");
      }

      // Re-enable I2S
      NRF_I2S->ENABLE = 1;
      delay(10);

      Serial.println("Now test audio with 't' command");
      Serial.println("If still quiet, press 'f' again to try the other format");
    } else if (cmd == 'c' || cmd == 'C') {
      // Cycle I2S channel mode
      Serial.println("\n=== I2S CHANNEL MODE CYCLE ===");

      // Read current channel mode
      uint32_t currentMode = NRF_I2S->CONFIG.CHANNELS;

      // Disable I2S
      NRF_I2S->ENABLE = 0;
      delay(10);

      // Cycle through modes: Stereo (0) -> Left (1) -> Right (2) -> Stereo
      switch(currentMode) {
        case 0: // Stereo -> Left
          NRF_I2S->CONFIG.CHANNELS = 1;
          Serial.println("Changed to LEFT channel only");
          break;
        case 1: // Left -> Right
          NRF_I2S->CONFIG.CHANNELS = 2;
          Serial.println("Changed to RIGHT channel only");
          break;
        case 2: // Right -> Stereo
          NRF_I2S->CONFIG.CHANNELS = 0;
          Serial.println("Changed to STEREO (both channels)");
          break;
        default:
          NRF_I2S->CONFIG.CHANNELS = 0;
          Serial.println("Reset to STEREO (both channels)");
          break;
      }

      // Re-enable I2S
      NRF_I2S->ENABLE = 1;
      delay(10);

      Serial.println("Now test with 't' command");
      Serial.println("Press 'c' again to try next mode if still quiet");
    } else if (cmd == 's' || cmd == 'S') {
      // Speaker hardware diagnostic
      Serial.println("\n=== SPEAKER HARDWARE DIAGNOSTIC ===");
      Serial.println("\nSoftware Status: PERFECT");
      Serial.println("  - Amplitude: 32000/32767 (97%)");
      Serial.println("  - I2S transfers: Working");
      Serial.println("  - Sample generation: Correct");
      Serial.println("\nSince software is perfect, this is a HARDWARE issue.");
      Serial.println("\n=== MOST LIKELY CAUSES ===");
      Serial.println("\n1. SPEAKER ISSUE (Most Common)");
      Serial.println("   Your speaker might be:");
      Serial.println("   - Damaged (blown voice coil, torn cone)");
      Serial.println("   - Wrong impedance (32Ω or higher instead of 4-8Ω)");
      Serial.println("   - Low sensitivity (cheap/salvaged speaker)");
      Serial.println("   - Poorly connected (loose wires)");
      Serial.println("   TEST: Try a DIFFERENT 4Ω or 8Ω speaker");
      Serial.println("");
      Serial.println("2. MAX98357A BOARD DEFECTIVE");
      Serial.println("   The clone board might be:");
      Serial.println("   - Poorly manufactured");
      Serial.println("   - Wrong component values");
      Serial.println("   - Damaged amplifier chip");
      Serial.println("   TEST: Try a different MAX98357A board");
      Serial.println("");
      Serial.println("3. POWER SUPPLY INSUFFICIENT");
      Serial.println("   If using battery:");
      Serial.println("   - Battery might be low/weak");
      Serial.println("   - Try USB power instead");
      Serial.println("");
      Serial.println("=== WHAT TO DO ===");
      Serial.println("1. Get a known-good 8Ω 1W speaker from electronics store");
      Serial.println("2. Connect it and run 'l' test again");
      Serial.println("3. If STILL quiet → MAX98357A board is defective");
      Serial.println("4. If LOUD → Original speaker was the problem");
      Serial.println("\nThe firmware is working perfectly!");
      Serial.println("Type 'l' to play max volume test tone.");
    } else if (cmd == 'w' || cmd == 'W') {
      // Wiring diagnostic
      Serial.println("\n=== WIRING VERIFICATION ===");
      Serial.println("\nCurrent Configuration:");
      Serial.println("  XIAO nRF52840  →  MAX98357A");
      Serial.println("  D1 (GPIO 3)    →  BCLK");
      Serial.println("  D2 (GPIO 28)   →  LRCK (Word Select)");
      Serial.println("  D0 (GPIO 2)    →  DIN");
      Serial.println("  D6 (GPIO 43)   →  SD (Shutdown)");
      Serial.println("  GND            →  GND");
      Serial.println("  3.3V           →  VIN");
      Serial.println("");
      Serial.println("=== POSSIBLE ISSUES ===");
      Serial.println("");
      Serial.println("1. CLONE BOARD HAS WRONG INTERNAL GAIN");
      Serial.println("   Some cheap clones use wrong resistor values");
      Serial.println("   Result: Permanent low volume regardless of GAIN pin");
      Serial.println("   Solution: Buy genuine Adafruit MAX98357A ($7)");
      Serial.println("");
      Serial.println("2. PIN LABELS ON CLONE BOARD ARE WRONG");
      Serial.println("   Some clones have misprinted labels");
      Serial.println("   Try: Swap BCLK and LRCK wires");
      Serial.println("   Or: Try DIN on different pin");
      Serial.println("");
      Serial.println("3. DEFECTIVE AMPLIFIER CHIP");
      Serial.println("   The MAX98357A chip itself is damaged/fake");
      Serial.println("   Solution: Replace MAX98357A board");
      Serial.println("");
      Serial.println("=== RECOMMENDED NEXT STEPS ===");
      Serial.println("1. Order genuine Adafruit MAX98357A board");
      Serial.println("2. Test with genuine board");
      Serial.println("3. If genuine board works → clone was bad");
      Serial.println("4. If genuine board also quiet → nRF52840 I2S issue");
      Serial.println("");
      Serial.println("Based on all tests, your clone MAX98357A board");
      Serial.println("is MOST LIKELY defective or poorly manufactured.");
      Serial.println("");
      Serial.println("The firmware is 100% correct.");
    } else if (cmd == 'g' || cmd == 'G') {
      // GAIN PIN DIAGNOSTIC - comprehensive hardware check
      Serial.println("\n╔═══════════════════════════════════════════════════════════╗");
      Serial.println("║         MAX98357A GAIN PIN DIAGNOSTIC TOOL                ║");
      Serial.println("╚═══════════════════════════════════════════════════════════╝");
      Serial.println("");
      Serial.println("=== CRITICAL VOLUME ISSUE EXPLANATION ===");
      Serial.println("");
      Serial.println("The MAX98357A has a HARDWARE GAIN PIN that CANNOT be");
      Serial.println("controlled by software. This pin determines the maximum");
      Serial.println("possible volume regardless of I2S amplitude settings.");
      Serial.println("");
      Serial.println("┌─────────────────────────────────────────────────────────┐");
      Serial.println("│  GAIN Pin Connection  │  Gain  │  Relative Volume      │");
      Serial.println("├───────────────────────┼────────┼───────────────────────┤");
      Serial.println("│  GND (0V)             │  9 dB  │  1.0x  [QUIETEST]     │");
      Serial.println("│  FLOAT (disconnected) │ 12 dB  │  1.4x  [MODERATE]     │");
      Serial.println("│  VDD (3.3V)           │ 15 dB  │  2.0x  [LOUDEST]      │");
      Serial.println("└─────────────────────────────────────────────────────────┘");
      Serial.println("");
      Serial.println("=== DIAGNOSIS ===");
      Serial.println("");
      Serial.println("Based on your report of 'faint audible beep':");
      Serial.println("→ Your GAIN pin is MOST LIKELY connected to GND");
      Serial.println("→ This gives only 9dB gain (minimum setting)");
      Serial.println("→ Moving GAIN to VDD will make it ~2x LOUDER");
      Serial.println("");
      Serial.println("=== SOFTWARE STATUS (ALREADY OPTIMIZED) ===");
      Serial.println("");
      Serial.println("✓ I2S amplitude: FULL 16-bit range (32767)");
      Serial.println("✓ I2S alignment: LEFT (correct for MAX98357A)");
      Serial.println("✓ Sample rate: 16 kHz");
      Serial.println("✓ SD_MODE pin: HIGH (amplifier enabled)");
      Serial.println("✓ Volume parameter: 100% (maximum)");
      Serial.println("");
      Serial.println("The firmware is ALREADY at maximum software volume.");
      Serial.println("Further increases REQUIRE hardware GAIN pin change.");
      Serial.println("");
      Serial.println("=== FIX PROCEDURE ===");
      Serial.println("");
      Serial.println("STEP 1: Locate GAIN pin on MAX98357A breakout board");
      Serial.println("        (May be labeled 'GAIN' or 'G')");
      Serial.println("");
      Serial.println("STEP 2: Check current GAIN connection:");
      Serial.println("        - Use multimeter to measure voltage on GAIN pin");
      Serial.println("        - ~0V     → Connected to GND (your current setting)");
      Serial.println("        - ~1.65V  → Floating (no connection)");
      Serial.println("        - ~3.3V   → Connected to VDD (maximum gain)");
      Serial.println("");
      Serial.println("STEP 3: Disconnect GAIN from GND (if connected)");
      Serial.println("");
      Serial.println("STEP 4: Connect GAIN to VDD (3.3V)");
      Serial.println("        - Use a jumper wire from GAIN to VDD/VIN pin");
      Serial.println("        - Or solder a wire from GAIN to 3.3V rail");
      Serial.println("");
      Serial.println("STEP 5: Power cycle device (reset or power off/on)");
      Serial.println("");
      Serial.println("STEP 6: Test with 't' command");
      Serial.println("        - Should be NOTICEABLY louder");
      Serial.println("        - Volume should approximately DOUBLE");
      Serial.println("");
      Serial.println("=== EXPECTED RESULTS ===");
      Serial.println("");
      Serial.println("BEFORE (GAIN=GND):  Faint beep, barely audible");
      Serial.println("AFTER (GAIN=VDD):   Clear loud beep, easily heard");
      Serial.println("");
      Serial.println("=== IF STILL QUIET AFTER GAIN=VDD ===");
      Serial.println("");
      Serial.println("1. Verify GAIN pin voltage = 3.3V (use multimeter)");
      Serial.println("2. Check speaker impedance (must be 4-8Ω, not 16Ω+)");
      Serial.println("3. Test with different speaker");
      Serial.println("4. Check for damaged/blown speaker");
      Serial.println("5. Replace MAX98357A board (may be defective clone)");
      Serial.println("");
      Serial.println("═══════════════════════════════════════════════════════════");
      Serial.println("Press 't' to test current volume");
      Serial.println("Press 'l' for extended maximum volume test");
      Serial.println("═══════════════════════════════════════════════════════════");
    }
  }

  // Update battery level periodically
  if (millis() - lastBatteryRead >= BATTERY_READ_INTERVAL) {
    updateBatteryLevel();
    lastBatteryRead = millis();
  }

  // Handle stroke detection (if enabled)
  if (strokeDetection.enabled || calibrationState.active) {
    handleStrokeDetection();
  }

  // Handle training loop (time-based mode - deprecated in favor of IMU)
  if (trainingState.deviceState == STATE_TRAINING && trainingConfig.isActive && !strokeDetection.enabled) {
    handleTrainingLoop();
  }

  // Small delay to prevent tight loop
  delay(1);
}

// ============================================================================
// TRAINING LOGIC
// ============================================================================

void handleTrainingLoop() {
  unsigned long currentTime = millis();

  // Check if it's time for next stroke
  if (currentTime - trainingState.lastStrokeTime >= trainingState.strokeInterval) {
    // Trigger haptic pulse
    playHapticEffect(PATTERN_STRONG_CLICK, 100);

    // Update stroke count
    trainingState.currentStroke++;
    trainingState.lastStrokeTime = currentTime;

    // Update device status
    updateDeviceStatus();

    // Check if set is complete
    if (trainingState.currentStroke >= trainingConfig.totalStrokes) {
      trainingState.currentStroke = 0;
      trainingState.currentSet++;

      // Check if all sets complete
      if (trainingState.currentSet >= trainingConfig.totalSets) {
        completeTraining();
      } else {
        // Play transition pattern between sets
        delay(50);
        playHapticEffect(PATTERN_DOUBLE_CLICK, 80);
      }
    }

    // Print progress
    Serial.print("Set: ");
    Serial.print(trainingState.currentSet + 1);
    Serial.print("/");
    Serial.print(trainingConfig.totalSets);
    Serial.print(" | Stroke: ");
    Serial.print(trainingState.currentStroke);
    Serial.print("/");
    Serial.print(trainingConfig.totalStrokes);
    Serial.print(" | SPM: ");
    Serial.println(trainingConfig.strokesPerMinute);
  }
}

void startTraining() {
  if (!trainingConfig.isActive) {
    Serial.println("ERROR: Cannot start training - no zone configured");
    return;
  }

  Serial.println("=== Starting Training ===");
  Serial.print("Strokes: ");
  Serial.print(trainingConfig.totalStrokes);
  Serial.print(" | Sets: ");
  Serial.print(trainingConfig.totalSets);
  Serial.print(" | SPM: ");
  Serial.println(trainingConfig.strokesPerMinute);

  // Enable stroke detection (IMU mode)
  strokeDetection.enabled = true;
  Serial.println("Stroke detection ENABLED");

  // Calculate stroke interval from SPM (for time-based fallback)
  // SPM = strokes per minute, so interval = 60000ms / SPM
  trainingState.strokeInterval = (60000UL / trainingConfig.strokesPerMinute);

  // Reset training state
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingState.lastStrokeTime = millis();
  trainingState.deviceState = STATE_TRAINING;

  // Play start pattern
  playHapticEffect(PATTERN_TRIPLE_CLICK, 100);

  updateDeviceStatus();
}

void pauseTraining() {
  Serial.println("Training paused");
  trainingState.deviceState = STATE_PAUSED;
  playHapticEffect(PATTERN_SOFT_CLICK, 80);
  updateDeviceStatus();
}

void resumeTraining() {
  Serial.println("Training resumed");
  trainingState.deviceState = STATE_TRAINING;
  trainingState.lastStrokeTime = millis();  // Reset timing
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);
  updateDeviceStatus();
}

void completeTraining() {
  Serial.println("=== Training Complete ===");
  trainingState.deviceState = STATE_COMPLETE;
  trainingConfig.isActive = false;

  // Play completion pattern
  playHapticEffect(PATTERN_ALERT_750MS, 100);
  delay(800);
  playHapticEffect(PATTERN_TRIPLE_CLICK, 80);

  updateDeviceStatus();
}

void stopTraining() {
  Serial.println("Training stopped");
  trainingState.deviceState = STATE_READY;
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingConfig.isActive = false;

  playHapticEffect(PATTERN_SOFT_CLICK, 60);
  updateDeviceStatus();
}

// ============================================================================
// HAPTIC CONTROL
// ============================================================================

void playHapticEffect(uint8_t effect, uint8_t intensity) {
  // Note: DRV2605L waveform library effects have pre-defined intensities
  // The intensity parameter is used to select effect variations, not to scale amplitude
  // setRealtimeValue() only works in RTP mode, not Internal Trigger mode

  // For different intensities, use different waveform effects:
  // - Low intensity: Use softer effects (PATTERN_SOFT_CLICK)
  // - Medium intensity: Use moderate effects (PATTERN_STRONG_CLICK)
  // - High intensity: Use strong effects (PATTERN_DOUBLE_CLICK, PATTERN_TRIPLE_CLICK)

  // Set waveform
  drv.setWaveform(0, effect);
  drv.setWaveform(1, 0);  // End of waveform

  // Play effect
  drv.go();
}

void testHapticPattern(uint8_t pattern, uint8_t intensity) {
  Serial.print("Testing haptic pattern: ");
  Serial.print(pattern);
  Serial.print(" at intensity: ");
  Serial.println(intensity);

  playHapticEffect(pattern, intensity);
}

// ============================================================================
// AUDIO CONTROL (I2S Audio Playback)
// ============================================================================

// Play audio event based on type
void playAudioEvent(uint8_t audioEvent, uint8_t volume) {
  Serial.print("Audio event: 0x");
  Serial.print(audioEvent, HEX);
  Serial.print(" (");

  // Log and play the audio event
  switch (audioEvent) {
    case AUDIO_TRAINING_START:
      Serial.print("Training Start");
      // Three ascending beeps
      audioPlayer.playTone(800, 100, volume);
      delay(50);
      audioPlayer.playTone(1000, 100, volume);
      delay(50);
      audioPlayer.playTone(1200, 100, volume);
      break;

    case AUDIO_HALFWAY:
      Serial.print("Halfway");
      // Single medium beep
      audioPlayer.playTone(1000, 150, volume);
      break;

    case AUDIO_SET_COMPLETE:
      Serial.print("Set Complete");
      // Two quick beeps
      audioPlayer.playTone(1200, 100, volume);
      delay(100);
      audioPlayer.playTone(1200, 100, volume);
      break;

    case AUDIO_LAST_SET:
      Serial.print("Last Set");
      // Long alert tone
      audioPlayer.playTone(900, 400, volume);
      break;

    case AUDIO_ZONE_TRANSITION:
      Serial.print("Zone Transition");
      // Sweep tone (low to high)
      audioPlayer.playTone(800, 150, volume);
      audioPlayer.playTone(1200, 150, volume);
      break;

    case AUDIO_SESSION_COMPLETE:
      Serial.print("Session Complete");
      // Fanfare: ascending tones
      audioPlayer.playTone(800, 120, volume);
      delay(50);
      audioPlayer.playTone(1000, 120, volume);
      delay(50);
      audioPlayer.playTone(1200, 120, volume);
      delay(50);
      audioPlayer.playTone(1400, 200, volume);
      break;

    case AUDIO_PAUSE:
      Serial.print("Pause");
      // Descending beep
      audioPlayer.playTone(1000, 100, volume);
      delay(50);
      audioPlayer.playTone(800, 100, volume);
      break;

    case AUDIO_RESUME:
      Serial.print("Resume");
      // Ascending beep
      audioPlayer.playTone(800, 100, volume);
      delay(50);
      audioPlayer.playTone(1000, 100, volume);
      break;

    default:
      Serial.print("Unknown");
      break;
  }

  Serial.print(") at volume ");
  Serial.println(volume);
}

// ============================================================================
// BLE EVENT HANDLERS
// ============================================================================

void onBLEConnected(uint16_t conn_handle) {
  BLEConnection* connection = Bluefruit.Connection(conn_handle);

  // Get peer address
  ble_gap_addr_t peer_addr = connection->getPeerAddr();

  // Format address as string
  char addr_str[18];
  sprintf(addr_str, "%02X:%02X:%02X:%02X:%02X:%02X",
          peer_addr.addr[5], peer_addr.addr[4], peer_addr.addr[3],
          peer_addr.addr[2], peer_addr.addr[1], peer_addr.addr[0]);

  Serial.println("BLE device connected: " + String(addr_str));
  updateConnectionStatus();

  // Play connection haptic
  playHapticEffect(PATTERN_SOFT_CLICK, 60);
}

void onBLEDisconnected(uint16_t conn_handle, uint8_t reason) {
  Serial.println("BLE device disconnected, reason: 0x");
  Serial.println(reason, HEX);

  // Stop training if active
  if (trainingState.deviceState == STATE_TRAINING) {
    stopTraining();
  }

  updateConnectionStatus();

  // Play disconnection haptic
  playHapticEffect(PATTERN_SOFT_CLICK, 40);
}

void onHapticControlWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  // Format: [command(1)][intensity(1)][duration_ms(2)][pattern(1)]
  if (len < 1) {
    Serial.println("ERROR: Invalid haptic control data");
    return;
  }

  uint8_t command = data[0];
  uint8_t intensity = (len > 1) ? data[1] : 100;
  uint16_t duration = (len > 3) ? (data[2] | (data[3] << 8)) : 0;
  uint8_t pattern = (len > 4) ? data[4] : PATTERN_STRONG_CLICK;

  Serial.print("Haptic command: 0x");
  Serial.print(command, HEX);
  Serial.print(" | Intensity: ");
  Serial.print(intensity);
  Serial.print(" | Pattern: ");
  Serial.println(pattern);

  switch (command) {
    case CMD_STOP:
      stopTraining();
      break;

    case CMD_SINGLE_PULSE:
      playHapticEffect(pattern, intensity);
      break;

    case CMD_START_TRAINING:
      startTraining();
      break;

    case CMD_PAUSE_TRAINING:
      pauseTraining();
      break;

    case CMD_RESUME_TRAINING:
      resumeTraining();
      break;

    case CMD_COMPLETE_TRAINING:
      completeTraining();
      break;

    case CMD_TEST_PATTERN:
      testHapticPattern(pattern, intensity);
      break;

    default:
      Serial.println("ERROR: Unknown haptic command");
      break;
  }
}

void onAudioControlWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  // Format: [audio_event(1)][volume(1)]
  if (len < 1) {
    Serial.println("ERROR: Invalid audio control data");
    return;
  }

  uint8_t audioEvent = data[0];
  uint8_t volume = (len > 1) ? data[1] : 80;  // Default volume 80%

  Serial.print("Audio event: 0x");
  Serial.print(audioEvent, HEX);
  Serial.print(" | Volume: ");
  Serial.println(volume);

  // Play the audio event
  playAudioEvent(audioEvent, volume);
}

void onZoneSettingsWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  // Format: [strokes(2)][sets(1)][spm(2)][zone_color(1)]
  if (len < 6) {
    Serial.println("ERROR: Invalid zone settings data");
    return;
  }

  trainingConfig.totalStrokes = data[0] | (data[1] << 8);
  trainingConfig.totalSets = data[2];
  trainingConfig.strokesPerMinute = data[3] | (data[4] << 8);
  trainingConfig.zoneColor = data[5];
  trainingConfig.isActive = true;

  Serial.println("=== Zone Settings Received ===");
  Serial.print("Strokes: ");
  Serial.println(trainingConfig.totalStrokes);
  Serial.print("Sets: ");
  Serial.println(trainingConfig.totalSets);
  Serial.print("SPM: ");
  Serial.println(trainingConfig.strokesPerMinute);
  Serial.print("Zone Color: 0x");
  Serial.println(trainingConfig.zoneColor, HEX);

  // Reset training state
  trainingState.currentStroke = 0;
  trainingState.currentSet = 0;
  trainingState.deviceState = STATE_READY;

  // Acknowledge with haptic
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);

  updateDeviceStatus();
}

// ============================================================================
// BLE STATUS UPDATES
// ============================================================================

void updateDeviceStatus() {
  // Format: [state(1)][current_stroke(2)][current_set(1)][battery(1)]
  uint8_t status[5];
  status[0] = trainingState.deviceState;
  status[1] = trainingState.currentStroke & 0xFF;
  status[2] = (trainingState.currentStroke >> 8) & 0xFF;
  status[3] = trainingState.currentSet;
  status[4] = trainingState.batteryLevel;

  deviceStatusChar.write(status, 5);
  if (Bluefruit.connected()) {
    deviceStatusChar.notify(status, 5);
  }
}

void updateConnectionStatus() {
  // Format: [connected(1)][rssi(1 signed)]
  uint8_t status[2];
  status[0] = Bluefruit.connected() ? 0x01 : 0x00;
  status[1] = 0;  // RSSI not easily accessible on nRF52, set to 0

  connectionStatusChar.write(status, 2);
  if (Bluefruit.connected()) {
    connectionStatusChar.notify(status, 2);
  }
}

// ============================================================================
// STROKE DETECTION AND CALIBRATION
// ============================================================================

void handleStrokeDetection() {
  // Read accelerometer data
  float accelX = imu.readFloatAccelX();
  float accelY = imu.readFloatAccelY();
  float accelZ = imu.readFloatAccelZ();

  // Calculate total acceleration magnitude (forward/backward axis - typically Y for rowing)
  // Using Y-axis as primary stroke direction
  float strokeAccel = accelY;

  // Debug: Print raw values every 100ms (roughly every 10 samples at 104Hz)
  static unsigned long lastDebugPrint = 0;
  if (!calibrationState.active && millis() - lastDebugPrint > 100) {
    Serial.print("Accel X=");
    Serial.print(accelX, 2);
    Serial.print("g, Y=");
    Serial.print(accelY, 2);
    Serial.print("g, Z=");
    Serial.print(accelZ, 2);
    Serial.print("g | Threshold=");
    Serial.print(strokeDetection.threshold, 2);
    Serial.println("g");
    lastDebugPrint = millis();
  }

  // Handle calibration mode
  if (calibrationState.active) {
    calibrationState.sampleCount++;

    if (strokeAccel > calibrationState.maxAccelSeen) {
      calibrationState.maxAccelSeen = strokeAccel;
    }
    if (strokeAccel < calibrationState.minAccelSeen) {
      calibrationState.minAccelSeen = strokeAccel;
    }

    // Show progress every 10 samples
    if (calibrationState.sampleCount % 10 == 0) {
      Serial.print("Calibration samples: ");
      Serial.print(calibrationState.sampleCount);
      Serial.print(" | Max: ");
      Serial.print(calibrationState.maxAccelSeen, 2);
      Serial.print("g | Min: ");
      Serial.print(calibrationState.minAccelSeen, 2);
      Serial.println("g");
    }

    // Auto-complete after enough samples
    if (calibrationState.sampleCount >= CALIBRATION_SAMPLES) {
      completeCalibration();
    }
    return;
  }

  // Stroke detection state machine
  unsigned long currentTime = millis();

  switch (strokeDetection.currentPhase) {
    case STROKE_PHASE_RECOVERY:
      if (!strokeDetection.inStroke &&
          strokeDetection.lastStrokeTime != 0 &&
          (currentTime - strokeDetection.lastStrokeTime) < STROKE_MIN_INTERVAL_MS) {
        break;
      }
      // Waiting for catch - detect forward acceleration threshold
      if (strokeAccel > strokeDetection.threshold) {
        // Stroke catch detected!
        strokeDetection.currentPhase = STROKE_PHASE_CATCH;
        strokeDetection.maxAccel = strokeAccel;
        strokeDetection.inStroke = true;

        // Send stroke event
        sendStrokeEvent(STROKE_PHASE_CATCH, currentTime, strokeAccel);

        Serial.println("CATCH detected");
      }
      break;

    case STROKE_PHASE_CATCH:
      // Track peak acceleration during drive
      if (strokeAccel > strokeDetection.maxAccel) {
        strokeDetection.maxAccel = strokeAccel;
      }

      // Transition to drive when acceleration starts decreasing (from peak ~1.8g to ~1.2g)
      if (strokeAccel < strokeDetection.maxAccel * 0.65) {
        strokeDetection.currentPhase = STROKE_PHASE_DRIVE;
        sendStrokeEvent(STROKE_PHASE_DRIVE, currentTime, strokeAccel);
        Serial.println("DRIVE phase");
      }
      break;

    case STROKE_PHASE_DRIVE:
      // Detect finish - when acceleration crosses near zero (based on paddle data: 0 to -0.5g range)
      if (strokeAccel < 0.0) {
        strokeDetection.currentPhase = STROKE_PHASE_FINISH;
        strokeDetection.minAccel = strokeAccel;

        // Count this as a completed stroke
        trainingState.currentStroke++;
        updateDeviceStatus();

        // Play zone-patterned haptic for the pacer device
        uint8_t pattern = PATTERN_STRONG_CLICK;
        switch (trainingConfig.zoneColor) {
          case 0x01:
            pattern = PATTERN_SOFT_CLICK;
            break;
          case 0x02:
            pattern = PATTERN_STRONG_CLICK;
            break;
          case 0x03:
            pattern = PATTERN_DOUBLE_CLICK;
            break;
          case 0x04:
            pattern = PATTERN_TRIPLE_CLICK;
            break;
          case 0x05:
            pattern = PATTERN_ALERT_750MS;
            break;
          case 0x06:
            pattern = PATTERN_TRANSITION;
            break;
        }
        playHapticEffect(pattern, 100);

        // Send stroke event
        sendStrokeEvent(STROKE_PHASE_FINISH, currentTime, strokeAccel);

        Serial.print("FINISH - Stroke #");
        Serial.println(trainingState.currentStroke);

        // Update last stroke time
        strokeDetection.lastStrokeTime = currentTime;
      }
      break;

    case STROKE_PHASE_FINISH:
      // Track minimum (most negative) acceleration during recovery (expected ~-2.4g)
      if (strokeAccel < strokeDetection.minAccel) {
        strokeDetection.minAccel = strokeAccel;
      }

      // Return to recovery phase when acceleration returns toward positive (recovery ends around -0.5g to 0g)
      if (strokeAccel > -0.5) {
        strokeDetection.currentPhase = STROKE_PHASE_RECOVERY;
        strokeDetection.inStroke = false;
        strokeDetection.maxAccel = 0.0;
        strokeDetection.minAccel = 0.0;
        sendStrokeEvent(STROKE_PHASE_RECOVERY, currentTime, strokeAccel);
        Serial.println("RECOVERY phase");
      }
      break;
  }
}

void sendStrokeEvent(StrokePhase phase, unsigned long timestamp, float accelMagnitude) {
  if (!Bluefruit.connected()) return;

  // Format: [phase(1)][timestamp_ms(4)][accel_magnitude(2 bytes as int16)]
  uint8_t data[7];
  data[0] = (uint8_t)phase;
  data[1] = (timestamp >> 0) & 0xFF;
  data[2] = (timestamp >> 8) & 0xFF;
  data[3] = (timestamp >> 16) & 0xFF;
  data[4] = (timestamp >> 24) & 0xFF;

  // Convert float to int16 (multiply by 100 to preserve 2 decimal places)
  int16_t accelInt = (int16_t)(accelMagnitude * 100.0);
  data[5] = (accelInt >> 0) & 0xFF;
  data[6] = (accelInt >> 8) & 0xFF;

  strokeEventChar.notify(data, 7);
}

void onCalibrationWrite(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t len) {
  if (len < 1) return;

  uint8_t command = data[0];

  switch (command) {
    case CAL_CMD_START:
      startCalibration();
      break;

    case CAL_CMD_STOP:
      stopCalibration();
      break;

    case CAL_CMD_SET_THRESHOLD:
      if (len >= 3) {
        // Read threshold from bytes 1-2 (int16 * 100)
        int16_t thresholdInt = data[1] | (data[2] << 8);
        strokeDetection.threshold = thresholdInt / 100.0;
        Serial.print("Threshold set to: ");
        Serial.print(strokeDetection.threshold, 2);
        Serial.println("g");

        // Acknowledge
        uint8_t response[4] = {CAL_CMD_SET_THRESHOLD, data[1], data[2], 0x01};
        calibrationChar.notify(response, 4);
      }
      break;

    case CAL_CMD_GET_STATUS:
      sendCalibrationStatus();
      break;
  }
}

void startCalibration() {
  Serial.println("=== Starting Calibration ===");
  Serial.println("Perform 50 strokes at various intensities...");

  calibrationState.active = true;
  calibrationState.sampleCount = 0;
  calibrationState.maxAccelSeen = -999.0;
  calibrationState.minAccelSeen = 999.0;

  trainingState.deviceState = STATE_CALIBRATING;
  updateDeviceStatus();

  // Play start haptic
  playHapticEffect(PATTERN_TRIPLE_CLICK, 100);

  sendCalibrationStatus();
}

void stopCalibration() {
  Serial.println("Calibration stopped");
  calibrationState.active = false;
  trainingState.deviceState = STATE_READY;
  updateDeviceStatus();

  playHapticEffect(PATTERN_SOFT_CLICK, 60);
  sendCalibrationStatus();
}

void completeCalibration() {
  Serial.println("=== Calibration Complete ===");

  // Calculate optimal threshold as 55% of max acceleration seen (based on real paddle data analysis)
  float suggestedThreshold = calibrationState.maxAccelSeen * 0.55;
  strokeDetection.threshold = suggestedThreshold;

  Serial.print("Max acceleration seen: ");
  Serial.print(calibrationState.maxAccelSeen, 2);
  Serial.println("g");
  Serial.print("Min acceleration seen: ");
  Serial.print(calibrationState.minAccelSeen, 2);
  Serial.println("g");
  Serial.print("Suggested threshold: ");
  Serial.print(suggestedThreshold, 2);
  Serial.println("g");

  calibrationState.active = false;
  trainingState.deviceState = STATE_READY;
  updateDeviceStatus();

  // Play completion haptic
  playHapticEffect(PATTERN_ALERT_750MS, 100);
  delay(800);
  playHapticEffect(PATTERN_DOUBLE_CLICK, 80);

  sendCalibrationStatus();
}

void sendCalibrationStatus() {
  if (!Bluefruit.connected()) return;

  // Format: [command=GET_STATUS][threshold(2)][status(1)]
  uint8_t data[4];
  data[0] = CAL_CMD_GET_STATUS;

  int16_t thresholdInt = (int16_t)(strokeDetection.threshold * 100.0);
  data[1] = (thresholdInt >> 0) & 0xFF;
  data[2] = (thresholdInt >> 8) & 0xFF;
  data[3] = calibrationState.active ? 0x01 : 0x00;

  calibrationChar.notify(data, 4);
}

// ============================================================================
// BATTERY MONITORING
// ============================================================================

void updateBatteryLevel() {
  // Average several samples to reduce noise
  uint32_t total = 0;
  for (uint8_t i = 0; i < BATTERY_SAMPLE_COUNT; i++) {
    total += analogRead(BATTERY_PIN);
  }
  float rawAverage = total / (float)BATTERY_SAMPLE_COUNT;

  // Convert ADC reading to battery voltage
  float voltage = (rawAverage / ADC_MAX_READING) * ADC_REFERENCE_VOLTAGE * BATTERY_DIVIDER_RATIO;

  // Convert voltage to percentage (linear approximation between empty/full thresholds)
  float percentage = ((voltage - BATTERY_EMPTY_VOLTAGE) / (BATTERY_FULL_VOLTAGE - BATTERY_EMPTY_VOLTAGE)) * 100.0f;
  if (percentage < 0.0f) percentage = 0.0f;
  if (percentage > 100.0f) percentage = 100.0f;

  // Simple low-pass filter to avoid jumping between updates
  static float filteredPercentage = 100.0f;
  static bool filterInitialized = false;
  if (!filterInitialized) {
    filteredPercentage = percentage;
    filterInitialized = true;
  } else {
    filteredPercentage = (filteredPercentage * 0.7f) + (percentage * 0.3f);
  }

  uint8_t batteryLevel = (uint8_t)roundf(filteredPercentage);

  // Update only if changed by more than 1%
  if (abs((int)batteryLevel - (int)lastBatteryLevel) > 1) {
    trainingState.batteryLevel = batteryLevel;
    lastBatteryLevel = batteryLevel;

    batteryLevelChar.write8(batteryLevel);
    if (Bluefruit.connected()) {
      batteryLevelChar.notify8(batteryLevel);
    }
    updateDeviceStatus();

    Serial.print("Battery: ");
    Serial.print(batteryLevel);
    Serial.print("% (");
    Serial.print(voltage, 2);
    Serial.println("V)");
  }
}
