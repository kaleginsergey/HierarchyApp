package com.example.hierarchyapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MapActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true

        // Build markers JSON
        val jsMarkers = buildMarkersJson()

        // Load HTML from assets, then inject data once page is ready
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val escaped = jsMarkers
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                view.evaluateJavascript("init('$escaped')", null)
            }
        }

        webView.loadUrl("file:///android_asset/map.html")
    }

    private fun buildMarkersJson(): String {
        fun findLabel(nodeId: Int): String {
            fun search(nodes: List<TreeNode>): String? {
                for (n in nodes) {
                    if (n.level == 3 && n.nodeId == nodeId) return n.label
                    search(n.children)?.let { return it }
                }
                return null
            }
            return search(DataRepository.buildTree()) ?: "Узел $nodeId"
        }

        fun esc(s: String) = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val items = DataRepository.nodeDetails.values.mapNotNull { d ->
            val parts = d.geoData.split(",").map { it.trim() }
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val label = findLabel(d.nodeId)
            """{"id":${d.nodeId},"lat":$lat,"lng":$lng,"label":"${esc(label)}","cls":"${esc(d.nodeClass)}","serial":"${esc(d.serialNumber)}","location":"${esc(d.location)}","limit":"${esc(d.consumptionLimit)}"}"""
        }

        return "[${items.joinToString(",")}]"
    }
}
