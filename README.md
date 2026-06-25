# PixelSnap

PixelSnap is a minimal retro pixel camera for taking photos and recording short videos into the system gallery.

## Current Features

- One main camera screen with a live preview.
- Tap the live view to take a photo.
- Long-press the live view to start video recording, then tap to stop.
- Video recording with microphone audio.
- Photos and videos default to a 16:9 capture ratio.
- Photos saved to `DCIM/Camera` with a bottom-right text-only PixelSnap watermark.
- Videos saved to `DCIM/Camera`.
- Captured photos preview briefly inside the viewfinder and can be dismissed by tap.
- Captured videos preview full-screen with rotation-aware sizing and a centered play control; tap the control to play or tap elsewhere to return to live camera.
- Media previews size the framed-print border from the original photo/video aspect ratio, with the border outside the media pixels and warm-white space beyond it.
- The screen stays awake while PixelSnap is active.
- One stable full-screen camera surface for portrait and landscape orientation changes.
- Restrained capture guides: four thick corner marks and one center square, hidden during media preview.
- Recording feedback uses a blinking red dot and elapsed timer.
- Photo capture uses a brief full-screen flash.
- Lightweight custom launcher icon.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- CameraX 1.6.1
- Android MediaStore

## Build Commands

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

`assembleDebug` writes the default APK and the versioned debug APK to the same directory:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/debug/PixelSnap-0.1.1-debug.apk
```

## Installation

```powershell
adb install -r app/build/outputs/apk/debug/PixelSnap-0.1.1-debug.apk
```

## Status

MVP prototype.

Developed by CODEX & XUE.
