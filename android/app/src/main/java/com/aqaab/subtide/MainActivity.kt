package com.aqaab.subtide

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    companion object { private const val REQUEST_CAPTURE = 1001 }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Aqaab Subtide\nجاهز لبدء الترجمة"; textSize = 18f; setPadding(32, 48, 32, 32) }
        val start = Button(this).apply { text = "بدء ترجمة YouTube"; setOnClickListener { requestCapture() } }
        val stop = Button(this).apply { text = "إيقاف"; setOnClickListener { stopService(Intent(this@MainActivity, CaptureService::class.java)); status.text = "تم إيقاف الترجمة" } }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(status); addView(start); addView(stop) })
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "اسمح للتطبيق بالظهور فوق التطبيقات ثم اضغط بدء مرة أخرى."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        status.text = "سيظهر الآن طلب مشاركة الشاشة/الصوت من Android."
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Use Activity Result API in a future UI refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra("resultCode", resultCode)
                .putExtra("data", data)
            startForegroundService(service)
            status.text = "الترجمة قيد التشغيل. افتح YouTube الآن."
        } else if (requestCode == REQUEST_CAPTURE) {
            status.text = "لم تتم الموافقة على التقاط الصوت."
        }
    }
}
