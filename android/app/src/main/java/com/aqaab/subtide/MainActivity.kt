package com.aqaab.subtide

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Entry point for the device-safe in-app YouTube mode. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 48, 32, 48)
            setBackgroundColor(Color.BLACK)
        }
        val title = TextView(this).apply {
            text = "Aqaab Subtide\nترجمة YouTube بالعربية"
            textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        }
        val info = TextView(this).apply {
            text = "وضع YouTube الداخلي لا يحتاج صلاحية الظهور فوق التطبيقات.\nالصق رابط الفيديو داخل المشغل ثم شغّله."
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY); setPadding(0, 24, 0, 24)
        }
        val start = Button(this).apply {
            text = "فتح YouTube داخل Aqaab"
            setOnClickListener { startActivity(Intent(this@MainActivity, YouTubePlayerActivity::class.java)) }
        }
        root.addView(title); root.addView(info); root.addView(start)
        setContentView(root)
    }
}
