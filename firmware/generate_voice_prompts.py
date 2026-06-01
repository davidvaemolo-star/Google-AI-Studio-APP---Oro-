#!/usr/bin/env python3
"""
Voice Prompt Generator for Oro Haptic Paddle
Generates TTS audio files using Google Cloud TTS (higher quality than gTTS)
Requires: pip install google-cloud-texttospeech pydub
"""

import os
from google.cloud import texttospeech
from pydub import AudioSegment

# Audio prompts to generate
PROMPTS = {
    "training_start": "Training start",
    "halfway": "Halfway",
    "set_complete": "Set complete",
    "last_set": "Last set",
    "zone_transition": "Zone transition",
    "session_complete": "Session complete",
    "pause": "Pause",
    "resume": "Resume",
    "power_on": "Oro",
    "countdown_3": "3",
    "countdown_2": "2",
    "countdown_1": "1"
}

def generate_tts_files():
    """Generate TTS audio files using Google Cloud TTS"""

    # Initialize the TTS client
    client = texttospeech.TextToSpeechClient()

    # Configure voice parameters
    voice = texttospeech.VoiceSelectionParams(
        language_code="en-US",
        ssml_gender=texttospeech.SsmlVoiceGender.FEMALE,
        name="en-US-Neural2-F"  # High quality neural voice
    )

    # Configure audio output
    audio_config = texttospeech.AudioConfig(
        audio_encoding=texttospeech.AudioEncoding.LINEAR16,
        sample_rate_hertz=16000,  # Match I2S sample rate
        speaking_rate=1.2,  # Fast speech pace (1.0 = normal, 1.2 = 20% faster)
        pitch=0.0,
        volume_gain_db=0.0
    )

    output_dir = "voice_prompts_raw"
    os.makedirs(output_dir, exist_ok=True)

    print("Generating voice prompts with Google Cloud TTS...")
    print("Voice: Female US Neural (fast pace)\n")

    for filename, text in PROMPTS.items():
        print(f"Generating: {text} -> {filename}.wav")

        # Create synthesis input
        synthesis_input = texttospeech.SynthesisInput(text=text)

        # Perform the TTS request
        response = client.synthesize_speech(
            input=synthesis_input,
            voice=voice,
            audio_config=audio_config
        )

        # Save the raw WAV file (16kHz, 16-bit, mono)
        output_path = os.path.join(output_dir, f"{filename}.wav")
        with open(output_path, "wb") as out:
            out.write(response.audio_content)

        # Load and verify the audio
        audio = AudioSegment.from_wav(output_path)
        duration_ms = len(audio)
        sample_rate = audio.frame_rate
        channels = audio.channels
        sample_width = audio.sample_width * 8  # Convert bytes to bits

        print(f"  ✓ Duration: {duration_ms}ms")
        print(f"  ✓ Format: {sample_rate}Hz, {channels} channel(s), {sample_width}-bit")
        print(f"  ✓ Saved to: {output_path}\n")

    print(f"\n✓ All prompts generated in '{output_dir}/' directory")
    print("\nNext step: Run convert_to_c_arrays.py to generate firmware headers")

def generate_with_fallback():
    """Fallback to pyttsx3 if Google Cloud TTS is not available"""
    try:
        import pyttsx3
        from pydub import AudioSegment
        from pydub.generators import Sine
        import wave
        import struct
    except ImportError:
        print("Error: Please install required packages:")
        print("  pip install pyttsx3 pydub")
        return

    output_dir = "voice_prompts_raw"
    os.makedirs(output_dir, exist_ok=True)

    print("Generating voice prompts with pyttsx3 (offline TTS)...")
    print("Voice: Female US (fast pace)\n")

    # Initialize pyttsx3
    engine = pyttsx3.init()

    # Set voice properties
    voices = engine.getProperty('voices')
    # Try to find a female voice
    female_voice = None
    for voice in voices:
        if 'female' in voice.name.lower() or 'zira' in voice.name.lower():
            female_voice = voice.id
            break

    if female_voice:
        engine.setProperty('voice', female_voice)

    engine.setProperty('rate', 200)  # Fast speech (default ~200, normal ~150)
    engine.setProperty('volume', 1.0)

    for filename, text in PROMPTS.items():
        print(f"Generating: {text} -> {filename}.wav")

        # Generate WAV file
        temp_path = os.path.join(output_dir, f"{filename}_temp.wav")
        output_path = os.path.join(output_dir, f"{filename}.wav")

        engine.save_to_file(text, temp_path)
        engine.runAndWait()

        # Convert to 16kHz mono using pydub
        audio = AudioSegment.from_wav(temp_path)
        audio = audio.set_frame_rate(16000).set_channels(1).set_sample_width(2)
        audio.export(output_path, format="wav")

        # Remove temp file
        os.remove(temp_path)

        duration_ms = len(audio)
        print(f"  ✓ Duration: {duration_ms}ms")
        print(f"  ✓ Saved to: {output_path}\n")

    print(f"\n✓ All prompts generated in '{output_dir}/' directory")
    print("\nNext step: Run convert_to_c_arrays.py to generate firmware headers")

if __name__ == "__main__":
    # Try Google Cloud TTS first, fallback to pyttsx3
    try:
        from google.cloud import texttospeech
        print("Using Google Cloud Text-to-Speech (high quality)")
        print("Note: Requires GOOGLE_APPLICATION_CREDENTIALS environment variable")
        print("See: https://cloud.google.com/text-to-speech/docs/quickstart-client-libraries\n")
        generate_tts_files()
    except ImportError:
        print("Google Cloud TTS not available, using pyttsx3 (offline)\n")
        generate_with_fallback()
    except Exception as e:
        print(f"Google Cloud TTS error: {e}")
        print("Falling back to pyttsx3...\n")
        generate_with_fallback()
