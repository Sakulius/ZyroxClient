import math
import random
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/zyrox/sounds/ui"
random.seed(7)


def tone(length, start, end, decay, volume=1.0, attack=0.003, harmonics=((1, 1.0),)):
    samples = []
    phase = 0.0
    count = int(RATE * length)
    for i in range(count):
        t = i / RATE
        freq = start * (end / start) ** (i / count)
        phase += 2 * math.pi * freq / RATE
        envelope = min(1.0, t / attack) * math.exp(-t / decay)
        value = sum(weight * math.sin(phase * n) for n, weight in harmonics)
        samples.append(value * envelope * volume)
    return samples


def noise(length, cutoff_start, cutoff_end, shape, volume=1.0):
    samples = []
    low = 0.0
    count = int(RATE * length)
    for i in range(count):
        progress = i / count
        cutoff = cutoff_start + (cutoff_end - cutoff_start) * progress
        k = 1 - math.exp(-2 * math.pi * cutoff / RATE)
        low += (random.uniform(-1, 1) - low) * k
        samples.append(low * shape(progress) * volume)
    return samples


def mix(*parts):
    length = max(len(part) for offset, part in parts) + max(int(offset * RATE) for offset, part in parts)
    out = [0.0] * length
    for offset, part in parts:
        start = int(offset * RATE)
        for i, value in enumerate(part):
            out[start + i] += value
    return out


def write(name, samples, peak=0.7):
    top = max(abs(value) for value in samples) or 1.0
    fade = int(RATE * 0.004)
    for i in range(min(fade, len(samples))):
        samples[-1 - i] *= i / fade
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as folder:
        temp = Path(folder) / "sound.wav"
        with wave.open(str(temp), "wb") as file:
            file.setnchannels(1)
            file.setsampwidth(2)
            file.setframerate(RATE)
            file.writeframes(b"".join(struct.pack("<h", int(value / top * peak * 32767)) for value in samples))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(temp), "-c:a", "libvorbis", "-q:a", "6", str(OUT / f"{name}.ogg")], check=True)
    print(name)


bell = ((1, 1.0), (2, 0.25), (3, 0.08))
write("hover", tone(0.05, 2600, 2400, 0.012, harmonics=((1, 1.0),)), 0.35)
write("click", mix((0, tone(0.09, 1150, 720, 0.022, harmonics=bell)), (0, noise(0.012, 6000, 2000, lambda p: 1 - p, 0.4))), 0.6)
write("toggle_on", mix((0, tone(0.08, 880, 880, 0.03, harmonics=bell)), (0.045, tone(0.14, 1320, 1320, 0.05, harmonics=bell))), 0.55)
write("toggle_off", mix((0, tone(0.08, 1100, 1100, 0.03, harmonics=bell)), (0.045, tone(0.14, 740, 740, 0.05, harmonics=bell))), 0.5)
write("open", mix((0, noise(0.24, 400, 3200, lambda p: math.sin(math.pi * p) ** 2, 1.0)), (0.12, tone(0.3, 1046, 1046, 0.09, harmonics=bell)), (0.16, tone(0.3, 1568, 1568, 0.08, 0.5, harmonics=bell))), 0.5)
write("close", mix((0, noise(0.18, 2600, 300, lambda p: math.sin(math.pi * p) ** 2, 1.0)), (0.06, tone(0.22, 784, 784, 0.07, 0.6, harmonics=bell))), 0.45)
write("slide", mix((0, noise(0.07, 1500, 4000, lambda p: math.sin(math.pi * p), 0.8)), (0.01, tone(0.06, 1800, 1500, 0.018, 0.5))), 0.4)
write("tab", tone(0.07, 1700, 1500, 0.02, harmonics=bell), 0.45)
write("locked", mix((0, tone(0.08, 260, 220, 0.03, harmonics=((1, 1.0), (2, 0.4)))), (0.09, tone(0.1, 220, 190, 0.035, harmonics=((1, 1.0), (2, 0.4))))), 0.6)
write("type", mix((0, noise(0.02, 5000, 1500, lambda p: 1 - p, 1.0)), (0, tone(0.03, 3000, 2600, 0.008, 0.3))), 0.25)
write("equip", mix((0, tone(0.12, 988, 988, 0.04, harmonics=bell)), (0.06, tone(0.16, 1318, 1318, 0.05, harmonics=bell)), (0.12, tone(0.4, 1976, 1976, 0.12, 0.7, harmonics=bell))), 0.5)
