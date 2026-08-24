package com.aqaab.subtide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.ArrayBlockingQueue

class CaptureServiceV2 : Service() {
    companion object { private const val SAMPLE_RATE = 16000; private const val CHUNK_SECONDS = 8; private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS }

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var captureWorker: Thread? = null
    private var asrWorker: Thread? = null
    private var whisper: WhisperEngine? = null
    private var translator: LocalTranslator? = null
    private val chunks = ArrayBlockingQueue<ShortArray>(2)
    private val main = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        createNotificationChannel(); startForeground(10, notification())
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data) ?: return START_NOT_STICKY
        showOverlay("Aqaab: جاري الاستماع…")
        if (Build.VERSION.SDK_INT >= 29) startCapture()
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!).addMatchingUsage(AudioAttributes.USAGE_MEDIA).build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(min.coerceAtLeast(4096) * 2).setAudioPlaybackCaptureConfig(config).build()
        whisper = WhisperEngine(this)
        translator = LocalTranslator()
        recorder?.startRecording()
        captureWorker = Thread { captureLoop() }.also { it.start() }
        asrWorker = Thread { asrLoop() }.also { it.start() }
    }

    private fun captureLoop() {
        val block = ShortArray(CHUNK_SAMPLES)
        var filled = 0
        while (!Thread.currentThread().isInterrupted && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val n = recorder?.read(block, filled, block.size - filled) ?: -1
            if (n <= 0) continue
            filled += n
            if (filled == block.size) {
                chunks.offer(block.copyOf(), 1, java.util.concurrent.TimeUnit.SECONDS)
                filled = 0
            }
        }
    }

    private fun asrLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val audio = chunks.take()
            showOverlay("Aqaab: تحويل الكلام إلى نص…")
            val text = runCatching { whisper?.transcribe(audio, audio.size, SAMPLE_RATE).orEmpty() }.getOrElse { "" }
            if (text.isBlank()) continue
            showOverlay("Aqaab: ترجمة…")
            translator?.translateToArabic(text, { arabic -> showOverlay(arabic) }, { showOverlay("Aqaab: تعذر ترجمة المقطع") })
        }
    }

    private fun showOverlay(text: String) {
        main.post {
            if (windowManager == null) windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = overlay ?: TextView(this).also {
                it.textSize = 18f; it.setTextColor(0xFFFFFFFF.toInt()); it.setBackgroundColor(0xCC000000.toInt()); it.setPadding(24, 12, 24, 12); overlay = it
                val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
                val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, android.graphics.PixelFormat.TRANSLUCENT)
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; params.y = 140; windowManager?.addView(it, params)
            }
            view.text = text
        }
    }

    private fun createNotificationChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("translation", "Aqaab translation", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification = Notification.Builder(this, "translation").setContentTitle("Aqaab Subtide").setContentText("جلسة ترجمة YouTube قيد التشغيل").setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build()

    override fun onDestroy() {
        captureWorker?.interrupt(); asrWorker?.interrupt(); captureWorker = null; asrWorker = null
        recorder?.runCatching { stop() }; recorder?.release(); recorder = null
        whisper?.close(); whisper = null; translator?.close(); translator = null
        projection?.stop(); projection = null
        overlay?.let { runCatching { windowManager?.removeView(it) } }; overlay = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
