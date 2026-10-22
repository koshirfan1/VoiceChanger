#!/usr/bin/env python3
"""Voice profile matching (WORLD vocoder).

1) Target awaaz ki recording (female, saaf, 30s-2min) se "profile" nikaalein:
       python voice_profile.py analyze female.wav profile.json
2) Apni recording ko us profile ki taraf morph karein:
       python voice_profile.py convert my_voice.wav profile.json out.wav [--formant 1.15] [--strength 1.0]

Profile mein: pitch (F0) ka average aur spread, aur awaaz ka average spectral envelope (tone).
Ye neural voice cloning nahi hai: kisi ki "pehchaan" copy nahi hoti, sirf pitch aur tone kareeb aata hai.
Sirf apni awaaz ya jin ki ijazat ho, unki recording use karein.
"""
import argparse, json
import numpy as np
import pyworld as pw
import soundfile as sf
from scipy.ndimage import uniform_filter1d

FS = 16000


def load(path):
    x, fs = sf.read(path, dtype="float64")
    if x.ndim > 1:
        x = x.mean(axis=1)
    if fs != FS:
        from scipy.signal import resample_poly
        from math import gcd
        g = gcd(fs, FS)
        x = resample_poly(x, FS // g, fs // g)
    return x / (np.max(np.abs(x)) + 1e-9) * 0.9


def world(x):
    f0, t = pw.harvest(x, FS, f0_floor=70, f0_ceil=500)
    f0 = pw.stonemask(x, f0, t, FS)
    sp = pw.cheaptrick(x, f0, t, FS)
    ap = pw.d4c(x, f0, t, FS)
    return f0, t, sp, ap


def mean_log_env(sp, f0):
    v = f0 > 0
    return np.log(sp[v] + 1e-12).mean(axis=0)


def analyze(args):
    x = load(args.input)
    f0, t, sp, ap = world(x)
    v = f0 > 0
    if v.sum() < 50:
        raise SystemExit("Recording mein awaaz (voiced frames) bohat kam hai.")
    lf0 = np.log(f0[v])
    prof = {"fs": FS, "log_f0_mean": float(lf0.mean()), "log_f0_std": float(lf0.std()),
            "mean_f0_hz": float(np.exp(lf0.mean())),
            "mean_log_env": mean_log_env(sp, f0).tolist()}
    json.dump(prof, open(args.output, "w"))
    print(f"Profile saved. Target average pitch = {prof['mean_f0_hz']:.0f} Hz")


def warp(sp, alpha):
    """Formants ko alpha guna upar (alpha>1) ya neeche shift karo."""
    n = sp.shape[1]
    src = np.arange(n) / alpha                # new[k] = old[k/alpha]
    src = np.clip(src, 0, n - 1)
    i0 = np.floor(src).astype(int); i1 = np.minimum(i0 + 1, n - 1); fr = src - i0
    return sp[:, i0] * (1 - fr) + sp[:, i1] * fr


def convert(args):
    prof = json.load(open(args.profile))
    x = load(args.input)
    f0, t, sp, ap = world(x)
    v = f0 > 0
    lf0 = np.log(f0[v])
    mu_s, sd_s = lf0.mean(), lf0.std() + 1e-6
    mu_t, sd_t = prof["log_f0_mean"], prof["log_f0_std"]
    s = args.strength
    new = f0.copy()
    # pitch: average target par, spread mild (poori nakal se robot jaisa lagta hai)
    spread = 1 + s * (min(sd_t / sd_s, 1.5) - 1)
    new[v] = np.exp(mu_s + s * (mu_t - mu_s) + (lf0 - mu_s) * spread)
    sp2 = warp(sp, 1 + s * (args.formant - 1))
    # tone: target ka average envelope match karo (smooth + clamp, warna kharab awaaz aati hai)
    diff = np.array(prof["mean_log_env"]) - mean_log_env(sp2, f0)
    diff = np.clip(uniform_filter1d(diff, 15), -1.5, 1.5) * s * 0.7
    sp2 = sp2 * np.exp(diff)[None, :]
    y = pw.synthesize(new, np.ascontiguousarray(sp2), ap, FS)
    y = y / (np.max(np.abs(y)) + 1e-9) * 0.9
    sf.write(args.output, y, FS)
    print(f"Done. Pitch {np.exp(mu_s):.0f} Hz -> {np.exp(np.log(new[v]).mean()):.0f} Hz. Saved {args.output}")


if __name__ == "__main__":
    ap_ = argparse.ArgumentParser()
    sub = ap_.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("analyze"); a.add_argument("input"); a.add_argument("output"); a.set_defaults(fn=analyze)
    c = sub.add_parser("convert")
    c.add_argument("input"); c.add_argument("profile"); c.add_argument("output")
    c.add_argument("--formant", type=float, default=1.15, help="formant shift (male->female ~1.12-1.2)")
    c.add_argument("--strength", type=float, default=1.0, help="0..1 kitna target ki taraf jaana hai")
    c.set_defaults(fn=convert)
    args = ap_.parse_args(); args.fn(args)
