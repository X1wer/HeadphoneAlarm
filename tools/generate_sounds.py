"""Generates the bundled alarm tones of FülesÉbresztő.

All four tones are synthesised from scratch by this script (sine/triangle waves with simple
envelopes), so they carry no third-party rights. They are released together with the app
under the project's license.

Usage:  python tools/generate_sounds.py <output_dir>
Writes 16-bit mono WAV files; the build ships them as OGG Vorbis converted with
    ffmpeg -i in.wav -c:a libvorbis -q:a 4 out.ogg
"""
import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22050


def envelope(i, n, attack=0.01, release=0.08):
    t = i / RATE
    total = n / RATE
    if t < attack:
        return t / attack
    if t > total - release:
        return max(0.0, (total - t) / release)
    return 1.0


def tone(freq, seconds, volume=0.6, harmonics=(1.0,), attack=0.01, release=0.08, decay=0.0):
    n = int(seconds * RATE)
    out = []
    for i in range(n):
        t = i / RATE
        s = sum(a * math.sin(2 * math.pi * freq * (k + 1) * t) for k, a in enumerate(harmonics))
        s /= sum(harmonics)
        d = math.exp(-decay * t) if decay else 1.0
        out.append(s * volume * envelope(i, n, attack, release) * d)
    return out


def silence(seconds):
    return [0.0] * int(seconds * RATE)


def write(path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        frames = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32000)) for s in samples)
        w.writeframes(frames)


def classic():
    # Two-tone "beep-beep" pattern, repeated, like a traditional digital alarm clock.
    beep = tone(1760, 0.09, 0.55, harmonics=(1.0, 0.25)) + silence(0.06)
    burst = beep * 4 + silence(0.45)
    return burst * 3


def soft():
    # Gentle rising arpeggio (C major 7) with soft attack and long decay.
    notes = [523.25, 659.25, 783.99, 987.77, 1046.5, 987.77, 783.99, 659.25]
    out = []
    for f in notes:
        out += tone(f, 0.42, 0.45, harmonics=(1.0, 0.3, 0.1), attack=0.04, release=0.2, decay=2.5)
    return out + silence(0.6)


def beep():
    # Plain single-frequency beeps.
    return (tone(1000, 0.25, 0.5, attack=0.005, release=0.02) + silence(0.35)) * 4


def chime():
    # Bell-like strikes with a fast exponential decay, descending.
    out = []
    for f in (880.0, 739.99, 659.25, 587.33):
        out += tone(f, 0.9, 0.5, harmonics=(1.0, 0.6, 0.35, 0.2), attack=0.003, release=0.3, decay=3.2)
    return out + silence(0.4)


def main():
    out_dir = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, fn in (("classic_alarm", classic), ("soft_melody", soft), ("simple_beep", beep), ("morning_chime", chime)):
        write(out_dir / f"{name}.wav", fn())
        print("wrote", name)


if __name__ == "__main__":
    main()
