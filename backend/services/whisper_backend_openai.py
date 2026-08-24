"""
Whisper Backend - OpenAI Whisper
Standard implementation using openai-whisper package.
"""

import os
import logging
from typing import List, Dict, Any, Optional, Callable

from services.whisper_backend_base import WhisperBackend, TranscriptionSegment
from whisper_threads import configure_whisper_threads

logger = logging.getLogger('subtide')


class OpenAIWhisperBackend(WhisperBackend):
    """OpenAI Whisper backend with conservative CPU limits."""

    def __init__(self, model_size: str = None):
        self.model_size = model_size or os.getenv('WHISPER_MODEL', 'tiny')
        self.model = None
        self.device = None
        self._detect_device()

    def _detect_device(self):
        try:
            import torch
            if torch.cuda.is_available():
                self.device = "cuda"
            elif hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
                self.device = "mps"
            else:
                self.device = "cpu"
            logger.info(f"OpenAI Whisper: Using device {self.device}")
        except ImportError:
            self.device = "cpu"
            logger.warning("OpenAI Whisper: torch not found, using CPU")

    def _load_model(self):
        if self.model is not None:
            return
        try:
            # Apply the limit before loading/using the model.
            threads = configure_whisper_threads()
            import whisper
            logger.info(f"Loading OpenAI Whisper model: {self.model_size} (threads={threads})")
            self.model = whisper.load_model(self.model_size, device=self.device)
            logger.info(f"OpenAI Whisper model loaded on {self.device}")
        except ImportError as e:
            raise RuntimeError("openai-whisper not installed. Install with: pip install openai-whisper") from e

    def transcribe(
        self,
        audio_path: str,
        model_size: str = None,
        language: Optional[str] = None,
        initial_prompt: Optional[str] = None,
        progress_callback: Optional[Callable[[str, str, int], None]] = None,
        segment_callback: Optional[Callable[[TranscriptionSegment], None]] = None,
    ) -> List[Dict[str, Any]]:
        if model_size and model_size != self.model_size:
            self.model_size = model_size
            self.model = None

        self._load_model()
        if progress_callback:
            progress_callback('whisper', 'Starting transcription...', 10)

        transcribe_options = {
            'verbose': False,
            'fp16': self.device == 'cuda',
            'beam_size': int(os.getenv('WHISPER_BEAM_SIZE', '1')),
            'condition_on_previous_text': os.getenv('WHISPER_CONDITION_ON_PREVIOUS', 'false').lower() == 'true',
        }
        if language:
            transcribe_options['language'] = language
        if initial_prompt:
            transcribe_options['initial_prompt'] = initial_prompt

        transcribe_options['no_speech_threshold'] = float(os.getenv('WHISPER_NO_SPEECH_THRESHOLD', '0.4'))
        transcribe_options['compression_ratio_threshold'] = float(os.getenv('WHISPER_COMPRESSION_RATIO_THRESHOLD', '2.4'))
        transcribe_options['logprob_threshold'] = float(os.getenv('WHISPER_LOGPROB_THRESHOLD', '-1.0'))

        logger.info(f"OpenAI Whisper: Transcribing {audio_path}")
        result = self.model.transcribe(audio_path, **transcribe_options)

        segments = []
        for i, seg in enumerate(result.get('segments', [])):
            seg_dict = {'start': seg.get('start', 0), 'end': seg.get('end', 0), 'text': seg.get('text', '').strip()}
            segments.append(seg_dict)
            if segment_callback:
                segment_callback(TranscriptionSegment(start=seg_dict['start'], end=seg_dict['end'], text=seg_dict['text']))
            if progress_callback and i % 10 == 0:
                progress_callback('whisper', f'Processed {i} segments...', min(30 + i, 80))

        if progress_callback:
            progress_callback('whisper', f'Transcription complete: {len(segments)} segments', 90)
        return segments

    def get_device(self) -> str:
        return self.device or "cpu"

    def get_backend_name(self) -> str:
        return "openai-whisper"

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
