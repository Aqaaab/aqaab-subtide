# Aqaab Subtide Lite

Profile optimized for desktop-class machines with about 4GB RAM.

## Defaults

- Whisper model: `tiny`
- Whisper CPU threads: `2`
- Beam size: `1`
- VAD: disabled
- Speaker diarization: disabled
- Translation cache enabled
- Automatic Whisper fallback when YouTube has no usable captions

## Linux / macOS

```bash
cd backend
cp .env.lite.example .env
chmod +x run-lite.sh
./run-lite.sh
```

## Windows

Open `backend\\run-lite.cmd` after installing Python 3 and FFmpeg.

## Chrome extension

1. Start the backend so `http://127.0.0.1:5001` is available.
2. Open Chrome/Edge/Brave and go to the browser's Extensions page.
3. Enable Developer mode.
4. Choose **Load unpacked** and select the repository's `extension` folder.
5. Set the target language to Arabic (`ar`) in the extension popup.
6. Open a YouTube video and start translation.

When the video has no usable captions, the backend automatically falls back to Whisper, then the normal translation path continues.

## API key

The Lite profile still needs a translation provider unless a local compatible provider such as Ollama is configured. Put the provider key only in `backend/.env`; never commit real keys to GitHub.

## Distribution

The repository contains GitHub Actions workflows for Lite validation and packaging. A `lite-v*` tag creates a distributable Lite ZIP artifact.
