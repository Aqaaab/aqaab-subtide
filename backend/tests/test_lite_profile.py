import os


def test_lite_defaults(monkeypatch):
    monkeypatch.setenv('WHISPER_MODEL', 'tiny')
    monkeypatch.setenv('WHISPER_CPU_THREADS', '2')
    monkeypatch.setenv('WHISPER_BEAM_SIZE', '1')
    monkeypatch.setenv('ENABLE_DIARIZATION', 'false')
    monkeypatch.setenv('ENABLE_VAD', 'false')

    # Import only after environment is prepared.
    import backend.config as config

    assert config.WHISPER_MODEL_SIZE == 'tiny'
    assert config.WHISPER_CPU_THREADS == 2
    assert config.WHISPER_BEAM_SIZE == 1
    assert config.ENABLE_DIARIZATION is False
    assert config.ENABLE_VAD is False


def test_no_secret_in_lite_example():
    path = os.path.join(os.path.dirname(__file__), '..', '.env.lite.example')
    text = open(path, encoding='utf-8').read()
    assert 'OPENAI_API_KEY=' in text
    assert not any(marker in text for marker in ('sk-', 'AIza', 'ghp_'))
