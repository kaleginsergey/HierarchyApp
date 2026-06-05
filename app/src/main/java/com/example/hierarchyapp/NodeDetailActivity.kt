package com.example.hierarchyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class NodeDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_node_detail)

        val nodeId   = intent.getIntExtra(EXTRA_NODE_ID, -1)
        val nodeName = intent.getStringExtra(EXTRA_NODE_NAME) ?: ""

        // Back button
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

            // Open in maps on click
            btnMap.setOnClickListener {
                val geo = detail.geoData.replace(" ", "")
                val uri = Uri.parse("geo:$geo?q=$geo")
                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                }
            }
        } else {
            tvClass.text    = "—"
            tvSerial.text   = "—"
            tvLocation.text = "—"
            tvLimit.text    = "—"
            tvGeo.text      = "—"
            btnMap.isEnabled = false
        }
    }

    companion object {
        const val EXTRA_NODE_ID   = "node_id"
        const val EXTRA_NODE_NAME = "node_name"
    }
}
