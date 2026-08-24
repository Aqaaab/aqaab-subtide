#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/sherpa-onnx-whisper-tiny"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
mkdir -p "$ROOT/app/src/main/assets"
rm -rf "$ASSETS" "$ROOT/.whisper-tiny.tar.bz2"
curl -L --fail --retry 3 "$URL" -o "$ROOT/.whisper-tiny.tar.bz2"
tar -xjf "$ROOT/.whisper-tiny.tar.bz2" -C "$ROOT/app/src/main/assets"
rm -f "$ROOT/.whisper-tiny.tar.bz2"
# Keep only the INT8 multilingual runtime files.
find "$ASSETS" -type f ! \( -name 'tiny-encoder.int8.onnx' -o -name 'tiny-decoder.int8.onnx' -o -name 'tiny-tokens.txt' \) -delete
find "$ASSETS" -type d -empty -delete
printf 'Whisper Tiny INT8 model ready in %s\n' "$ASSETS"
