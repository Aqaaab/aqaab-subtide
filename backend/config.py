import os
import platform


def detect_platform():
    explicit_platform = os.getenv('PLATFORM')
    if explicit_platform:
        return explicit_platform
    if platform.system() == 'Darwin' and platform.machine() == 'arm64':
        return 'macos'
    if platform.system() == 'Windows':
        return 'windows'
    try:
        import torch
        if torch.cuda.is_available():
            return 'linux-cuda'
    except ImportError:
        pass
    return 'linux-cpu'

PLATFORM = detect_platform()
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.getenv('CACHE_DIR', os.path.join(BASE_DIR, 'cache'))
MODEL_CACHE_DIR = os.getenv('MODEL_CACHE_DIR', os.path.join(os.path.expanduser('~'), '.cache', 'subtide-models'))
CACHE_MAX_SIZE_MB = int(os.getenv('CACHE_MAX_SIZE_MB', '5000'))
CACHE_AUDIO_TTL_HOURS = int(os.getenv('CACHE_AUDIO_TTL_HOURS', '24'))
CACHE_CLEANUP_INTERVAL_MINUTES = int(os.getenv('CACHE_CLEANUP_INTERVAL_MINUTES', '30'))
LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
LOG_JSON = os.getenv('LOG_JSON', 'false').lower() == 'true'
LOG_FILE = os.getenv('LOG_FILE')

ENABLE_WHISPER = os.getenv('ENABLE_WHISPER', 'true').lower() == 'true'
HF_TOKEN = os.getenv('HF_TOKEN')
# Diarization is an optional, memory-heavy feature. It is opt-in.
ENABLE_DIARIZATION = os.getenv('ENABLE_DIARIZATION', 'false').lower() == 'true' and bool(HF_TOKEN)
DIARIZATION_MODE = os.getenv('DIARIZATION_MODE', 'off')

COOKIES_FILE = os.getenv('COOKIES_FILE')
LLM_PROVIDER = os.getenv('LLM_PROVIDER', 'openai').lower()
OPENAI_API_KEY = os.getenv('OPENAI_API_KEY') or os.getenv('SERVER_API_KEY')
ANTHROPIC_API_KEY = os.getenv('ANTHROPIC_API_KEY')
GOOGLE_API_KEY = os.getenv('GOOGLE_API_KEY')
MISTRAL_API_KEY = os.getenv('MISTRAL_API_KEY')
OPENROUTER_API_KEY = os.getenv('OPENROUTER_API_KEY')
DEEPSEEK_API_KEY = os.getenv('DEEPSEEK_API_KEY')
OLLAMA_BASE_URL = os.getenv('OLLAMA_BASE_URL')

OPENAI_MODEL = os.getenv('OPENAI_MODEL', 'gpt-4o-mini')
ANTHROPIC_MODEL = os.getenv('ANTHROPIC_MODEL', 'claude-3-5-sonnet-latest')
GOOGLE_MODEL = os.getenv('GOOGLE_MODEL', 'gemini-2.0-flash-exp')
MISTRAL_MODEL = os.getenv('MISTRAL_MODEL', 'mistral-large-latest')
OPENROUTER_MODEL = os.getenv('OPENROUTER_MODEL', 'google/gemini-2.0-flash-exp:free')
DEEPSEEK_MODEL = os.getenv('DEEPSEEK_MODEL', 'deepseek-chat')
OLLAMA_MODEL = os.getenv('OLLAMA_MODEL', 'llama3.3')

OPENAI_CONCURRENT_REQUESTS = int(os.getenv('OPENAI_CONCURRENT_REQUESTS', '3'))
ANTHROPIC_CONCURRENT_REQUESTS = int(os.getenv('ANTHROPIC_CONCURRENT_REQUESTS', '2'))
GOOGLE_CONCURRENT_REQUESTS = int(os.getenv('GOOGLE_CONCURRENT_REQUESTS', '5'))
MISTRAL_CONCURRENT_REQUESTS = int(os.getenv('MISTRAL_CONCURRENT_REQUESTS', '2'))
OPENROUTER_CONCURRENT_REQUESTS = int(os.getenv('OPENROUTER_CONCURRENT_REQUESTS', '5'))
DEEPSEEK_CONCURRENT_REQUESTS = int(os.getenv('DEEPSEEK_CONCURRENT_REQUESTS', '2'))
OLLAMA_CONCURRENT_REQUESTS = int(os.getenv('OLLAMA_CONCURRENT_REQUESTS', '1'))

def _get_active_api_key():
    provider_keys = {'openai': OPENAI_API_KEY, 'anthropic': ANTHROPIC_API_KEY, 'google': GOOGLE_API_KEY,
                     'mistral': MISTRAL_API_KEY, 'openrouter': OPENROUTER_API_KEY, 'deepseek': DEEPSEEK_API_KEY,
                     'ollama': 'ollama'}
    return provider_keys.get(LLM_PROVIDER) or OPENAI_API_KEY

def _get_active_model():
    provider_models = {'openai': OPENAI_MODEL, 'anthropic': ANTHROPIC_MODEL, 'google': GOOGLE_MODEL,
                       'mistral': MISTRAL_MODEL, 'openrouter': OPENROUTER_MODEL, 'deepseek': DEEPSEEK_MODEL,
                       'ollama': OLLAMA_MODEL}
    return os.getenv('SERVER_MODEL') or provider_models.get(LLM_PROVIDER) or OPENAI_MODEL

SERVER_API_KEY = _get_active_api_key()
SERVER_MODEL = _get_active_model()
SERVER_API_URL = os.getenv('SERVER_API_URL')

_MODEL_LANG_MAP_STR = os.getenv('MODEL_LANG_MAP', '{}')
try:
    import json
    MODEL_LANG_MAP = json.loads(_MODEL_LANG_MAP_STR)
except (json.JSONDecodeError, TypeError, ValueError):
    MODEL_LANG_MAP = {}

def get_model_for_language(target_lang: str) -> str:
    if target_lang in MODEL_LANG_MAP:
        return MODEL_LANG_MAP[target_lang]
    base_lang = target_lang.split('-')[0]
    if base_lang in MODEL_LANG_MAP:
        return MODEL_LANG_MAP[base_lang]
    return MODEL_LANG_MAP.get('default', SERVER_MODEL)

# Lite-safe Whisper defaults; users can opt into larger models explicitly.
WHISPER_MODEL_SIZE = os.getenv('WHISPER_MODEL', 'tiny')
WHISPER_QUANTIZED = os.getenv('WHISPER_QUANTIZED', 'false').lower() == 'true'
WHISPER_HF_REPO = os.getenv('WHISPER_HF_REPO')
WHISPER_LANGUAGE = os.getenv('WHISPER_LANGUAGE', '') or None
WHISPER_CPU_THREADS = max(1, min(int(os.getenv('WHISPER_CPU_THREADS', '2')), os.cpu_count() or 2))

LANG_NAMES = {'en':'English','ja':'Japanese','ko':'Korean','zh-CN':'Chinese (Simplified)','zh-TW':'Chinese (Traditional)',
'es':'Spanish','fr':'French','de':'German','pt':'Portuguese','ru':'Russian','ar':'Arabic','hi':'Hindi','it':'Italian',
'nl':'Dutch','pl':'Polish','tr':'Turkish','vi':'Vietnamese','th':'Thai','id':'Indonesian'}

ENABLE_VAD = os.getenv('ENABLE_VAD', 'false').lower() == 'true'
VAD_THRESHOLD = float(os.getenv('VAD_THRESHOLD', '0.5'))
MAX_SUBTITLE_DURATION = float(os.getenv('MAX_SUBTITLE_DURATION', '6.0'))
MAX_SUBTITLE_WORDS = int(os.getenv('MAX_SUBTITLE_WORDS', '15'))
MIN_SPEAKERS = int(os.getenv('MIN_SPEAKERS', '0')) or None
MAX_SPEAKERS = int(os.getenv('MAX_SPEAKERS', '0')) or None
DIARIZATION_SMOOTHING = os.getenv('DIARIZATION_SMOOTHING', 'true').lower() == 'true'
MIN_SEGMENT_DURATION = float(os.getenv('MIN_SEGMENT_DURATION', '1.0'))
WHISPER_NO_SPEECH_THRESHOLD = float(os.getenv('WHISPER_NO_SPEECH_THRESHOLD', '0.4'))
WHISPER_COMPRESSION_RATIO_THRESHOLD = float(os.getenv('WHISPER_COMPRESSION_RATIO_THRESHOLD', '2.4'))
WHISPER_LOGPROB_THRESHOLD = float(os.getenv('WHISPER_LOGPROB_THRESHOLD', '-1.0'))
WHISPER_CONDITION_ON_PREVIOUS = os.getenv('WHISPER_CONDITION_ON_PREVIOUS', 'false').lower() == 'true'
WHISPER_BEAM_SIZE = max(1, int(os.getenv('WHISPER_BEAM_SIZE', '1')))

def get_whisper_backend_type():
    override = os.getenv('WHISPER_BACKEND')
    if override:
        return override
    if PLATFORM == 'runpod': return 'faster-whisper'
    if PLATFORM == 'macos': return 'mlx-whisper'
    return 'openai-whisper'

def get_diarization_backend_type():
    override = os.getenv('DIARIZATION_BACKEND')
    if override:
        return override
    if PLATFORM == 'runpod': return 'nemo'
    return 'pyannote'

WHISPER_BACKEND = get_whisper_backend_type()
DIARIZATION_BACKEND = get_diarization_backend_type()
