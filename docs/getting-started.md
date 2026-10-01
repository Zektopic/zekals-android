# Installation and operation

## Development setup

Use JDK 17 and Android SDK packages `platforms;android-35`, `build-tools;35.0.0`,
`ndk;28.2.13676358` and `cmake;3.22.1`. The Gradle wrapper pins 8.11.1 and verifies
the distribution SHA-256. Android Gradle Plugin is pinned to 8.9.2. Open the root
in Android Studio or set `JAVA_HOME` and `ANDROID_HOME` for command-line builds.
Windows uses `gradlew.bat`; Python scripts are platform-independent.

Choose Lite for communication without camera libraries. Run
`./gradlew :app:assembleLiteDebug :app:lintLiteDebug`. The APK is under
`app/build/outputs/apk/lite/debug/`. Install with `adb install -r <apk>` after
explicitly authorizing the development computer on the Android device.

For Vision, run `python3 scripts/download_model.py`, then
`./gradlew :app:assembleVisionDebug :app:lintVisionDebug`. The downloader verifies
the pinned face model before atomic installation in `app/src/vision/assets/`.
Vision builds fail if the model is missing or its SHA-256 differs, rather than
producing an APK that cannot track. Do not rename arbitrary models to this
filename. Model weights are not committed.
The model is not included in Lite assets or downloaded while someone communicates.

## First use

1. Set the device media volume to a comfortable level.
2. Choose the language using the language button. Enter a short phrase and Speak.
3. If no voice is available, install a matching offline voice through Android's
   text-to-speech settings. Return to the app and choose Voice in Settings.
4. Set font size, contrast, dwell time and scanning interval with the user.
5. Test Clear and Undo, Pause and Stop before enabling automatic input.

The editor uses the installed Android keyboard/IME. The on-screen Chinese board
contains common characters, not a complete Pinyin dictionary. Sinhala has separate
letters/combining marks and a join control; validate typing and deletion with a
native speaker. Phrase buttons insert text without unexpectedly speaking it.

## Optional camera

Use Vision. In Settings, choose an inference request (CPU first), then Camera:
start/stop. The app requests camera permission only on this explicit action. No
microphone or internet permission is requested. A denied permission leaves the
other controls available; once Android stops showing the prompt, the camera button
opens App info so the permission can be granted there. While the camera runs, a
small preview next to the title shows what the model sees, with dots on the irises,
eye corners and nose; nothing is recorded or saved. Once a face is found the camera
zooms in on it using the sensor's own crop (up to 3x), so the eyes are captured at
full sensor detail, and it only re-centres when the face drifts, keeping the face
frame steady. Exposure meters on the face where the camera supports it. If no face
is found, the app tries the other image rotations in case the device reports its
camera angle wrongly. The camera status line shows Camera
off, Starting, No face detected or Eye tracking on, with the inference provider in
use.

Sit 40–60 cm from the screen and press Calibrate. A large preview appears first;
centre your face until the dots sit on your eyes, and calibration starts once your
face has been found for a second. Then look at each of nine dots while its ring
fills. Time only counts while your face is tracked, so looking away pauses the dot
rather than failing. Calibration learns from where the irises sit in the eyes and
from small head turns together, so either can move the pointer. The result reports the typical error as a share of the
screen; calibrations worse than 25% are rejected. Try the large phrase buttons
first.
In Eye tracking a pointer follows the gaze. The calibration is kept across app
restarts until the camera is stopped from Settings; recalibrate after repositioning
the device or turning it the other way up.

Pause/Stop and alternate input remain available. Camera and speech stop when the
activity leaves the foreground; on return the camera restarts on its own and
selection stays paused until the user chooses Resume, which can be dwelled on with
the eyes. The screen stays on while the camera runs. NNAPI requires the optional
custom model described in the hardware guide.
