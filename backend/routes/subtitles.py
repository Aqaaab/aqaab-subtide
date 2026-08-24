from flask import Blueprint, request, jsonify
from backend.services.youtube_service import fetch_subtitles
from backend.services.process_service import await_whisper_transcribe
from backend.config import ENABLE_WHISPER
from backend.utils.input_validation import validate_lang_code

subtitles_bp = Blueprint('subtitles', __name__)

@subtitles_bp.route('/api/subtitles', methods=['GET'])
def get_subtitles():
    """Fetch YouTube subtitles and optionally fall back to Whisper when none exist."""
    video_id = request.args.get('video_id')
    lang = request.args.get('lang', 'en')
    auto_whisper = request.args.get('auto_whisper', 'true').lower() == 'true'

    if not video_id:
        return jsonify({'error': 'video_id is required'}), 400

    from backend.services.youtube_service import validate_video_id
    if not validate_video_id(video_id):
        return jsonify({'error': 'Invalid video_id format'}), 400

    if not validate_lang_code(lang):
        return jsonify({'error': 'Invalid language code'}), 400

    response, status_code = fetch_subtitles(video_id, lang)
    if isinstance(response, dict):
        segments = response.get('segments') or response.get('events') or []
        if segments or not (auto_whisper and ENABLE_WHISPER):
            return jsonify(response), status_code

        # No usable captions: create a local transcription so the caller can
        # continue with normal translation without a second API contract.
        try:
            url = f"https://www.youtube.com/watch?v={video_id}"
            whisper_segments = await_whisper_transcribe(video_id, url)
            if whisper_segments:
                return jsonify({
                    'segments': whisper_segments,
                    'source': 'whisper',
                    'cached': True,
                }), 200
        except Exception:
            # Preserve the original subtitle response rather than masking the
            # underlying yt-dlp result when Whisper is unavailable.
            pass

    return jsonify(response) if isinstance(response, dict) else response, status_code
