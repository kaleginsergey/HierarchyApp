package com.example.hierarchyapp

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*

class MapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val container = findViewById<FrameLayout>(R.id.mapContainer)
        val diagText  = findViewById<TextView>(R.id.diagText)

        try {
            val markers = buildMarkers()
            val diag = buildString {
                appendLine("=== ДИАГНОСТИКА ===")
                appendLine("Маркеров найдено: ${markers.size}")
                markers.forEach { m ->
                    appendLine("  #${m.id}: lat=${m.lat} lng=${m.lng} cls=${m.cls}")
                }
                appendLine("Размер экрана: ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
                appendLine("Density: ${resources.displayMetrics.density}")
            }
            Log.d("MapActivity", diag)
            diagText.text = diag

            if (markers.isEmpty()) {
                diagText.text = "ОШИБКА: маркеры не найдены!\n\n$diag"
                return
            }

            val mapView = NativeMapView(this, markers) { marker ->
                showPopup(marker)
            }
            container.addView(mapView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            // Hide diag after map loads
            diagText.visibility = View.GONE

        } catch (e: Exception) {
            val msg = "ИСКЛЮЧЕНИЕ: ${e.javaClass.simpleName}\n${e.message}\n\n${e.stackTraceToString().take(600)}"
            Log.e("MapActivity", msg, e)
            diagText.text = msg
        }
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
        findViewById<TextView>(R.id.popupTitle).text    = "№${m.id} — ${m.label}"
        findViewById<TextView>(R.id.popupClass).text    = m.cls
        findViewById<TextView>(R.id.popupSerial).text   = m.serial
        findViewById<TextView>(R.id.popupLocation).text = m.location
        findViewById<TextView>(R.id.popupLimit).text    = "${m.limit} л"
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
    private val paintGrid   = Paint().apply { color = Color.WHITE; alpha = 100; strokeWidth = 1.5f; isAntiAlias = true }
    private val paintGreen  = Paint().apply { color = Color.parseColor("#16A34A"); isAntiAlias = true }
    private val paintBlue   = Paint().apply { color = Color.parseColor("#1565C0"); isAntiAlias = true }
    private val paintBorder = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val paintNum    = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
    private val paintLbl    = Paint().apply { color = Color.parseColor("#222222"); textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private val RADIUS = 36f
    private var scale = 1.0; private var offX = 0f; private var offY = 0f
    private var selectedId = -1
    private var initDone = false

    private var dragX = 0f; private var dragY = 0f
    private var offX0 = 0f; private var offY0 = 0f
    private var dragging = false; private var downX = 0f; private var downY = 0f
    private var pinching = false; private var pinchDist0 = 0f
    private var pin1 = PointF(); private var pin2 = PointF()

    private val RE = 6378137.0
    private fun mercY(lat: Double) = ln(tan(PI / 4 + lat * PI / 360)) * RE
    private fun lx(lng: Double) = (lng * scale * RE * PI / 180 + offX).toFloat()
    private fun ly(lat: Double) = (-mercY(lat) * scale + offY).toFloat()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && !initDone) { fitMarkers(w.toFloat(), h.toFloat()); initDone = true }
    }

    private fun fitMarkers(w: Float, h: Float) {
        if (markers.isEmpty()) return
        val pad = 80f
        val minLat = markers.minOf { it.lat }; val maxLat = markers.maxOf { it.lat }
        val minLng = markers.minOf { it.lng }; val maxLng = markers.maxOf { it.lng }
        val dLng  = (maxLng - minLng).coerceAtLeast(0.005)
        val mMin  = mercY(minLat); val mMax = mercY(maxLat)
        val dMerc = (mMax - mMin).coerceAtLeast(100.0)
        val raw   = minOf((w - pad * 2) / dLng, (h - pad * 2) / dMerc)
        scale = raw * 180 / PI / RE
        offX  = w / 2f - ((minLng + maxLng) / 2 * scale * RE * PI / 180).toFloat()
        offY  = h / 2f - (-(mMin + mMax) / 2 * scale).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, paintBg)
        if (markers.isEmpty()) {
            paintLbl.textSize = 40f
            canvas.drawText("Нет данных", w / 2, h / 2, paintLbl)
            return
        }
        drawGrid(canvas, w, h)
        paintNum.textSize = RADIUS * 0.65f
        paintLbl.textSize = RADIUS * 0.55f
        markers.forEach { m ->
            val x = lx(m.lng); val y = ly(m.lat)
            val sel = m.id == selectedId
            val r   = if (sel) RADIUS * 1.3f else RADIUS
            canvas.drawCircle(x, y, r, if (sel) paintBlue else paintGreen)
            canvas.drawCircle(x, y, r, paintBorder)
            val ty = y - (paintNum.descent() + paintNum.ascent()) / 2
            canvas.drawText(m.id.toString(), x, ty, paintNum)
            canvas.drawText(m.cls, x, y + r + paintLbl.textSize * 1.2f, paintLbl)
        }
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        val step = 0.003
        val minLng = markers.minOf { it.lng } - 0.02
        val maxLng = markers.maxOf { it.lng } + 0.02
        val minLat = markers.minOf { it.lat } - 0.02
        val maxLat = markers.maxOf { it.lat } + 0.02
        var lng = minLng; while (lng <= maxLng) { val x = lx(lng); canvas.drawLine(x, 0f, x, h, paintGrid); lng += step }
        var lat = minLat; while (lat <= maxLat) { val y = ly(lat); canvas.drawLine(0f, y, w, y, paintGrid); lat += step }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                dragX = e.x; dragY = e.y
                offX0 = offX; offY0 = offY
                dragging = false; pinching = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 2) {
                pin1.set(e.getX(0), e.getY(0)); pin2.set(e.getX(1), e.getY(1))
                pinchDist0 = hypot((pin1.x - pin2.x).toDouble(), (pin1.y - pin2.y).toDouble()).toFloat()
                pinching = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching && e.pointerCount >= 2) {
                    val nd = hypot((e.getX(0)-e.getX(1)).toDouble(), (e.getY(0)-e.getY(1)).toDouble()).toFloat()
                    val cx = (e.getX(0)+e.getX(1))/2; val cy = (e.getY(0)+e.getY(1))/2
                    val lngC = (cx - offX) / (scale * RE * PI / 180)
                    val mercC = -(cy - offY) / scale
                    scale *= nd / pinchDist0
                    offX = cx - (lngC * scale * RE * PI / 180).toFloat()
                    offY = cy - (-mercC * scale).toFloat()
                    pinchDist0 = nd; invalidate()
                } else if (!pinching) {
                    if (hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > 8) dragging = true
                    if (dragging) { offX = offX0 + (e.x - downX); offY = offY0 + (e.y - downY); invalidate() }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !pinching) {
                    val hit = markers.firstOrNull { m ->
                        hypot((e.x - lx(m.lng)).toDouble(), (e.y - ly(m.lat)).toDouble()) < RADIUS + 20
                    }
                    selectedId = hit?.id ?: -1
                    if (hit != null) onMarkerClick(hit)
                    invalidate()
                }
                dragging = false; pinching = false
            }
            MotionEvent.ACTION_POINTER_UP -> pinching = false
        }
        return true
    }
}
