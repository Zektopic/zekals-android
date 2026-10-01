# Third-party notices

The application's MIT license does not relicense dependencies, voices or models.
Preserve notices and review exact artifact licenses before redistribution.

- Android SDK/NDK and Gradle are development tools with their own terms.
- MediaPipe Tasks code is Apache-2.0; face-landmarker weights have their upstream
  model distribution terms. The reviewed artifact URL/checksum is in the downloader.
- ONNX Runtime is MIT licensed; bundled provider dependencies retain their notices.
- Android voice engines and downloaded voices are installed separately and retain
  vendor/model terms. This app does not provide rights to redistribute them.
- Language assets originate in the companion desktop project and remain subject
  to native-speaker review. No personal voice or private phrase dataset is bundled.

References: [MediaPipe Face Landmarker Android guide](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android),
[ONNX Runtime Android](https://onnxruntime.ai/docs/tutorials/mobile/deploy-android.html),
[Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).
