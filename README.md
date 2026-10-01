# zekALS Android

**Native, local communication with accessible controls and offline speech.**

zekALS Android is the mobile companion to
[zekALS](https://github.com/Zektopic/zekals-containerized). It provides a message
editor, phrase boards, multilingual character pages, and speech using installed
Android voices. Touch, keyboards, accessibility services and single switches work
independently of camera tracking.

## Choose the appropriate build

| Build | Intended use | Included components |
| --- | --- | --- |
| **Lite** | Older phones, low memory devices, touch/switch communication | Native UI, offline Android TTS, C++ dwell/filter core; no camera or ML libraries |
| **Vision** | Optional experimental webcam gaze input | Lite functionality plus Camera2 capture, C++ image conversion, MediaPipe and optional ONNX adapters |

Lite targets ARM64, ARMv7 and x86_64. Vision targets ARM64 and x86_64. Android 8.0
(API 26) or newer is required. CPU is the default inference choice. GPU and legacy
NNAPI acceleration are optional and depend on the model, runtime and device;
NNAPI selection is not proof of NPU execution.

## Features

- Native controls with large touch targets, font scaling, high contrast, visible
  selection, pause, undo, fixed scrolling controls and access-mode settings.
- English, French, Simplified Chinese, Italian and Sinhala packs; Greek retained.
  Language assets share the desktop format and can be extended without Java edits.
- Installed offline voices, language matching, quality-based voice ordering,
  speed control and immediate stop. No cloud API key or bundled voice is required.
- A C++/JNI core with validated buffers, strided YUV conversion, rotation/mirroring,
  time-based filtering and selection that fires once per entry.
- Vision captures the latest frame, caps inference at 10 Hz, reduces it under
  thermal pressure, cancels stale input and calibrates before gaze selection.
- No application internet permission, background camera service, conversation
  logging or message backup. Models are a deliberate build-time installation.

## Build

Install JDK 17 and the Android SDK, including API 35, Build Tools 35.0.0,
NDK 28.0.13004108 and CMake 3.22.1. Set `ANDROID_HOME` to the SDK directory.

```bash
./gradlew :app:assembleLiteDebug :app:lintLiteDebug
```

For Vision, first download the pinned model and then build:

```bash
python3 scripts/download_model.py
./gradlew :app:assembleVisionDebug :app:lintVisionDebug
```

Install the chosen APK from `app/build/outputs/apk/`. Debug APKs are development
artifacts. A production release requires your signing configuration and physical
device acceptance testing; signing keys are never stored in this repository.

## Documentation

- [Installation and operation](docs/getting-started.md)
- [Accessibility and speech](docs/accessibility.md)
- [Hardware, models and performance](docs/hardware.md)
- [Architecture and lifecycle](docs/architecture.md)
- [Language packs](docs/languages.md)
- [Native C++ interface](docs/native-core.md)
- [Security and privacy](docs/security.md)
- [Development and validation](docs/development.md)
- [Validation record and limitations](docs/validation.md)
- [Third-party licenses](docs/third-party.md)

## Status

The communication foundation has produced a debug APK for all three baseline
architectures and passed Android lint. The expanded Lite/Vision source is staged
for review; the final Vision build and physical device testing are still required.
Webcam gaze is experimental and needs individual calibration and user/caregiver
trials. No universal NPU support, medical efficacy or measured end-to-end speedup
is claimed. A natural Sinhala voice must be supplied by a compatible installed
engine; none is bundled.

Application code is [MIT licensed](LICENSE). Runtime libraries, camera models and
installed voices retain their own licenses.
