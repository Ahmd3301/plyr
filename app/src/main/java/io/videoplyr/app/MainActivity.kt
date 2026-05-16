package io.videoplyr.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var isPageLoaded = false
    private var pendingJs: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ملء الشاشة بالكامل خلف النوتش
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
                pendingJs?.let { webView.evaluateJavascript(it, null); pendingJs = null }
            }
        }

        webView.loadUrl("file:///android_asset/player.html")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val js = when (uri.host) {
            "open" -> "window.loadVideo('${uri.getQueryParameter("url")}','${uri.getQueryParameter("title") ?: "Video"}')"
            "playlist" -> {
                val data = uri.getQueryParameter("data") ?: return
                val json = String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP))
                "window.loadPlaylist('${json.replace("'", "\\'")}')"
            }
            else -> null
        }
        if (js != null) {
            if (isPageLoaded) webView.evaluateJavascript(js, null) else pendingJs = js
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun closeApp() {
            finishAffinity() // إغلاق التطبيق بالكامل
        }
    }
}
