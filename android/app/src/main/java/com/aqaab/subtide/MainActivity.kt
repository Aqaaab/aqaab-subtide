package com.aqaab.subtide

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    companion object { private const val REQUEST_CAPTURE = 1001; private const val REQUEST_AUDIO = 1002 }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Aqaab Subtide\nجاهز لبدء الترجمة"; textSize = 18f; setPadding(32, 48, 32, 32) }
        val start = Button(this).apply { text = "بدء ترجمة YouTube"; setOnClickListener { requestCapture() } }
        val stop = Button(this).apply { text = "إيقاف"; setOnClickListener { stopService(Intent(this@MainActivity, CaptureServiceV2::class.java)); status.text = "تم إيقاف الترجمة" } }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(status); addView(start); addView(stop) })
    }

    private fun requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "اسمح للتطبيق بالظهور فوق التطبيقات ثم اضغط بدء مرة أخرى."
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        requestProjection()
    }

    private fun requestProjection() {
        status.text = "وافق على طلب مشاركة الشاشة/الصوت من Android."
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) requestProjection()
        else if (requestCode == REQUEST_AUDIO) status.text = "يلزم السماح بالميكروفون/التقاط الصوت لبدء الترجمة."
    }

    @Deprecated("Use Activity Result API in a future UI refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureServiceV2::class.java).putExtra("resultCode", resultCode).putExtra("data", data))
            status.text = "الترجمة قيد التشغيل. افتح YouTube الآن."
        } else if (requestCode == REQUEST_CAPTURE) status.text = "لم تتم الموافقة على التقاط الصوت."
    }
}
