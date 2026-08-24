package com.aqaab.subtide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import java.util.concurrent.TimeUnit

class CaptureServiceV2 : Service() {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SECONDS = 8
        private const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_SECONDS
        private const val MAX_QUEUED_CHUNKS = 2
        private const val NOTIFICATION_ID = 10
        private const val CHANNEL_ID = "translation"
    }

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var captureWorker: Thread? = null
    private var asrWorker: Thread? = null
    private var whisper: WhisperEngine? = null
    private var translator: LocalTranslator? = null

    private val chunks = ArrayBlockingQueue<ShortArray>(MAX_QUEUED_CHUNKS)
    private val main = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    @Volatile private var stopping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode == null || data == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForegroundCompat()

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
        if (projection == null) {
            showOverlay("Aqaab: تعذر بدء التقاط الصوت")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        showOverlay("Aqaab: جاري الاستماع…")
        startCapture()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture() {
        if (Build.VERSION.SDK_INT < 29) {
            showOverlay("Aqaab: يلزم Android 10 أو أحدث للتقاط صوت YouTube")
            stopSelf()
            return
        }

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            showOverlay("Aqaab: جهاز الصوت غير مدعوم")
            stopSelf()
            return
        }

        recorder = runCatching {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes((minBuffer * 2).coerceAtLeast(16_384))
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        }.getOrElse {
            showOverlay("Aqaab: تعذر إنشاء مسجل الصوت")
            stopSelf()
            return
        }

        whisper = runCatching { WhisperEngine(this) }.getOrElse {
            showOverlay("Aqaab: تعذر تحميل Whisper Tiny")
            stopSelf()
            return
        }
        translator = LocalTranslator()

        chunks.clear()
        stopping = false
        recorder?.startRecording()

        captureWorker = Thread({ captureLoop() }, "Aqaab-AudioCapture").also { it.start() }
        asrWorker = Thread({ asrLoop() }, "Aqaab-Whisper").also { it.start() }
    }

    private fun captureLoop() {
        val block = ShortArray(CHUNK_SAMPLES)
        var filled = 0

        while (!stopping && !Thread.currentThread().isInterrupted) {
            val currentRecorder = recorder ?: break
            if (currentRecorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) break

            val read = currentRecorder.read(block, filled, block.size - filled)
            if (read <= 0) continue
            filled += read

            if (filled == block.size) {
                // Drop the oldest pending chunk instead of blocking audio capture.
                if (!chunks.offer(block.copyOf())) {
                    chunks.poll()
                    chunks.offer(block.copyOf())
                }
                filled = 0
            }
        }
    }

    private fun asrLoop() {
        while (!stopping && !Thread.currentThread().isInterrupted) {
            val audio = try {
                chunks.poll(500, TimeUnit.MILLISECONDS) ?: continue
            } catch (_: InterruptedException) {
                break
            }

            showOverlay("Aqaab: تحويل الكلام إلى نص…")
            val text = runCatching {
                whisper?.transcribe(audio, audio.size, SAMPLE_RATE).orEmpty()
            }.getOrElse { "" }

            if (text.isBlank()) continue

            showOverlay("Aqaab: ترجمة…")
            translator?.translateToArabic(
                text,
                onResult = { arabic -> showOverlay(arabic) },
                onError = { showOverlay("Aqaab: تعذر ترجمة المقطع") },
            )
        }
    }

    private fun showOverlay(text: String) {
        main.post {
            if (stopping) return@post
            if (windowManager == null) {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            }

            val view = overlay ?: TextView(this).also {
                it.textSize = 18f
                it.setTextColor(0xFFFFFFFF.toInt())
                it.setBackgroundColor(0xCC000000.toInt())
                it.setPadding(24, 12, 24, 12)
                overlay = it

                val type = if (Build.VERSION.SDK_INT >= 26) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    android.graphics.PixelFormat.TRANSLUCENT,
                )
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.y = 140
                runCatching { windowManager?.addView(it, params) }
            }
            view.text = text
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aqaab translation",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun notification(): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Aqaab Subtide")
                .setContentText("جلسة ترجمة YouTube قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Aqaab Subtide")
                .setContentText("جلسة ترجمة YouTube قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        stopping = true
        captureWorker?.interrupt()
        asrWorker?.interrupt()
        captureWorker = null
        asrWorker = null

        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null

        whisper?.close()
        whisper = null
        translator?.close()
        translator = null
        chunks.clear()

        projection?.stop()
        projection = null

        overlay?.let { view ->
            main.post { runCatching { windowManager?.removeView(view) } }
        }
        overlay = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
