# Aqaab Subtide Android

Android 10+ application for translating audio from the official YouTube app into Arabic using an on-device Whisper Tiny INT8 model and on-device ML Kit translation.

## Runtime flow

YouTube -> Android AudioPlaybackCapture -> bounded 8-second PCM chunks -> Whisper Tiny INT8 (2 CPU threads) -> language identification -> ML Kit translation -> Arabic overlay.

The app does not record or upload the YouTube video itself. The user explicitly starts a MediaProjection session and a visible foreground-service notification remains active while capture is running.

## Device requirements

- Android 10+ for playback capture.
- About 4 GB RAM target.
- Overlay permission.
- `RECORD_AUDIO` permission as required by Android media playback capture.
- User approval of the Android MediaProjection capture dialog.
- The source application must permit playback capture under Android's audio capture policy.

## Current capabilities

- Captures supported YouTube media audio without recording the video.
- Processes audio in bounded 8-second chunks to control RAM usage.
- Runs Whisper Tiny INT8 locally with two CPU threads.
- Detects the spoken language locally.
- Translates recognized text to Arabic on-device with ML Kit.
- Displays the Arabic result as a non-touching overlay above YouTube.
- Drops stale queued chunks instead of allowing unbounded memory growth.
- Stops and releases MediaProjection, AudioRecord, Whisper, translation clients, and overlay resources cleanly.

## Build

The repository intentionally does not store the Whisper model in Git. GitHub Actions downloads the model and builds the APK automatically.

Manual build:

```bash
cd android
bash scripts/fetch-whisper-tiny.sh
gradle :app:assembleDebug --no-daemon
```

Automated build:

`.github/workflows/android-lite.yml`

The workflow explicitly verifies AndroidX, downloads the Whisper Tiny INT8 model, builds from the current commit, and uploads `aqaab-subtide-android-lite`.

## Important limitation

This Android build does not read YouTube's private/internal caption track. It uses the supported audio-capture path, so a video can be translated even when no Arabic captions exist, provided Android and the source app permit playback capture.
