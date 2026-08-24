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

/** In-app YouTube mode with bounded caption queue and explicit translation states. */
class YouTubePlayerActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var urlInput: EditText
    private val handler = Handler(Looper.getMainLooper())
    private val translator = LocalTranslator()
    private var lastCaption = ""
    private var lastCaptionAt = 0L
    private var pendingCaption: String? = null
    private var translating = false
    private var modelReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Aqaab Subtide"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        root.addView(TextView(this).apply {
            text = "Aqaab Subtide — ترجمة YouTube"
            textSize = 19f; setTextColor(Color.WHITE); setPadding(16, 14, 16, 6)
        })
        status = TextView(this).apply {
            text = "جاري تجهيز نموذج الترجمة الهندية…"
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(16, 0, 16, 8)
        }
        root.addView(status)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(10, 6, 10, 8) }
        urlInput = EditText(this).apply {
            hint = "رابط فيديو YouTube"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        controls.addView(urlInput)
        controls.addView(Button(this).apply { text = "لصق"; setOnClickListener { pasteUrl() } })
        controls.addView(Button(this).apply { text = "تشغيل"; setOnClickListener { loadYouTube(urlInput.text.toString()) } })
        root.addView(controls)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true; settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = MOBILE_CHROME_USER_AGENT; webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    status.text = if (modelReady) "تم فتح YouTube. شغّل الفيديو وفعّل CC إذا لزم." else "جاري تجهيز نموذج الترجمة…"
                    injectCaptionScript()
                }
            }
            addJavascriptInterface(CaptionBridge(), "AqaabCaption")
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)
        subtitle = TextView(this).apply {
            text = "الترجمة العربية تظهر هنا"; textSize = 19f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.argb(220, 0, 0, 0)); setPadding(20, 14, 20, 14)
        }
        root.addView(subtitle); setContentView(root)

        translator.prepare("hi", {
            runOnUiThread {
                modelReady = true
                status.text = "جاهز — الصق رابط YouTube ثم اضغط تشغيل"
            }
        }, {
            runOnUiThread { status.text = "تعذر تجهيز نموذج الهندية. تحقق من الإنترنت ثم أعد فتح التطبيق." }
        })
        val initialUrl = intent.getStringExtra("url").orEmpty()
        if (initialUrl.isNotBlank()) { urlInput.setText(initialUrl); loadYouTube(initialUrl) }
    }

    private fun pasteUrl() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val value = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
        if (value.isBlank()) { Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show(); return }
        urlInput.setText(value); urlInput.setSelection(urlInput.text.length); status.text = "تم لصق الرابط — اضغط تشغيل"
    }

    private fun loadYouTube(raw: String) {
        val normalized = normalizeYouTubeUrl(raw.trim())
        if (normalized == null) { status.text = "الرابط غير صالح. استخدم رابط YouTube كاملًا أو youtu.be"; return }
        lastCaption = ""; lastCaptionAt = 0L; pendingCaption = null; translating = false
        subtitle.text = "الترجمة العربية تظهر هنا"
        status.text = if (modelReady) "جاري فتح الفيديو…" else "جاري تجهيز نموذج الترجمة…"
        webView.loadUrl(normalized); startCaptionPolling()
    }

    private fun normalizeYouTubeUrl(value: String): String? {
        if (value.isBlank()) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        return when {
            host == "youtu.be" -> uri.path.orEmpty().trim('/').takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
            host == "youtube.com" || host.endsWith(".youtube.com") -> value
            else -> null
        }
    }

    private fun startCaptionPolling() {
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) return
                injectCaptionScript(); handler.postDelayed(this, 500)
            }
        })
    }

    private fun injectCaptionScript() { webView.evaluateJavascript(CAPTION_SCRIPT, null) }

    private fun enqueueCaption(clean: String) {
        if (clean == lastCaption && System.currentTimeMillis() - lastCaptionAt < 1200) return
        pendingCaption = clean
        if (!translating) processNextCaption()
    }

    private fun processNextCaption() {
        val text = pendingCaption ?: return
        pendingCaption = null
        translating = true
        status.text = "تم التقاط النص — جاري الترجمة…"
        val hint = if (HINDI_REGEX.containsMatchIn(text)) "hi" else null
        if (hint == null) {
            translating = false; subtitle.text = text; status.text = "تم عرض النص — لم يتم تحديد الهندية"; return
        }
        translator.translateToArabic(text, hint,
            { arabic ->
                runOnUiThread { subtitle.text = arabic.ifBlank { text }; status.text = "الترجمة تعمل" }
                translating = false; if (pendingCaption != null) processNextCaption()
            },
            { error ->
                runOnUiThread { subtitle.text = text; status.text = "تعذرت الترجمة: ${error.message ?: "خطأ غير معروف"}" }
                translating = false; if (pendingCaption != null) processNextCaption()
            }
        )
        handler.postDelayed({
            if (translating) {
                translating = false
                runOnUiThread { status.text = "تأخرت الترجمة — أعد المحاولة أو تحقق من النموذج" }
                if (pendingCaption != null) processNextCaption()
            }
        }, TRANSLATION_TIMEOUT_MS)
    }

    inner class CaptionBridge {
        @JavascriptInterface fun onCaption(text: String) {
            val clean = text.replace(Regex("\\s+"), " ").trim()
            if (clean.isBlank()) return
            val now = System.currentTimeMillis()
            if (clean == lastCaption && now - lastCaptionAt < 1200) return
            lastCaption = clean; lastCaptionAt = now
            runOnUiThread { enqueueCaption(clean) }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); pendingCaption = null
        webView.removeJavascriptInterface("AqaabCaption"); webView.stopLoading(); webView.destroy(); translator.close(); super.onDestroy()
    }

    companion object {
        private val HINDI_REGEX = Regex("[\\u0900-\\u097F]")
        private const val TRANSLATION_TIMEOUT_MS = 8000L
        private const val MOBILE_CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36"
        private const val CAPTION_SCRIPT = """
            (function(){try{
              function emit(){
                var selectors=['.ytp-caption-segment','.caption-window','.ytp-caption-window-container','.ytp-caption-window-bottom','[class*=\"caption-segment\"]'];
                var parts=[];
                selectors.forEach(function(sel){document.querySelectorAll(sel).forEach(function(n){var t=(n.innerText||n.textContent||'').trim();if(t)parts.push(t);});});
                var text=[...new Set(parts)].join(' ').replace(/\s+/g,' ').trim();
                if(text&&window.AqaabCaption)window.AqaabCaption.onCaption(text);
              }
              emit();
              if(!window.__aqaabCaptionObserver){window.__aqaabCaptionObserver=new MutationObserver(function(){emit();});if(document.documentElement)window.__aqaabCaptionObserver.observe(document.documentElement,{subtree:true,childList:true,characterData:true});}
            }catch(e){}}
            )();
        """
    }
}
