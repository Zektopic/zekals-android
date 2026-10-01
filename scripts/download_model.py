#!/usr/bin/env python3
"""Download the versioned MediaPipe face model; verify before atomic installation."""
import hashlib
from pathlib import Path
import urllib.request

URL = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
SHA256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"


def main():
    destination = Path(__file__).resolve().parents[1] / "app" / "src" / "vision" / "assets" / "face_landmarker.task"
    destination.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(URL, timeout=60) as response:
        data = response.read(16 * 1024 * 1024)
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("Model checksum mismatch; nothing installed")
    temporary = destination.with_suffix(".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)
    print(f"Installed {destination}\nMODEL_SHA256={SHA256}")


if __name__ == "__main__":
    main()
