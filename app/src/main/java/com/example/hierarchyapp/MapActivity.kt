package com.example.hierarchyapp

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class MapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        // Replace WebView with native Canvas view
        val container = findViewById<FrameLayout>(R.id.mapContainer)
        val markers = buildMarkers()
        val mapView = NativeMapView(this, markers) { marker ->
            showPopup(marker)
        }
        container.addView(mapView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    data class Marker(
        val id: Int, val lat: Double, val lng: Double,
        val label: String, val cls: String,
        val serial: String, val location: String, val limit: String
    )

    private fun buildMarkers(): List<Marker> {
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
        return DataRepository.nodeDetails.values.mapNotNull { d ->
            val parts = d.geoData.split(",").map { it.trim() }
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            Marker(d.nodeId, lat, lng, findLabel(d.nodeId),
                d.nodeClass, d.serialNumber, d.location, d.consumptionLimit)
        }
    }

    private fun showPopup(m: Marker) {
        val popup = findViewById<LinearLayout>(R.id.mapPopup)
        popup.visibility = View.VISIBLE
        findViewById<TextView>(R.id.popupTitle).text = "№${m.id} — ${m.label}"
        findViewById<TextView>(R.id.popupClass).text = m.cls
        findViewById<TextView>(R.id.popupSerial).text = m.serial
        findViewById<TextView>(R.id.popupLocation).text = m.location
        findViewById<TextView>(R.id.popupLimit).text = "${m.limit} л"
        findViewById<TextView>(R.id.popupClose).setOnClickListener {
            popup.visibility = View.GONE
        }
    }
}

class NativeMapView(
    context: android.content.Context,
    private val markers: List<MapActivity.Marker>,
    private val onMarkerClick: (MapActivity.Marker) -> Unit
) : View(context) {

    private val paintBg     = Paint().apply { color = Color.parseColor("#DCE8F5") }
    private val paintGrid   = Paint().apply { color = Color.parseColor("#FFFFFF"); alpha = 120; strokeWidth = 1.5f; isAntiAlias = true }
    private val paintMarker = Paint().apply { color = Color.parseColor("#16A34A"); isAntiAlias = true }
    private val paintSel    = Paint().apply { color = Color.parseColor("#1565C0"); isAntiAlias = true }
    private val paintBorder = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f; isAntiAlias = true }
    private val paintText   = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
    private val paintLabel  = Paint().apply { color = Color.parseColor("#333333"); textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val MARKER_R = 36f   // px — larger hit target

    // Projection state
    private var scale = 1.0
    private var offX  = 0f
    private var offY  = 0f
    private var selectedId = -1

    // Touch state
    private var lastTouch = PointF()
    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var ptr1 = PointF(); private var ptr2 = PointF()
    private var pinching = false
    private var lastPinchDist = 0f

    private val R_EARTH = 6378137.0

    private fun mercY(lat: Double) =
        ln(tan(Math.PI / 4 + lat * Math.PI / 360)) * R_EARTH

    private fun lngToX(lng: Double) = (lng * scale * R_EARTH * Math.PI / 180 + offX).toFloat()
    private fun latToY(lat: Double) = (-mercY(lat) * scale + offY).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (markers.isEmpty()) return
        val pad = 80f
        val lats = markers.map { it.lat }
        val lngs = markers.map { it.lng }
        val minLat = lats.min(); val maxLat = lats.max()
        val minLng = lngs.min(); val maxLng = lngs.max()
        val dLng = (maxLng - minLng).coerceAtLeast(0.005)
        val mMin = mercY(minLat); val mMax = mercY(maxLat)
        val dMerc = (mMax - mMin).coerceAtLeast(100.0)
        val sx = (w - pad * 2) / dLng
        val sy = (h - pad * 2) / dMerc
        val rawScale = minOf(sx, sy)
        scale = rawScale * 180 / Math.PI / R_EARTH
        val midLng = (minLng + maxLng) / 2
        val midMerc = (mMin + mMax) / 2
        offX = w / 2f - (midLng * scale * R_EARTH * Math.PI / 180).toFloat()
        offY = h / 2f - (-midMerc * scale).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, paintBg)
        drawGrid(canvas)
        paintText.textSize = MARKER_R * 0.65f
        paintLabel.textSize = MARKER_R * 0.55f
        markers.forEach { m ->
            val x = lngToX(m.lng); val y = latToY(m.lat)
            val sel = m.id == selectedId
            val r = if (sel) MARKER_R * 1.25f else MARKER_R
            canvas.drawCircle(x, y, r, if (sel) paintSel else paintMarker)
            canvas.drawCircle(x, y, r, paintBorder)
            val textY = y - (paintText.descent() + paintText.ascent()) / 2
            canvas.drawText(m.id.toString(), x, textY, paintText)
            canvas.drawText(m.cls, x, y + r + paintLabel.textSize * 1.2f, paintLabel)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        if (markers.isEmpty()) return
        val lngs = markers.map { it.lng }; val lats = markers.map { it.lat }
        val step = 0.003
        val minLng = lngs.min() - 0.015; val maxLng = lngs.max() + 0.015
        val minLat = lats.min() - 0.015; val maxLat = lats.max() + 0.015
        var lng = minLng
        while (lng <= maxLng) {
            val x = lngToX(lng)
            canvas.drawLine(x, 0f, x, height.toFloat(), paintGrid)
            lng += step
        }
        var lat = minLat
        while (lat <= maxLat) {
            val y = latToY(lat)
            canvas.drawLine(0f, y, width.toFloat(), y, paintGrid)
            lat += step
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                lastTouch.set(event.x, event.y)
                isDragging = false; pinching = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    ptr1.set(event.getX(0), event.getY(0))
                    ptr2.set(event.getX(1), event.getY(1))
                    lastPinchDist = hypot((ptr1.x - ptr2.x).toDouble(), (ptr1.y - ptr2.y).toDouble()).toFloat()
                    pinching = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && event.pointerCount >= 2) {
                    val nx1 = PointF(event.getX(0), event.getY(0))
                    val nx2 = PointF(event.getX(1), event.getY(1))
                    val nd = hypot((nx1.x - nx2.x).toDouble(), (nx1.y - nx2.y).toDouble()).toFloat()
                    val cx = (nx1.x + nx2.x) / 2; val cy = (nx1.y + nx2.y) / 2
                    val factor = nd / lastPinchDist
                    val lngC = (cx - offX) / (scale * R_EARTH * Math.PI / 180)
                    val mercC = -(cy - offY) / scale
                    scale *= factor
                    offX = cx - (lngC * scale * R_EARTH * Math.PI / 180).toFloat()
                    offY = cy - (-mercC * scale).toFloat()
                    lastPinchDist = nd
                    invalidate()
                } else if (!pinching) {
                    val dx = event.x - lastTouch.x; val dy = event.y - lastTouch.y
                    if (hypot(dx.toDouble(), dy.toDouble()) > 8) isDragging = true
                    if (isDragging) { offX += dx; offY += dy; invalidate() }
                    lastTouch.set(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !pinching) {
                    val tx = event.x; val ty = event.y
                    val hit = markers.firstOrNull { m ->
                        val mx = lngToX(m.lng); val my = latToY(m.lat)
                        hypot((tx - mx).toDouble(), (ty - my).toDouble()) < MARKER_R + 20
                    }
                    if (hit != null) { selectedId = hit.id; onMarkerClick(hit) }
                    else selectedId = -1
                    invalidate()
                }
                isDragging = false; pinching = false
            }
            MotionEvent.ACTION_POINTER_UP -> { pinching = false }
        }
        return true
    }
}
