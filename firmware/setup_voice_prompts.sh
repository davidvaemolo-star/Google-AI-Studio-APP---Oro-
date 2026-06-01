#!/bin/bash
# One-click setup for voice prompts
# Runs both Python scripts automatically

echo "============================================================"
echo " Oro Haptic Paddle - Voice Prompts Setup"
echo "============================================================"
echo ""

echo "Step 1: Generating voice prompts with TTS..."
python3 generate_voice_simple.py
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Voice generation failed!"
    echo "Please install dependencies: pip3 install gtts pydub"
    exit 1
fi

echo ""
echo "Step 2: Converting to C arrays..."
python3 convert_to_c_arrays.py
if [ $? -ne 0 ]; then
    echo ""
    echo "ERROR: Conversion failed!"
    exit 1
fi

echo ""
echo "============================================================"
echo " SUCCESS! Voice prompts are ready."
echo "============================================================"
echo ""
echo "Next steps:"
echo "  1. Open OroHapticFirmware.ino in Arduino IDE"
echo "  2. Add this line near the top:"
echo "     #include \"audio_prompts.h\""
echo "  3. Update playAudioEvent() function (see VOICE_PROMPTS_SETUP.md)"
echo "  4. Upload to your device"
echo ""
echo "For detailed instructions, see: VOICE_PROMPTS_SETUP.md"
echo "============================================================"
