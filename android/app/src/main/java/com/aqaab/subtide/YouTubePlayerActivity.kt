package com.aqaab.subtide

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** In-app YouTube mode. No SYSTEM_ALERT_WINDOW permission is required. */
class YouTubePlayerActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var urlInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val translator = LocalTranslator()
    private var lastCaption = ""
    private var lastCaptionAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Aqaab Subtide"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val header = TextView(this).apply {
            text = "Aqaab Subtide — ترجمة YouTube"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(16, 14, 16, 6)
        }
        root.addView(header)

        status = TextView(this).apply {
            text = "ألصق رابط YouTube ثم اضغط تشغيل"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(16, 0, 16, 8)
        }
        root.addView(status)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 6, 10, 8)
        }

        urlInput = EditText(this).apply {
            hint = "رابط فيديو YouTube"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        controls.addView(urlInput)

        val paste = Button(this).apply {
            text = "لصق"
            setOnClickListener { pasteUrl() }
        }
        controls.addView(paste)

        val open = Button(this).apply {
            text = "تشغيل"
            setOnClickListener { loadYouTube(urlInput.text.toString()) }
        }
        controls.addView(open)
        root.addView(controls)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = MOBILE_CHROME_USER_AGENT
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    status.text = "تم فتح YouTube. شغّل الفيديو ثم فعّل CC إن لزم."
                    injectCaptionScript()
                }
            }
            addJavascriptInterface(CaptionBridge(), "AqaabCaption")
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)

        subtitle = TextView(this).apply {
            text = "الترجمة العربية تظهر هنا"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            setPadding(20, 14, 20, 14)
        }
        root.addView(subtitle)
        setContentView(root)

        val initialUrl = intent.getStringExtra("url").orEmpty()
        if (initialUrl.isNotBlank()) {
            urlInput.setText(initialUrl)
            loadYouTube(initialUrl)
        }
    }

    private fun pasteUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val value = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (value.isBlank()) {
            Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
            return
        }
        urlInput.setText(value)
        urlInput.setSelection(urlInput.text.length)
        status.text = "تم لصق الرابط — اضغط تشغيل"
    }

    private fun loadYouTube(raw: String) {
        val value = raw.trim()
        val normalized = normalizeYouTubeUrl(value)
        if (normalized == null) {
            status.text = "الرابط غير صالح. استخدم رابط YouTube كاملًا أو youtu.be"
            return
        }
        lastCaption = ""
        lastCaptionAt = 0L
        subtitle.text = "الترجمة العربية تظهر هنا"
        status.text = "جاري فتح الفيديو…"
        webView.loadUrl(normalized)
        startCaptionPolling()
    }

    private fun normalizeYouTubeUrl(value: String): String? {
        if (value.isBlank()) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host == "youtu.be" -> {
                val id = uri.path.orEmpty().trim('/')
                if (id.isBlank()) null else "https://www.youtube.com/watch?v=$id"
            }
            host == "youtube.com" || host.endsWith(".youtube.com") -> value
            else -> null
        }
    }

    private fun startCaptionPolling() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) return
                injectCaptionScript()
                handler.postDelayed(this, 700)
            }
        })
    }

    private fun injectCaptionScript() {
        webView.evaluateJavascript(CAPTION_SCRIPT, null)
    }

    inner class CaptionBridge {
        @JavascriptInterface
        fun onCaption(text: String) {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.isBlank()) return
            val now = System.currentTimeMillis()
            if (clean == lastCaption && now - lastCaptionAt < 1500) return
            lastCaption = clean
            lastCaptionAt = now
            runOnUiThread { status.text = "تم التقاط النص — جاري الترجمة…" }
            translator.translateToArabic(
                clean,
                { arabic ->
                    runOnUiThread {
                        subtitle.text = arabic.ifBlank { clean }
                        status.text = "الترجمة تعمل"
                    }
                },
                {
                    runOnUiThread {
                        subtitle.text = clean
                        status.text = "تعذرت الترجمة، يظهر النص الأصلي"
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.removeJavascriptInterface("AqaabCaption")
        webView.stopLoading()
        webView.destroy()
        translator.close()
        super.onDestroy()
    }

    companion object {
        private const val MOBILE_CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"

        private const val CAPTION_SCRIPT = """
            (function(){
              try {
                function emit(){
                  var selectors=[
                    '.ytp-caption-segment',
                    '.caption-window',
                    '.ytp-caption-window-container',
                    '.ytp-caption-window-bottom',
                    '[class*="caption-segment"]'
                  ];
                  var parts=[];
                  selectors.forEach(function(sel){
                    document.querySelectorAll(sel).forEach(function(n){
                      var t=(n.innerText||n.textContent||'').trim();
                      if(t) parts.push(t);
                    });
                  });
                  var text=[...new Set(parts)].join(' ').replace(/\s+/g,' ').trim();
                  if(text && window.AqaabCaption) window.AqaabCaption.onCaption(text);
                }
                emit();
                if(!window.__aqaabCaptionObserver){
                  window.__aqaabCaptionObserver=new MutationObserver(function(){ emit(); });
                  if(document.documentElement){
                    window.__aqaabCaptionObserver.observe(document.documentElement,{subtree:true,childList:true,characterData:true});
                  }
                }
              } catch(e) {}
            })();
        """
    }
}
