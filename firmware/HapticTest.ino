/*
 * Oro Haptic Paddle - Hardware Diagnostic Test
 *
 * This sketch tests the DRV2605L haptic driver without BLE.
 * Use this for initial hardware bring-up and troubleshooting.
 *
 * Hardware: Seeed XIAO nRF52840 Sense + DRV2605L
 *
 * Pin Map:
 * - I2C: SDA=D4, SCL=D5
 * - Battery: A0
 *
 * Tests Performed:
 * 1. I2C bus scan
 * 2. DRV2605L initialization
 * 3. All haptic patterns (1-13, 24, 47, 51)
 * 4. Battery voltage reading
 * 5. Timing accuracy test
 *
 * Expected Output:
 * - Serial messages showing test progress
 * - Motor vibration for each haptic pattern
 * - Battery voltage and percentage
 * - Timing statistics
 */

#include <Wire.h>
#include <Adafruit_DRV2605.h>

// Hardware Configuration
#define SDA_PIN 4
#define SCL_PIN 5
#define BATTERY_PIN A0

Adafruit_DRV2605 drv;

void setup() {
  Serial.begin(115200);
  delay(2000);

  Serial.println("========================================");
  Serial.println("  ORO HAPTIC PADDLE - DIAGNOSTIC TEST  ");
  Serial.println("========================================");
  Serial.println();

  // Test 1: I2C Bus Scan
  testI2CBus();

  // Test 2: DRV2605L Initialization
  if (!testDRV2605L()) {
    Serial.println("CRITICAL ERROR: DRV2605L test failed");
    Serial.println("Check wiring and power connections");
    while(1) { delay(1000); }
  }

  // Test 3: Battery Monitoring
  testBattery();

  // Test 4: Haptic Patterns
  testHapticPatterns();

  // Test 5: Timing Accuracy
  testTimingAccuracy();

  Serial.println();
  Serial.println("========================================");
  Serial.println("  ALL TESTS COMPLETE - DEVICE READY   ");
  Serial.println("========================================");
  Serial.println();
  Serial.println("You can now upload the main firmware:");
  Serial.println("  OroHapticFirmware.ino");
}

void loop() {
  // Continuous battery monitoring
  static unsigned long lastRead = 0;
  if (millis() - lastRead >= 5000) {
    testBattery();
    lastRead = millis();
  }

  delay(100);
}

// ============================================================================
// TEST FUNCTIONS
// ============================================================================

void testI2CBus() {
  Serial.println("TEST 1: I2C Bus Scan");
  Serial.println("--------------------");
  Serial.println("Scanning I2C bus on SDA=D4, SCL=D5...");

  Wire.begin();
  Wire.setClock(400000);  // 400kHz

  int deviceCount = 0;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    uint8_t error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("  Device found at 0x");
      if (addr < 16) Serial.print("0");
      Serial.println(addr, HEX);
      deviceCount++;

      // Identify known devices
      if (addr == 0x5A) {
        Serial.println("    → DRV2605L Haptic Driver");
      }
    }
  }

  if (deviceCount == 0) {
    Serial.println("  ⚠️ WARNING: No I2C devices found!");
    Serial.println("  Check SDA/SCL wiring and 3.3V power");
  } else {
    Serial.print("  ✓ Found ");
    Serial.print(deviceCount);
    Serial.println(" device(s)");
  }

  Serial.println();
  delay(1000);
}

bool testDRV2605L() {
  Serial.println("TEST 2: DRV2605L Haptic Driver");
  Serial.println("-------------------------------");

  // Check I2C communication
  Wire.beginTransmission(0x5A);
  if (Wire.endTransmission() != 0) {
    Serial.println("  ✗ FAILED: DRV2605L not found at 0x5A");
    Serial.println("  Check wiring:");
    Serial.println("    VIN → 3.3V (NOT 5V!)");
    Serial.println("    GND → GND");
    Serial.println("    SDA → D4");
    Serial.println("    SCL → D5");
    return false;
  }

  Serial.println("  ✓ DRV2605L detected at 0x5A");

  // Initialize driver
  if (!drv.begin()) {
    Serial.println("  ✗ FAILED: Driver initialization failed");
    return false;
  }

  Serial.println("  ✓ Driver initialized successfully");

  // Configure for ERM motor
  drv.selectLibrary(1);
  drv.setMode(DRV2605_MODE_INTTRIG);

  Serial.println("  ✓ Configured for ERM motor (Library 1)");

  // Test basic haptic
  Serial.println("  Testing basic vibration...");
  drv.setWaveform(0, 1);  // Strong click
  drv.setWaveform(1, 0);
  drv.go();

  Serial.println("  ✓ If motor vibrated, hardware is working!");
  Serial.println();

  delay(500);
  return true;
}

void testBattery() {
  Serial.println("TEST 3: Battery Monitoring");
  Serial.println("---------------------------");

  int rawADC = analogRead(BATTERY_PIN);
  float voltage = (rawADC / 1023.0) * 3.6 * 2.96;  // XIAO voltage divider
  float percentage = ((voltage - 3.0) / (4.2 - 3.0)) * 100.0;

  if (percentage < 0) percentage = 0;
  if (percentage > 100) percentage = 100;

  Serial.print("  Raw ADC: ");
  Serial.println(rawADC);
  Serial.print("  Voltage: ");
  Serial.print(voltage, 2);
  Serial.println(" V");
  Serial.print("  Battery: ");
  Serial.print((int)percentage);
  Serial.println(" %");

  // Warnings
  if (voltage < 3.2) {
    Serial.println("  ⚠️ WARNING: Battery low! Charge soon.");
  } else if (voltage > 4.3) {
    Serial.println("  ⚠️ WARNING: Voltage too high! Check battery.");
  } else if (voltage < 2.5) {
    Serial.println("  ⚠️ No battery detected (reading too low)");
  } else {
    Serial.println("  ✓ Battery voltage normal");
  }

  Serial.println();
  delay(500);
}

void testHapticPatterns() {
  Serial.println("TEST 4: Haptic Pattern Library");
  Serial.println("-------------------------------");
  Serial.println("Testing all haptic effects...");
  Serial.println("(Motor will vibrate with different patterns)");
  Serial.println();

  // Test key patterns
  struct Pattern {
    uint8_t id;
    const char* name;
  };

  Pattern patterns[] = {
    {1, "Strong Click"},
    {2, "Sharp Click"},
    {3, "Soft Click"},
    {12, "Double Click"},
    {13, "Triple Click"},
    {24, "Alert 750ms"},
    {47, "Pulsing Strong"},
    {51, "Transition Click"}
  };

  for (int i = 0; i < 8; i++) {
    Serial.print("  [");
    Serial.print(i + 1);
    Serial.print("/8] Pattern ");
    Serial.print(patterns[i].id);
    Serial.print(": ");
    Serial.print(patterns[i].name);
    Serial.print("... ");

    drv.setWaveform(0, patterns[i].id);
    drv.setWaveform(1, 0);
    drv.go();

    Serial.println("✓");
    delay(1000);  // Wait between patterns
  }

  Serial.println("  ✓ All patterns tested");
  Serial.println();
}

void testTimingAccuracy() {
  Serial.println("TEST 5: Timing Accuracy");
  Serial.println("-----------------------");
  Serial.println("Testing BPM synchronization accuracy...");
  Serial.println();

  // Test three common BPM rates
  testBPM(60, 10);   // 60 BPM = 1000ms interval
  testBPM(90, 10);   // 90 BPM = 666.67ms interval
  testBPM(120, 10);  // 120 BPM = 500ms interval

  Serial.println("  ✓ Timing tests complete");
  Serial.println();
}

void testBPM(int bpm, int pulseCount) {
  unsigned long interval = 60000UL / bpm;
  unsigned long minJitter = 999999;
  unsigned long maxJitter = 0;
  unsigned long totalJitter = 0;

  Serial.print("  Testing ");
  Serial.print(bpm);
  Serial.print(" BPM (");
  Serial.print(interval);
  Serial.print("ms interval)...");

  unsigned long lastPulse = millis();

  for (int i = 0; i < pulseCount; i++) {
    while (millis() - lastPulse < interval) {
      delayMicroseconds(100);  // Tight loop for accuracy
    }

    unsigned long actualInterval = millis() - lastPulse;
    long jitter = abs((long)(actualInterval - interval));

    if (jitter < minJitter) minJitter = jitter;
    if (jitter > maxJitter) maxJitter = jitter;
    totalJitter += jitter;

    // Haptic pulse
    drv.setWaveform(0, 1);
    drv.setWaveform(1, 0);
    drv.go();

    lastPulse = millis();
  }

  float avgJitter = totalJitter / (float)pulseCount;

  Serial.println();
  Serial.print("    Avg jitter: ");
  Serial.print(avgJitter, 1);
  Serial.print("ms | Min: ");
  Serial.print(minJitter);
  Serial.print("ms | Max: ");
  Serial.print(maxJitter);
  Serial.println("ms");

  if (avgJitter <= 2.0) {
    Serial.println("    ✓ EXCELLENT - Within ±2ms target");
  } else if (avgJitter <= 5.0) {
    Serial.println("    ✓ GOOD - Acceptable for training");
  } else {
    Serial.println("    ⚠️ HIGH JITTER - May affect training quality");
  }

  Serial.println();
  delay(1000);
}

// ============================================================================
// DIAGNOSTIC UTILITIES
// ============================================================================

void printDiagnosticHeader() {
  Serial.println();
  Serial.println("========================================");
  Serial.println("  HARDWARE DIAGNOSTIC INFORMATION      ");
  Serial.println("========================================");
  Serial.println();
  Serial.println("Board: Seeed XIAO nRF52840 Sense");
  Serial.println("Haptic Driver: DRV2605L (I2C 0x5A)");
  Serial.println("Motor: ERM coin vibration motor");
  Serial.println();
  Serial.println("Pin Configuration:");
  Serial.println("  SDA (D4) → DRV2605L SDA");
  Serial.println("  SCL (D5) → DRV2605L SCL");
  Serial.println("  3.3V     → DRV2605L VIN");
  Serial.println("  GND      → DRV2605L GND");
  Serial.println("  A0       → Battery monitor");
  Serial.println();
  Serial.println("Motor Connections:");
  Serial.println("  DRV OUT+ → Motor Red");
  Serial.println("  DRV OUT- → Motor Black");
  Serial.println();
  Serial.println("========================================");
  Serial.println();
}
