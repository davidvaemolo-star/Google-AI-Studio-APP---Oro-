#!/usr/bin/env python3
"""
Record the Oro paddle's serial output to a file until you stop it with Ctrl+C.

Use this to capture a whole training session, including the end-of-session
audio decision. The interesting lines are:
    Audio event: 0x...                         (what the phone asked for)
    Playing: session summary voice 0x...
    [extAudio] ...                             (why the prompt did/didn't play)
    Summary voice OK  /  Summary voice failed -- chime fallback

Usage (close the Arduino Serial Monitor first so the port is free):
    python capture_boot.py --port COM16

Run a full training session, then press Ctrl+C after the session ends.
A dot prints for every line heard. Everything is saved to boot_log.txt.
"""
import argparse
import sys
import time

try:
    import serial
except ImportError:
    print("pyserial not installed. Run:  pip install pyserial", file=sys.stderr)
    sys.exit(1)

OUT_PATH = "boot_log.txt"
KEYWORDS = ("extaudio", "summary voice", "session summary",
            "audio event:", "external audio", "jedec", "oro haptic paddle")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", required=True, help="Serial port, e.g. COM16")
    ap.add_argument("--baud", type=int, default=115200)
    args = ap.parse_args()

    print(f"Recording {args.port} at {args.baud}.  (A dot = one line heard.)")
    print("Run a full training session, then press Ctrl+C when it's finished.\n")

    ser = None
    highlights = []
    lines_heard = 0

    out = open(OUT_PATH, "w", encoding="utf-8", errors="replace")
    try:
        while True:
            if ser is None or not ser.is_open:
                try:
                    ser = serial.Serial(args.port, args.baud, timeout=0.2)
                except (serial.SerialException, OSError):
                    time.sleep(0.1)
                    continue
            try:
                raw = ser.readline()
            except (serial.SerialException, OSError):
                try:
                    ser.close()
                except Exception:
                    pass
                ser = None
                continue
            if not raw:
                continue
            line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
            out.write(line + "\n")
            out.flush()
            lines_heard += 1
            sys.stdout.write(".")
            sys.stdout.flush()
            if any(k in line.lower() for k in KEYWORDS):
                highlights.append(line)
    except KeyboardInterrupt:
        pass
    finally:
        out.close()
        if ser is not None:
            try:
                ser.close()
            except Exception:
                pass

    print("\n\n" + "=" * 50)
    print(f"Lines heard: {lines_heard}")
    if highlights:
        print("\nAudio-related lines:\n")
        for h in highlights:
            print("  " + h)
    else:
        print("No audio-related lines were captured.")
    print("=" * 50)
    print(f"\nFull log saved to {OUT_PATH}")


if __name__ == "__main__":
    main()
