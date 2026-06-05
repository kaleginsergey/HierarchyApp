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
        webView.webViewClient = WebViewClient()

        // Build markers JSON from all node details
        val markers = DataRepository.nodeDetails.values.map { detail ->
            val parts = detail.geoData.split(",").map { it.trim() }
            val lat = parts.getOrNull(0) ?: "0"
            val lng = parts.getOrNull(1) ?: "0"
            // Find node label from tree
            val label = findNodeLabel(detail.nodeId)
            """{"lat":$lat,"lng":$lng,"id":${detail.nodeId},"label":"${label.replace("\"","\\\"")}","class":"${detail.nodeClass}","serial":"${detail.serialNumber}","location":"${detail.location}","limit":"${detail.consumptionLimit}"}"""
        }.joinToString(",")

        val html = buildHtml(markers)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun findNodeLabel(nodeId: Int): String {
        fun searchNodes(nodes: List<TreeNode>): String? {
            for (node in nodes) {
                if (node.level == 3 && node.nodeId == nodeId) return node.label
                val found = searchNodes(node.children)
                if (found != null) return found
            }
            return null
        }
        return searchNodes(DataRepository.buildTree()) ?: "Узел $nodeId"
    }

    private fun buildHtml(markersJson: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  html, body { width:100%; height:100%; }
  #map { width:100%; height:100%; }
  .popup {
    font-family: sans-serif;
    min-width: 180px;
  }
  .popup-title {
    font-weight: bold;
    font-size: 13px;
    margin-bottom: 8px;
    color: #1565C0;
    border-bottom: 1px solid #eee;
    padding-bottom: 6px;
  }
  .popup-row {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 12px;
    margin-bottom: 4px;
  }
  .popup-label { color: #888; }
  .popup-value { font-weight: 500; color: #222; }
</style>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
</head>
<body>
<div id="map"></div>
<script>
const markers = [$markersJson];

const map = L.map('map');

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  attribution: '© OpenStreetMap',
  maxZoom: 19
}).addTo(map);

const icon = L.divIcon({
  className: '',
  html: '<div style="width:28px;height:28px;background:#16A34A;border:3px solid white;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,0.4);display:flex;align-items:center;justify-content:center;color:white;font-size:11px;font-weight:bold;">' + '</div>',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
  popupAnchor: [0, -16]
});

const bounds = [];
markers.forEach(m => {
  const lat = parseFloat(m.lat);
  const lng = parseFloat(m.lng);
  bounds.push([lat, lng]);
  const popupHtml = '<div class="popup">' +
    '<div class="popup-title">№' + m.id + ' — ' + m.label + '</div>' +
    '<div class="popup-row"><span class="popup-label">Класс</span><span class="popup-value">' + m.class + '</span></div>' +
    '<div class="popup-row"><span class="popup-label">Серийный №</span><span class="popup-value">' + m.serial + '</span></div>' +
    '<div class="popup-row"><span class="popup-label">Расположение</span><span class="popup-value">' + m.location + '</span></div>' +
    '<div class="popup-row"><span class="popup-label">Лимит</span><span class="popup-value">' + m.limit + ' л</span></div>' +
    '</div>';
  L.marker([lat, lng], {icon}).addTo(map).bindPopup(popupHtml);
});

if (bounds.length > 0) {
  map.fitBounds(bounds, {padding: [40, 40]});
}
</script>
</body>
</html>
""".trimIndent()
}
