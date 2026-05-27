#!/usr/bin/env python3
"""
Stream firmware/audio_blob.bin to a device running OroAudioFlasher.ino.

Usage:
    python flash_audio.py --port COM7
    python flash_audio.py --port /dev/tty.usbmodem1101

Requires:
    pip install pyserial
"""
import argparse
import os
import sys
import time

import serial

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BLOB_PATH = os.path.join(SCRIPT_DIR, "audio_blob.bin")

def expect(ser, expected_prefix, timeout=30.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        line = ser.readline().decode("utf-8", "replace").strip()
        if not line:
            continue
        print(f"  <- {line}")
        if line.startswith(expected_prefix):
            return line
        if line.startswith("ERR"):
            raise RuntimeError(f"device error: {line}")
    raise TimeoutError(f"timeout waiting for '{expected_prefix}'")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", required=True)
    ap.add_argument("--baud", type=int, default=115200)
    ap.add_argument("--blob", default=BLOB_PATH)
    args = ap.parse_args()

    if not os.path.exists(args.blob):
        print(f"ERROR: blob not found at {args.blob}. Run build_audio_blob.py first.", file=sys.stderr)
        sys.exit(1)
    with open(args.blob, "rb") as f:
        blob = f.read()
    print(f"Blob: {args.blob}  ({len(blob)} bytes)")

    with serial.Serial(args.port, args.baud, timeout=5) as ser:
        time.sleep(2.0)  # let the board settle / USB CDC enumerate
        ser.reset_input_buffer()

        ser.write(b"PING\n");           print("-> PING")
        expect(ser, "OROFLASHER")

        ser.write(b"ERASE\n");          print("-> ERASE")
        expect(ser, "ERASED", timeout=60.0)

        ser.write(f"WRITE {len(blob)}\n".encode()); print(f"-> WRITE {len(blob)}")
        expect(ser, "READY")
        # Stream in 4 KB chunks; flush after each so the OS buffer doesn't grow unbounded.
        CHUNK = 4096
        for i in range(0, len(blob), CHUNK):
            ser.write(blob[i:i+CHUNK])
            ser.flush()
        expect(ser, "WROTE", timeout=60.0)

        ser.write(b"VERIFY 4\n");       print("-> VERIFY 4")
        line = expect(ser, "MAGIC")
        if line != "MAGIC OROA":
            raise RuntimeError(f"magic check failed: {line}")

        ser.write(b"DONE\n");           print("-> DONE")
        expect(ser, "BYE")

    print("\nFlash complete. Re-upload OroHapticFirmware.ino to the device.")

if __name__ == "__main__":
    main()
