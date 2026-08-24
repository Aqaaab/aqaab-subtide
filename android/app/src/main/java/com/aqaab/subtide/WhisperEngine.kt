package com.aqaab.subtide

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * Small, multilingual Whisper Tiny INT8 engine for 4GB Android devices.
 * Model files are supplied as app assets by the release workflow.
 */
class WhisperEngine(context: Context) {
    companion object {
        private const val MODEL_DIR = "sherpa-onnx-whisper-tiny"
    }

    private val recognizer: OfflineRecognizer

    init {
        val model = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$MODEL_DIR/tiny-encoder.int8.onnx",
                decoder = "$MODEL_DIR/tiny-decoder.int8.onnx",
                language = "",
                task = "transcribe",
                tailPaddings = 300,
                enableTokenTimestamps = false,
                enableSegmentTimestamps = false,
            ),
            tokens = "$MODEL_DIR/tiny-tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
            modelType = "whisper",
        )
        recognizer = OfflineRecognizer(assetManager = context.assets, config = com.k2fsa.sherpa.onnx.OfflineRecognizerConfig(modelConfig = model))
    }

    fun transcribe(samples: ShortArray, length: Int, sampleRate: Int = 16000): String {
        if (length <= 0) return ""
        val audio = FloatArray(length)
        for (i in 0 until length) audio[i] = samples[i] / 32768.0f
        val stream = recognizer.createStream()
        stream.acceptWaveform(audio, sampleRate)
        recognizer.decode(stream)
        val result = recognizer.getResult(stream)
        stream.release()
        return result.text.trim()
    }

    fun close() = recognizer.release()
}
