# Floating Teleprompter (Prompter)

An Android app that floats a scrolling script over your camera app, so you can record a video in one take, with your eyes next to the lens and the full quality of your phone's own camera.

Prompter never records video itself. Samsung Camera (or Instagram, TikTok, CapCut, or any camera app) does the recording; Prompter just shows your script on top, in a dark glass panel right under the front camera.

## Features
- **Floating script over any camera app.** Taps outside the panel go straight to the camera, and the panel never appears in the recorded video.
- **Voice mode:** starts when you start talking, follows your words, waits when you pause, ignores ad-libs, and switches to auto-scroll if it can't hear you. It uses Android's built-in speech recognizer, on-device when available.
- **Auto mode:** scrolls at a set speed (60–250 words per minute). `//` in a script adds a short pause.
- **Overlay controls:**
  - tap to pause or resume;
  - double-tap for the control bar: line back/forward, speed, text size, Voice/Auto, restart, lock, minimize;
  - swipe to scroll by hand;
  - long-press to lock, so touches pass through to the camera;
  - drag to move and resize, with the position remembered for portrait and landscape.
- **Volume keys and Bluetooth clickers** can control the script while the camera is open, through an optional Accessibility service.
- **Script library and editor:** search, duplicate, `//` pause markers, live word count and read time, and import from Android's Share menu.
- **Polish with Claude (optional):** rewrites a script for speaking, with presets (tighten, hook, 30/60/90 seconds) and a side-by-side compare. It uses your own Anthropic API key, stored encrypted with Android Keystore. This is the only feature that uses the internet, and only when you tap it.
- **Backup and restore** of all scripts as a JSON file.

## Requirements
- Android 12 (API 31) or newer. Developed and tested on a Samsung Galaxy S24 Ultra.
- Permissions: display over other apps, microphone (Voice mode), notifications, and battery set to "Unrestricted" so the overlay isn't closed mid-take. The app walks you through them on first launch.

## Building
```bash
cd android
./gradlew assembleRelease   # minified APK, about 6 MB
./gradlew testDebugUnitTest # unit tests
```
You need JDK 17+ and the Android SDK (compileSdk 37). Point to the SDK in `android/local.properties` (`sdk.dir=...`). Local release builds are signed with your debug key.

Optional: `eas.json` and `package.json` let [EAS Build](https://docs.expo.dev/build/introduction/) build and sign the APK in the cloud. The app itself is plain native Kotlin; there's no React Native or Expo runtime.

## Tech
Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, the official Anthropic Java SDK, and R8.

## Privacy
Scripts and settings stay on your phone. There are no accounts, analytics or ads. Voice mode uses the phone's speech recognizer. Nothing is sent anywhere except the script you choose to polish, which goes to Anthropic with your own key.

## Security
See [SECURITY.md](SECURITY.md) for what the app can access and why, how your data and API key are protected, and how to report a vulnerability privately.

## License
[MIT](LICENSE) © 2026 Muhammad Bazal Iqbal. The bundled fonts (JetBrains Mono, Inter) are under the SIL Open Font License; see `android/app/src/main/assets/licenses/`.
