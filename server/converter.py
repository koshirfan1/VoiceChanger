"""Voice conversion backends.

Set environment variables to use ElevenLabs Voice Changer:
    ELEVENLABS_API_KEY   (elevenlabs.io -> Developers -> API Keys)
    ELEVENLABS_VOICE_ID  (target voice id from your Voice Library)
Without them the server just echoes your own voice back (connection test).

Input/output: mono float32 numpy array in [-1, 1] at 16000 Hz.
"""
import os
import numpy as np
import requests


class Converter:
    """Passthrough: awaaz wahi wapas (sirf test ke liye)."""
    def __init__(self, sample_rate: int):
        self.sample_rate = sample_rate

    def convert(self, block: np.ndarray) -> np.ndarray:
        return block


class ElevenLabsConverter(Converter):
    URL = "https://api.elevenlabs.io/v1/voice-changer/{voice_id}"
    MODEL_ID = os.getenv("ELEVENLABS_MODEL_ID", "eleven_multilingual_sts_v2")

    def __init__(self, sample_rate: int, api_key: str, voice_id: str):
        super().__init__(sample_rate)
        if sample_rate != 16000:
            raise ValueError("ElevenLabs raw PCM input needs 16000 Hz")
        self.api_key, self.voice_id = api_key, voice_id

    def convert(self, block: np.ndarray) -> np.ndarray:
        pcm = (np.clip(block, -1, 1) * 32767).astype("<i2").tobytes()
        try:
            r = requests.post(
                self.URL.format(voice_id=self.voice_id),
                params={"output_format": "pcm_16000"},
                headers={"xi-api-key": self.api_key},
                files={"audio": ("audio.pcm", pcm, "application/octet-stream")},
                data={"model_id": self.MODEL_ID, "file_format": "pcm_s16le_16"},
                timeout=30,
            )
            r.raise_for_status()
            return np.frombuffer(r.content, dtype="<i2").astype(np.float32) / 32768.0
        except Exception as e:  # asli awaaz leak na ho, isliye khamoshi bhejte hain
            print("ElevenLabs error:", e)
            return np.zeros_like(block)


def make_converter(sample_rate: int) -> Converter:
    key, voice = os.getenv("ELEVENLABS_API_KEY"), os.getenv("ELEVENLABS_VOICE_ID")
    if key and voice:
        print("Using ElevenLabs voice changer")
        return ElevenLabsConverter(sample_rate, key, voice)
    print("ELEVENLABS_API_KEY / ELEVENLABS_VOICE_ID not set -> passthrough mode")
    return Converter(sample_rate)
