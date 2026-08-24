@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where python >nul 2>&1 || (echo ERROR: Python 3 is required.&exit /b 1)
where ffmpeg >nul 2>&1 || (echo ERROR: ffmpeg is required and must be on PATH.&exit /b 1)

set "ENABLE_WHISPER=true"
set "ENABLE_DIARIZATION=false"
set "ENABLE_VAD=false"
set "WHISPER_MODEL=tiny"
set "WHISPER_BEAM_SIZE=1"
set "WHISPER_CONDITION_ON_PREVIOUS=false"
set "WHISPER_CPU_THREADS=2"
set "OMP_NUM_THREADS=2"
set "MKL_NUM_THREADS=2"
set "OPENBLAS_NUM_THREADS=2"
set "NUMEXPR_NUM_THREADS=2"
set "TOKENIZERS_PARALLELISM=false"
set "PYTHONDONTWRITEBYTECODE=1"
set "CACHE_DIR=%CD%\cache"

if not exist "venv-lite\Scripts\python.exe" python -m venv venv-lite
call "venv-lite\Scripts\activate.bat"
python -m pip install --upgrade pip
python -m pip install -r requirements-lite.txt
if errorlevel 1 exit /b 1
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

echo Aqaab Subtide Lite
echo Whisper: %WHISPER_MODEL% ^| threads: %WHISPER_CPU_THREADS% ^| diarization: %ENABLE_DIARIZATION%
echo Server: http://127.0.0.1:5001
python app.py
endlocal
