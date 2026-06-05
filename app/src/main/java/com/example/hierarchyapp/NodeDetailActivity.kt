package com.example.hierarchyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NodeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_detail)

        val nodeId   = intent.getIntExtra(EXTRA_NODE_ID, -1)
        val nodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: ""

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val tvNodeName = findViewById<TextView>(R.id.tvNodeName)
        val tvNodeId   = findViewById<TextView>(R.id.tvNodeId)
        val tvClass    = findViewById<TextView>(R.id.tvClass)
        val tvSerial   = findViewById<TextView>(R.id.tvSerial)
        val tvLocation = findViewById<TextView>(R.id.tvLocation)
        val tvLimit    = findViewById<TextView>(R.id.tvLimit)
        val tvGeo      = findViewById<TextView>(R.id.tvGeo)
        val btnMap     = findViewById<Button>(R.id.btnOpenMap)

        tvNodeName.text = nodeName
        tvNodeId.text   = nodeId.toString()

        val detail = DataRepository.nodeDetails[nodeId]
        if (detail != null) {
            tvClass.text    = detail.nodeClass
            tvSerial.text   = detail.serialNumber
            tvLocation.text = detail.location
            tvLimit.text    = "${detail.consumptionLimit} л"
            tvGeo.text      = detail.geoData

            btnMap.setOnClickListener {
                val parts = detail.geoData.split(",").map { it.trim() }
                val lat = parts.getOrNull(0) ?: return@setOnClickListener
                val lng = parts.getOrNull(1) ?: return@setOnClickListener
                val label = Uri.encode(nodeName)
                // geo: URI — opens any maps app; falls back to Google Maps web
                try {
                    val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                    startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                } catch (e: Exception) {
                    // Fallback: open Google Maps in browser
                    val webUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                }
            }
        } else {
            tvClass.text     = "—"
            tvSerial.text    = "—"
            tvLocation.text  = "—"
            tvLimit.text     = "—"
            tvGeo.text       = "—"
            btnMap.isEnabled = false
        }
    }

    companion object {
        const val EXTRA_NODE_ID   = "node_id"
        const val EXTRA_NODE_NAME = "node_name"
    }
}
