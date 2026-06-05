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
        webView.settings.builtInZoomControls = false
        webView.webViewClient = WebViewClient()

        // Collect marker data
        data class Marker(val id: Int, val lat: Double, val lng: Double,
                          val label: String, val nodeClass: String,
                          val serial: String, val location: String, val limit: String)

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

        val markers = DataRepository.nodeDetails.values.mapNotNull { d ->
            val parts = d.geoData.split(",").map { it.trim() }
            val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            Marker(d.nodeId, lat, lng, findLabel(d.nodeId),
                d.nodeClass, d.serialNumber, d.location, d.consumptionLimit)
        }

        // Build JS array
        val jsMarkers = markers.joinToString(",\n") { m ->
            fun esc(s: String) = s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
            """{"id":${m.id},"lat":${m.lat},"lng":${m.lng},"label":"${esc(m.label)}","cls":"${esc(m.nodeClass)}","serial":"${esc(m.serial)}","location":"${esc(m.location)}","limit":"${esc(m.limit)}"}"""
        }

        webView.loadDataWithBaseURL(
            "about:blank",
            buildHtml(jsMarkers),
            "text/html", "UTF-8", null
        )
    }

    private fun buildHtml(markersJson: String) = """<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent;}
html,body{width:100%;height:100%;overflow:hidden;background:#e8f0fe;}
#canvas{display:block;touch-action:none;}
#popup{
  display:none;position:fixed;bottom:0;left:0;right:0;
  background:#fff;border-radius:16px 16px 0 0;
  box-shadow:0 -4px 20px rgba(0,0,0,0.15);
  padding:16px;z-index:100;font-family:sans-serif;
  max-height:60vh;overflow-y:auto;
}
.popup-drag{width:40px;height:4px;background:#ddd;border-radius:2px;margin:0 auto 12px;}
.popup-title{font-size:14px;font-weight:700;color:#1565C0;margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #eee;}
.row{display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid #f5f5f5;}
.row:last-child{border-bottom:none;}
.lbl{font-size:12px;color:#888;}
.val{font-size:12px;font-weight:600;color:#222;text-align:right;max-width:60%;}
#closeBtn{position:absolute;top:12px;right:14px;font-size:22px;color:#aaa;cursor:pointer;line-height:1;}
#hint{position:fixed;top:12px;left:50%;transform:translateX(-50%);
  background:rgba(0,0,0,0.55);color:#fff;font-size:12px;font-family:sans-serif;
  padding:6px 14px;border-radius:20px;pointer-events:none;white-space:nowrap;z-index:99;}
</style>
</head>
<body>
<canvas id="canvas"></canvas>
<div id="hint">Нажмите на маркер для деталей</div>
<div id="popup">
  <div class="popup-drag"></div>
  <span id="closeBtn">✕</span>
  <div class="popup-title" id="pt"></div>
  <div class="row"><span class="lbl">№ узла</span><span class="val" id="pi"></span></div>
  <div class="row"><span class="lbl">Класс</span><span class="val" id="pc"></span></div>
  <div class="row"><span class="lbl">Серийный №</span><span class="val" id="ps"></span></div>
  <div class="row"><span class="lbl">Расположение</span><span class="val" id="pl"></span></div>
  <div class="row"><span class="lbl">Лимит потребления</span><span class="val" id="plim"></span></div>
</div>
<script>
const MARKERS = [$markersJson];

const canvas = document.getElementById('canvas');
const ctx = canvas.getContext('2d');

// --- viewport ---
let vpW, vpH;
function resize(){
  vpW = canvas.width  = window.innerWidth;
  vpH = canvas.height = window.innerHeight;
  draw();
}

// --- projection state ---
let scale = 1;          // pixels per degree lon
let offX  = 0;          // canvas x of lng=0
let offY  = 0;          // canvas y of lat=0  (y grows down, lat grows up)

const R = 6378137;
function mercY(lat){ return Math.log(Math.tan(Math.PI/4 + lat*Math.PI/360)) * R; }

// fit all markers into view
function initView(){
  if(!MARKERS.length) return;
  const lats = MARKERS.map(m=>m.lat), lngs = MARKERS.map(m=>m.lng);
  const minLat=Math.min(...lats), maxLat=Math.max(...lats);
  const minLng=Math.min(...lngs), maxLng=Math.max(...lngs);

  const pad = 80;
  const dLng = maxLng - minLng || 0.01;
  const mMinY = mercY(minLat), mMaxY = mercY(maxLat);
  const dMerc = mMaxY - mMinY || 100;

  const scaleX = (vpW - pad*2) / dLng;
  const scaleY = (vpH - pad*2) / dMerc;
  scale = Math.min(scaleX, scaleY) * (180/Math.PI/R);   // convert merc→deg scale

  const midLng = (minLng+maxLng)/2;
  const midMerc = (mMinY+mMaxY)/2;
  offX = vpW/2 - lngToX(midLng);
  offY = vpH/2 - mercToY(midMerc);
}

function lngToX(lng){ return lng * (scale * R * Math.PI/180) + offX; }
function mercToY(my){ return -my * scale + offY; }
function latToY(lat){ return mercToY(mercY(lat)); }

function xToLng(x){ return (x - offX) / (scale * R * Math.PI/180); }
function yToLat(y){ const my = -(y - offY)/scale; return Math.atan(Math.exp(my/R))*360/Math.PI - 90; }

// --- draw ---
const TILE_BG    = '#dce8f5';
const TILE_ROAD  = '#ffffff';
const MARKER_CLR = '#16A34A';
const MARKER_SEL = '#1565C0';
const MARKER_R   = 14;

let selectedId = -1;

function draw(){
  ctx.clearRect(0,0,vpW,vpH);

  // background
  ctx.fillStyle = TILE_BG;
  ctx.fillRect(0,0,vpW,vpH);

  // simple grid (simulated streets)
  drawGrid();

  // markers
  MARKERS.forEach(m=>{
    const x = lngToX(m.lng);
    const y = latToY(m.lat);
    const sel = m.id===selectedId;
    const r = sel ? MARKER_R+3 : MARKER_R;

    // shadow
    ctx.shadowColor='rgba(0,0,0,0.25)';
    ctx.shadowBlur=sel?10:6;

    // circle
    ctx.beginPath();
    ctx.arc(x,y,r,0,Math.PI*2);
    ctx.fillStyle = sel ? MARKER_SEL : MARKER_CLR;
    ctx.fill();
    ctx.shadowBlur=0;
    ctx.strokeStyle='#fff';
    ctx.lineWidth=2.5;
    ctx.stroke();

    // number
    ctx.fillStyle='#fff';
    ctx.font='bold '+(sel?13:11)+'px sans-serif';
    ctx.textAlign='center';
    ctx.textBaseline='middle';
    ctx.fillText(m.id, x, y);

    // label below
    ctx.fillStyle='#333';
    ctx.font=(sel?12:11)+'px sans-serif';
    ctx.textAlign='center';
    ctx.textBaseline='top';
    const short = m.cls;
    ctx.fillText(short, x, y+r+4);
  });
}

function drawGrid(){
  ctx.strokeStyle='rgba(255,255,255,0.5)';
  ctx.lineWidth=1.5;
  const step = 0.002;
  if(!MARKERS.length) return;
  const lngs=MARKERS.map(m=>m.lng), lats=MARKERS.map(m=>m.lat);
  const minLng=Math.min(...lngs)-0.01, maxLng=Math.max(...lngs)+0.01;
  const minLat=Math.min(...lats)-0.01, maxLat=Math.max(...lats)+0.01;
  for(let lng=minLng; lng<=maxLng; lng+=step){
    const x=lngToX(lng);
    ctx.beginPath(); ctx.moveTo(x,0); ctx.lineTo(x,vpH); ctx.stroke();
  }
  for(let lat=minLat; lat<=maxLat; lat+=step){
    const y=latToY(lat);
    ctx.beginPath(); ctx.moveTo(0,y); ctx.lineTo(vpW,y); ctx.stroke();
  }
}

// --- pan & zoom ---
let dragStart=null, dragOff=null, pinchDist=null;

function dist2(t){ return Math.hypot(t[0].clientX-t[1].clientX, t[0].clientY-t[1].clientY); }

canvas.addEventListener('touchstart', e=>{
  e.preventDefault();
  if(e.touches.length===1){
    dragStart={x:e.touches[0].clientX, y:e.touches[0].clientY};
    dragOff={x:offX, y:offY};
    pinchDist=null;
  } else if(e.touches.length===2){
    dragStart=null;
    pinchDist=dist2(e.touches);
  }
},{passive:false});

canvas.addEventListener('touchmove', e=>{
  e.preventDefault();
  if(e.touches.length===1 && dragStart){
    offX = dragOff.x + (e.touches[0].clientX - dragStart.x);
    offY = dragOff.y + (e.touches[0].clientY - dragStart.y);
    draw();
  } else if(e.touches.length===2 && pinchDist!==null){
    const nd=dist2(e.touches);
    const cx=(e.touches[0].clientX+e.touches[1].clientX)/2;
    const cy=(e.touches[0].clientY+e.touches[1].clientY)/2;
    const factor=nd/pinchDist;
    const lngC=xToLng(cx), latC=yToLat(cy);
    scale*=factor;
    offX=cx-lngToX_noOff(lngC);
    offY=cy-latToY_noOff(latC);
    pinchDist=nd;
    draw();
  }
},{passive:false});

function lngToX_noOff(lng){ return lng*(scale*R*Math.PI/180); }
function latToY_noOff(lat){ return -mercY(lat)*scale; }

canvas.addEventListener('touchend', e=>{
  if(e.changedTouches.length===1 && dragStart){
    const dx=e.changedTouches[0].clientX-dragStart.x;
    const dy=e.changedTouches[0].clientY-dragStart.y;
    if(Math.hypot(dx,dy)<10){
      // tap — check marker hit
      const tx=e.changedTouches[0].clientX, ty=e.changedTouches[0].clientY;
      let hit=null;
      MARKERS.forEach(m=>{
        const x=lngToX(m.lng), y=latToY(m.lat);
        if(Math.hypot(tx-x,ty-y)<(MARKER_R+8)) hit=m;
      });
      if(hit){ showPopup(hit); selectedId=hit.id; }
      else { hidePopup(); selectedId=-1; }
      draw();
    }
    dragStart=null;
  }
  pinchDist=null;
});

// mouse support (for emulator)
let mouseDrag=null, mouseOff=null;
canvas.addEventListener('mousedown',e=>{
  mouseDrag={x:e.clientX,y:e.clientY}; mouseOff={x:offX,y:offY};
});
canvas.addEventListener('mousemove',e=>{
  if(!mouseDrag) return;
  offX=mouseOff.x+(e.clientX-mouseDrag.x);
  offY=mouseOff.y+(e.clientY-mouseDrag.y);
  draw();
});
canvas.addEventListener('mouseup',e=>{
  if(!mouseDrag) return;
  const dx=e.clientX-mouseDrag.x, dy=e.clientY-mouseDrag.y;
  if(Math.hypot(dx,dy)<6){
    let hit=null;
    MARKERS.forEach(m=>{
      const x=lngToX(m.lng),y=latToY(m.lat);
      if(Math.hypot(e.clientX-x,e.clientY-y)<(MARKER_R+8)) hit=m;
    });
    if(hit){ showPopup(hit); selectedId=hit.id; }
    else { hidePopup(); selectedId=-1; }
    draw();
  }
  mouseDrag=null;
});
canvas.addEventListener('wheel',e=>{
  const factor=e.deltaY<0?1.15:0.87;
  const lngC=xToLng(e.clientX), latC=yToLat(e.clientY);
  scale*=factor;
  offX=e.clientX-lngToX_noOff(lngC);
  offY=e.clientY-latToY_noOff(latC);
  draw();
},{passive:true});

// --- popup ---
function showPopup(m){
  document.getElementById('pt').textContent='№'+m.id+' — '+m.label;
  document.getElementById('pi').textContent=m.id;
  document.getElementById('pc').textContent=m.cls;
  document.getElementById('ps').textContent=m.serial;
  document.getElementById('pl').textContent=m.location;
  document.getElementById('plim').textContent=m.limit+' л';
  document.getElementById('popup').style.display='block';
  document.getElementById('hint').style.display='none';
}
function hidePopup(){
  document.getElementById('popup').style.display='none';
}
document.getElementById('closeBtn').addEventListener('click',()=>{
  hidePopup(); selectedId=-1; draw();
});

// init
window.addEventListener('resize', resize);
resize();
initView();
draw();

// hide hint after 3s
setTimeout(()=>{ const h=document.getElementById('hint'); if(h) h.style.display='none'; }, 3000);
</script>
</body>
</html>"""
}
