import os
import threading
import queue
import time
import logging
import numpy as np
from backend.services.whisper_service import get_whisper_model
from backend.services.translation_service import await_translate_subtitles

logger = logging.getLogger('subtide')
_last_chunk_time = {}


class LiveWhisperService:
    """Low-latency local Whisper service with bounded memory usage."""

    def __init__(self, sid, target_lang, socketio):
        self.sid = sid
        self.target_lang = target_lang
        self.socketio = socketio
        self.audio_queue = queue.Queue(maxsize=256)
        self.running = False
        self.thread = None
        self.sample_rate = 16000
        self.audio_buffer = np.array([], dtype=np.float32)

        # Keep CPU-side libraries conservative on low-memory systems.
        os.environ.setdefault('OMP_NUM_THREADS', os.getenv('WHISPER_CPU_THREADS', '2'))
        os.environ.setdefault('MKL_NUM_THREADS', os.getenv('WHISPER_CPU_THREADS', '2'))
        os.environ.setdefault('OPENBLAS_NUM_THREADS', os.getenv('WHISPER_CPU_THREADS', '2'))
        os.environ.setdefault('TOKENIZERS_PARALLELISM', 'false')

        model_path = get_whisper_model()
        self.model = None
        self.backend = 'unknown'
        if isinstance(model_path, str):
            try:
                import mlx_whisper
                import mlx.core as mx
                logger.info(f"[LIVE] Loading MLX model from {model_path}...")
                self.model = mlx_whisper.load_models.load_model(model_path, dtype=mx.float16)
                self.backend = 'mlx'
                logger.info('[LIVE] MLX model loaded.')
            except Exception as exc:
                logger.exception(f"[LIVE] Failed to load MLX model: {exc}")
        else:
            logger.error('[LIVE] Invalid model path for MLX usage')

    def start(self):
        if self.model is None:
            raise RuntimeError('Live Whisper model is unavailable')
        self.running = True
        self.thread = threading.Thread(target=self._process_loop, daemon=True)
        self.thread.start()
        logger.info(f"[LIVE] Service started for {self.sid}")

    def add_audio(self, pcm_bytes):
        try:
            audio_chunk = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
            if audio_chunk.size == 0:
                return
            rms = float(np.sqrt(np.mean(audio_chunk ** 2)))
            now = time.time()
            last_time = _last_chunk_time.get(self.sid, now)
            _last_chunk_time[self.sid] = now
            if rms > 0.01:
                logger.debug(
                    f"[LIVE] Audio chunk: {len(pcm_bytes)} bytes, "
                    f"volume={rms * 100:.1f}%, gap={now - last_time:.2f}s"
                )
            try:
                self.audio_queue.put_nowait(audio_chunk)
            except queue.Full:
                try:
                    self.audio_queue.get_nowait()
                    self.audio_queue.put_nowait(audio_chunk)
                    logger.warning('[LIVE] Audio queue full, dropping oldest chunk')
                except queue.Empty:
                    pass
        except Exception as exc:
            logger.error(f"[LIVE] Error decoding PCM chunk: {exc}")

    def stop(self):
        self.running = False
        if self.thread:
            self.thread.join(timeout=2)
        _last_chunk_time.pop(self.sid, None)
        logger.info(f"[LIVE] Service stopped for {self.sid}")

    def _process_loop(self):
        while self.running:
            try:
                while True:
                    try:
                        chunk = self.audio_queue.get_nowait()
                    except queue.Empty:
                        break
                    self.audio_buffer = np.concatenate((self.audio_buffer, chunk))

                min_samples = int(self.sample_rate * 1.5)
                if len(self.audio_buffer) >= min_samples:
                    self._transcribe_and_translate()
                    keep_samples = int(self.sample_rate * 0.5)
                    self.audio_buffer = self.audio_buffer[-keep_samples:]

                time.sleep(0.1)
            except Exception as exc:
                logger.exception(f"[LIVE] Error in process loop: {exc}")
                time.sleep(1)

    def _transcribe_and_translate(self):
        if not len(self.audio_buffer) or self.backend != 'mlx':
            return

        try:
            buffer_duration = len(self.audio_buffer) / self.sample_rate
            logger.debug(f"[LIVE] Transcribing {buffer_duration:.1f}s of audio...")
            transcribe_start = time.time()

            import mlx_whisper
            import mlx_whisper.audio
            import mlx_whisper.decoding
            import mlx.core as mx

            audio = mx.array(self.audio_buffer.astype(np.float32))
            audio = mlx_whisper.audio.pad_or_trim(audio)
            mel = mlx_whisper.audio.log_mel_spectrogram(audio).astype(mx.float16)
            result = mlx_whisper.decoding.decode(self.model, mel, temperature=0.0)
            res = result[0] if isinstance(result, list) else result
            transcribed_text = getattr(res, 'text', '').strip()
            language = getattr(res, 'language', None) or 'en'

            duration = time.time() - transcribe_start
            if not transcribed_text:
                logger.debug(f"[LIVE] Transcription complete in {duration:.2f}s (no speech)")
                return

            logger.info(f"[LIVE] Transcribed in {duration:.2f}s: {transcribed_text[:80]}...")
            self.socketio.emit(
                'live_result',
                {
                    'text': transcribed_text,
                    'translatedText': None,
                    'language': language,
                    'status': 'transcribing',
                },
                room=self.sid,
                namespace='/live',
            )

            if self.target_lang != language:
                self.socketio.start_background_task(
                    self._translate_task,
                    transcribed_text,
                    language,
                    self.target_lang,
                )
            else:
                self.socketio.emit(
                    'live_result',
                    {
                        'text': transcribed_text,
                        'translatedText': transcribed_text,
                        'language': language,
                        'status': 'final',
                    },
                    room=self.sid,
                    namespace='/live',
                )
        except Exception as exc:
            logger.error(f"[LIVE] Transcription failed: {exc}")

    def _translate_task(self, text, source_lang, target_lang):
        try:
            translated_subs = await_translate_subtitles(
                [{'text': text, 'start': 0, 'end': 1}],
                target_lang,
            )
            translated_text = text
            if translated_subs:
                translated_text = translated_subs[0].get('translatedText', text)

            self.socketio.emit(
                'live_result',
                {
                    'text': text,
                    'translatedText': translated_text,
                    'language': source_lang,
                    'status': 'final',
                },
                room=self.sid,
                namespace='/live',
            )
        except Exception as exc:
            logger.error(f"[LIVE] Translation task failed: {exc}")
