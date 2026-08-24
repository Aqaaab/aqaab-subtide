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
import android.os.IBinder

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, data)

        val channel = NotificationChannel("translation", "Aqaab translation", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, "translation")
            .setContentTitle("Aqaab Subtide")
            .setContentText("ترجمة YouTube قيد التشغيل")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(10, notification)

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(min.coerceAtLeast(4096) * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            recorder?.startRecording()
            Thread { readAudio() }.start()
        }
        return START_NOT_STICKY
    }

    private fun readAudio() {
        val buffer = ShortArray(4096)
        while (!Thread.currentThread().isInterrupted && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            recorder?.read(buffer, 0, buffer.size)
            // TODO: feed bounded audio chunks to the on-device ASR engine.
        }
    }

    override fun onDestroy() {
        recorder?.stop()
        recorder?.release()
        recorder = null
        projection?.stop()
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
