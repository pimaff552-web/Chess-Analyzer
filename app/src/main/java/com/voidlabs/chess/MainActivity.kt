package com.voidlabs.chess

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return null

                if (uri.host != "appassets.androidplatform.net") return null

                val path = uri.path ?: return null
                if (!path.startsWith("/assets/")) return null

                val assetName = path.removePrefix("/assets/")
                if (assetName.contains("..")) return null

                val mime = when {
                    assetName.endsWith(".html") -> "text/html"
                    assetName.endsWith(".js") -> "application/javascript"
                    assetName.endsWith(".wasm") -> "application/wasm"
                    else -> "application/octet-stream"
                }

                return try {
                    WebResourceResponse(
                        mime,
                        null,
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        assets.open(assetName)
                    )
                } catch (e: Exception) {
                    WebResourceResponse("text/plain", "UTF-8", null)
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
}
