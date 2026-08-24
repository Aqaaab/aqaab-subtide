package com.aqaab.subtide

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Button
import android.view.ViewGroup
import android.graphics.Color
import android.view.Gravity
import android.os.Handler
import android.os.Looper

/** In-app YouTube mode. No SYSTEM_ALERT_WINDOW permission is required. */
class YouTubePlayerActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var subtitle: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val translator = LocalTranslator()
    private var lastCaption = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 12, 12, 12) }
        val url = EditText(this).apply {
            hint = "الصق رابط YouTube هنا"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val open = Button(this).apply { text = "تشغيل"; setOnClickListener { loadYouTube(url.text.toString().trim()) } }
        controls.addView(url); controls.addView(open); root.addView(controls)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT; webViewClient = WebViewClient(); webChromeClient = WebChromeClient()
            addJavascriptInterface(CaptionBridge(), "AqaabCaption")
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)
        subtitle = TextView(this).apply {
            text = "الترجمة العربية ستظهر هنا عند توفر captions في الفيديو"; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.argb(190, 0, 0, 0)); setPadding(20, 12, 20, 12)
        }
        root.addView(subtitle); setContentView(root)
        loadYouTube(intent.getStringExtra("url") ?: "https://m.youtube.com/")
    }

    private fun loadYouTube(value: String) {
        val normalized = when {
            value.isBlank() -> "https://m.youtube.com/"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> "https://m.youtube.com/results?search_query=" + android.net.Uri.encode(value)
        }
        webView.loadUrl(normalized); startCaptionPolling()
    }

    private fun startCaptionPolling() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                if (!isFinishing) { webView.evaluateJavascript(CAPTION_SCRIPT, null); handler.postDelayed(this, 900) }
            }
        })
    }

    inner class CaptionBridge {
        @JavascriptInterface
        fun onCaption(text: String) {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.isBlank() || clean == lastCaption) return
            lastCaption = clean
            translator.translateToArabic(clean, { arabic -> runOnUiThread { subtitle.text = arabic.ifBlank { clean } } }, { runOnUiThread { subtitle.text = clean } })
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); translator.close(); webView.removeJavascriptInterface("AqaabCaption"); webView.destroy(); super.onDestroy()
    }

    companion object {
        private const val CAPTION_SCRIPT = """
            (function(){try{
              var nodes=document.querySelectorAll('.ytp-caption-segment, .caption-window');
              var parts=[]; nodes.forEach(function(n){var t=(n.innerText||n.textContent||'').trim();if(t)parts.push(t);});
              var text=[...new Set(parts)].join(' ');
              if(text&&window.AqaabCaption)window.AqaabCaption.onCaption(text);
            }catch(e){}})();
        """
    }
}
