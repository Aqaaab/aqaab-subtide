"""
Whisper Backend - Faster Whisper (CTranslate2)
Optimized for CUDA, with conservative CPU settings for Lite mode.
"""

import os
import logging
from typing import List, Dict, Any, Optional, Callable

from services.whisper_backend_base import WhisperBackend, TranscriptionSegment
from whisper_threads import configure_whisper_threads

logger = logging.getLogger('subtide')


class FasterWhisperBackend(WhisperBackend):
    def __init__(self, model_size: str = None):
        self.model_size = model_size or os.getenv('WHISPER_MODEL', 'tiny')
        self.model = None
        self.device = None
        self.compute_type = None
        self._detect_device()

    def _detect_device(self):
        try:
            import torch
            if torch.cuda.is_available():
                self.device = "cuda"
                self.compute_type = "float16"
            else:
                self.device = "cpu"
                self.compute_type = "int8"
            logger.info(f"FasterWhisper: Using {self.device} with {self.compute_type}")
        except ImportError:
            self.device = "cpu"
            self.compute_type = "int8"

    def _load_model(self):
        if self.model is not None:
            return
        try:
            threads = configure_whisper_threads()
            from faster_whisper import WhisperModel
            cpu_threads = threads if self.device == 'cpu' else max(1, int(os.getenv('WHISPER_CPU_THREADS', '2')))
            logger.info(f"Loading faster-whisper model: {self.model_size} (cpu_threads={cpu_threads})")
            self.model = WhisperModel(
                self.model_size,
                device=self.device,
                compute_type=self.compute_type,
                download_root=os.getenv('WHISPER_CACHE_DIR', None),
                cpu_threads=cpu_threads,
            )
        except ImportError as e:
            raise RuntimeError("faster-whisper not installed. Install with: pip install faster-whisper") from e

    def transcribe(self, audio_path: str, model_size: str = None, language: Optional[str] = None,
                   initial_prompt: Optional[str] = None,
                   progress_callback: Optional[Callable[[str, str, int], None]] = None,
                   segment_callback: Optional[Callable[[TranscriptionSegment], None]] = None) -> List[Dict[str, Any]]:
        if model_size and model_size != self.model_size:
            self.model_size = model_size
            self.model = None
        self._load_model()
        if progress_callback:
            progress_callback('whisper', 'Starting transcription...', 10)

        beam_size = int(os.getenv('WHISPER_BEAM_SIZE', '1'))
        vad_enabled = os.getenv('ENABLE_VAD', 'false').lower() == 'true'
        options = {'beam_size': max(1, beam_size), 'word_timestamps': True}
        if vad_enabled:
            options.update({'vad_filter': True, 'vad_parameters': {'min_silence_duration_ms': 500, 'speech_pad_ms': 200}})
        if language:
            options['language'] = language
        if initial_prompt:
            options['initial_prompt'] = initial_prompt
        options['no_speech_threshold'] = float(os.getenv('WHISPER_NO_SPEECH_THRESHOLD', '0.4'))
        options['compression_ratio_threshold'] = float(os.getenv('WHISPER_COMPRESSION_RATIO_THRESHOLD', '2.4'))
        options['log_prob_threshold'] = float(os.getenv('WHISPER_LOGPROB_THRESHOLD', '-1.0'))

        generator, info = self.model.transcribe(audio_path, **options)
        segments = []
        for i, segment in enumerate(generator, 1):
            item = {'start': segment.start, 'end': segment.end, 'text': segment.text.strip()}
            segments.append(item)
            if segment_callback:
                segment_callback(TranscriptionSegment(start=item['start'], end=item['end'], text=item['text']))
            if progress_callback and i % 10 == 0:
                progress_callback('whisper', f'Transcribed {i} segments...', min(30 + i, 80))
        if progress_callback:
            progress_callback('whisper', f'Transcription complete: {len(segments)} segments', 90)
        return segments

    def get_device(self) -> str:
        return self.device or "cpu"

    def get_backend_name(self) -> str:
        return "faster-whisper"

    def cleanup(self):
        if self.model is not None:
            del self.model
            self.model = None
            try:
                import torch
                if torch.cuda.is_available():
                    torch.cuda.empty_cache()
            except ImportError:
                pass
