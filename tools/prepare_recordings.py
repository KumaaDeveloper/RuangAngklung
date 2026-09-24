"""Rebuild the real angklung WAV assets from the supplied Parking Sun MP3 files.

Requires ffmpeg on PATH; uses only the Python standard library. The MP3
filenames in the supplied ZIP lost their '#' characters, so the Freesound
sound IDs below identify the actual pitch (see ATTRIBUTION.md).
"""

from array import array
from pathlib import Path
import math
import subprocess
import sys
import wave

assert sys.byteorder == "little", "This script expects a little-endian machine"
RATE = 22050
SOURCE = Path(__file__).resolve().parent / "recordings"
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
NOTES = (
    (74294, "g3"), (74283, "a3"), (74282, "as3"), (74285, "b3"),
    (74287, "c4"), (74289, "d4"), (74291, "e4"), (74293, "f4"),
    (74292, "fs4"), (74295, "g4"), (74284, "a4"), (74281, "as4"),
    (74286, "b4"), (74288, "c5"), (74290, "d5"),
)


def convert(sound_id, note):
    # The supplied A4 take contains several rattling strikes and sounds unlike
    # its neighbors. Transpose the clean G4 take by two semitones for La/A4.
    source_id = 74295 if note == "a4" else sound_id
    matches = list(SOURCE.glob(f"{source_id}_*.mp3"))
    if len(matches) != 1:
        raise ValueError(f"Expected one source MP3 for Freesound ID {source_id}")
    command = ["ffmpeg", "-v", "error", "-i", str(matches[0]), "-ac", "1", "-ar", str(RATE)]
    if note == "a4":
        command += ["-af", "aresample=22050,asetrate=24750,aresample=22050"]
    pcm = subprocess.run(
        command + ["-f", "f32le", "-acodec", "pcm_f32le", "-"],
        check=True, capture_output=True,
    ).stdout
    samples = array("f")
    samples.frombytes(pcm)
    if len(samples) < RATE // 4:
        raise ValueError(f"Recording too short for {note}")

    samples = samples[:int(RATE * 1.5)]
    peak = max(abs(sample) for sample in samples)
    window = samples[:min(len(samples), RATE // 2)]
    rms = math.sqrt(sum(sample * sample for sample in window) / len(window))
    if not peak or not rms:
        raise ValueError(f"Silent recording for {note}")
    gain = min(0.72 / peak, 0.17 / rms)

    fade_in = min(len(samples), int(RATE * .003))
    fade_out = min(len(samples), int(RATE * .045))
    output = array("h")
    for i, sample in enumerate(samples):
        envelope = min(1.0, i / max(1, fade_in), (len(samples) - i - 1) / max(1, fade_out))
        value = max(-1.0, min(1.0, sample * gain * envelope))
        output.append(round(value * 32767))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT / f"angklung_{note}.wav"), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(output.tobytes())


if __name__ == "__main__":
    for sound_id, note in NOTES:
        convert(sound_id, note)
    print(f"Created {len(NOTES)} real angklung WAV samples in {OUTPUT}")
