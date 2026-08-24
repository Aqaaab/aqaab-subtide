# Aqaab Subtide Android

Android 9+ application for translating audio from the YouTube app into Arabic using an on-device Whisper Tiny INT8 model plus on-device ML Kit translation.

## Runtime flow

YouTube app -> Android AudioPlaybackCapture -> 8-second bounded PCM chunks -> Whisper Tiny INT8 (2 CPU threads) -> language identification -> ML Kit translation -> Arabic overlay.

The app does not record or upload the YouTube video itself. The user explicitly starts a MediaProjection session and a visible foreground-service notification is shown while capture is active.

## Build

The repository intentionally does not store the ~100 MB Whisper model in Git. Fetch it first:

```bash
cd android
bash scripts/fetch-whisper-tiny.sh
gradle :app:assembleDebug --no-daemon
```

GitHub Actions performs the model download and APK build automatically with `.github/workflows/android-lite.yml`.

## Device requirements

- Android 10+ for playback capture (the app declares Android 9 minimum for the shell).
- About 4 GB RAM target.
- Overlay permission.
- RECORD_AUDIO runtime permission.
- User approval of the Android MediaProjection dialog.
- The source application must permit its audio to be captured by Android's playback-capture policy.

## First launch

1. Install the generated APK.
2. Open Aqaab Subtide.
3. Tap **بدء ترجمة YouTube**.
4. Grant overlay permission.
5. Grant audio permission.
6. Accept Android's screen/audio capture dialog.
7. Open the official YouTube app and play the video.
8. Keep the Aqaab foreground-service notification active while translating.

## Important limitation

This Android build handles the no-caption path by transcribing captured audio. It does not yet scrape YouTube's internal caption track from the official YouTube app. For videos with captions, the captured-audio path is still valid and is the fallback/primary path in this Android build.
