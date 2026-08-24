#!/usr/bin/env bash
# Aqaab Subtide Lite launcher for 4GB RAM desktop systems.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
export PYTHONPATH="$(dirname "$SCRIPT_DIR")${PYTHONPATH:+:$PYTHONPATH}"
export ENABLE_WHISPER="${ENABLE_WHISPER:-true}"
export ENABLE_DIARIZATION="${ENABLE_DIARIZATION:-false}"
export ENABLE_VAD="${ENABLE_VAD:-false}"
export WHISPER_MODEL="${WHISPER_MODEL:-tiny}"
export WHISPER_BEAM_SIZE="${WHISPER_BEAM_SIZE:-1}"
export WHISPER_CONDITION_ON_PREVIOUS="${WHISPER_CONDITION_ON_PREVIOUS:-false}"
export WHISPER_CPU_THREADS="${WHISPER_CPU_THREADS:-2}"
export OMP_NUM_THREADS="${OMP_NUM_THREADS:-2}"
export MKL_NUM_THREADS="${MKL_NUM_THREADS:-2}"
export TOKENIZERS_PARALLELISM="false"
export PYTHONDONTWRITEBYTECODE=1
export CACHE_DIR="${CACHE_DIR:-$SCRIPT_DIR/cache}"

command -v ffmpeg >/dev/null 2>&1 || { echo 'ERROR: ffmpeg is required.'; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo 'ERROR: Python 3 is required.'; exit 1; }

if [ ! -d "$SCRIPT_DIR/venv-lite" ]; then
  python3 -m venv "$SCRIPT_DIR/venv-lite"
fi
source "$SCRIPT_DIR/venv-lite/bin/activate"
python -m pip install --upgrade pip
python -m pip install -r "$SCRIPT_DIR/requirements-lite.txt"
mkdir -p "$CACHE_DIR"

echo "Aqaab Subtide Lite"
echo "Whisper: $WHISPER_MODEL | threads: $WHISPER_CPU_THREADS | diarization: $ENABLE_DIARIZATION"
echo "Server: http://127.0.0.1:${PORT:-5001}"
exec python app.py
