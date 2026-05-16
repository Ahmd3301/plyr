package io.videoplyr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var isPageLoaded = false
    private var pendingJs: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen Setup
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
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(AndroidBridge(), "Android")
        webView.webChromeClient = VideoWebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
                pendingJs?.let {
                    webView.evaluateJavascript(it, null)
                    pendingJs = null
                }
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
            "open" -> {
                val url = uri.getQueryParameter("url") ?: return
                val title = uri.getQueryParameter("title") ?: "Video"
                "window.loadVideo('$url', '$title')"
            }
            "playlist" -> {
                val data = uri.getQueryParameter("data") ?: return
                val json = String(Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP))
                "window.loadPlaylist('${json.replace("'", "\\'")}')"
            }
            else -> null
        }

        if (js != null) {
            if (isPageLoaded) webView.evaluateJavascript(js, null)
            else pendingJs = js
        }
    }

    override fun onBackPressed() {
        if (customView != null) {
            (webView.webChromeClient as VideoWebChromeClient).onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class VideoWebChromeClient : WebChromeClient() {
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customView = view
            customViewCallback = callback
            val decor = window.decorView as FrameLayout
            decor.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            webView.visibility = View.GONE
            WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }

        override fun onHideCustomView() {
            val decor = window.decorView as FrameLayout
            decor.removeView(customView)
            customView = null
            customViewCallback?.onCustomViewHidden()
            webView.visibility = View.VISIBLE
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface fun onVideoReady() {}
        @JavascriptInterface fun onError(msg: String) {}
    }
}
