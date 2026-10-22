"""WebSocket voice-conversion server.  Run:  python server.py
Phone streams 16-bit mono 16 kHz PCM. Server cuts it into phrases (at pauses),
converts each phrase, and sends the converted PCM back."""
import asyncio, json
import numpy as np
import websockets
from converter import make_converter

MIN_MS = 1000      # phrase kam az kam itna lamba
MAX_MS = 6000      # itni der mein zaroor bhej do
SIL_MS = 250       # itni khamoshi = phrase khatam
SIL_RMS = 0.01     # khamoshi ki hadd (mic ke hisab se adjust karein)


def rms(x):
    return float(np.sqrt(np.mean(x ** 2))) if len(x) else 0.0


async def handler(ws):
    sr = 16000
    conv = None
    buf = np.zeros(0, dtype=np.float32)
    async for msg in ws:
        if isinstance(msg, str):
            sr = json.loads(msg).get("sample_rate", 16000)
            conv = make_converter(sr)
            print("client connected, sr =", sr)
            continue
        if conv is None:
            conv = make_converter(sr)
        buf = np.concatenate([buf, np.frombuffer(msg, dtype="<i2").astype(np.float32) / 32768.0])
        n_min, n_max, n_sil = sr * MIN_MS // 1000, sr * MAX_MS // 1000, sr * SIL_MS // 1000
        if len(buf) < n_min:
            continue
        paused = rms(buf[-n_sil:]) < SIL_RMS
        if not (paused or len(buf) >= n_max):
            continue
        block, buf = buf, np.zeros(0, dtype=np.float32)
        if rms(block) < SIL_RMS:      # sirf khamoshi: API par paisa kharch na karein
            continue
        out = await asyncio.to_thread(conv.convert, block)
        await ws.send((np.clip(out, -1, 1) * 32767).astype("<i2").tobytes())


async def main():
    async with websockets.serve(handler, "0.0.0.0", 8765, max_size=None):
        print("Server ready on port 8765")
        await asyncio.Future()

asyncio.run(main())
