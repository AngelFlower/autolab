package dev.personal.autolab.virtuallab

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

private const val TAG = "AutoLab"

private val HTML_ANIMACION = """
<!DOCTYPE html>
<html>
<head><style>html,body{margin:0;background:#000;overflow:hidden}</style></head>
<body>
<canvas id="c"></canvas>
<script>
const c = document.getElementById('c');
const ctx = c.getContext('2d');
function resize() { c.width = window.innerWidth; c.height = window.innerHeight; }
resize();
window.addEventListener('resize', resize);

const N = 500;
const figuras = [];
for (let i = 0; i < N; i++) {
  figuras.push({
    x: Math.random() * c.width, y: Math.random() * c.height,
    vx: (Math.random() - 0.5) * 8, vy: (Math.random() - 0.5) * 8,
    r: 4 + Math.random() * 8, hue: Math.random() * 360,
  });
}

function frame() {
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, c.width, c.height);
  for (const f of figuras) {
    f.x += f.vx; f.y += f.vy;
    if (f.x < 0 || f.x > c.width) f.vx *= -1;
    if (f.y < 0 || f.y > c.height) f.vy *= -1;
    ctx.beginPath();
    ctx.fillStyle = 'hsl(' + f.hue + ', 80%, 60%)';
    ctx.arc(f.x, f.y, f.r, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.fillStyle = '#0f0';
  ctx.font = '20px sans-serif';
  ctx.fillText('AutoLab VirtualDisplay - WebView', 16, c.height - 16);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);

document.addEventListener('click', function (e) {
  console.log('CLICK_RECIBIDO x=' + e.clientX + ' y=' + e.clientY);
});
</script>
</body>
</html>
"""

/** WebView con una animacion de Canvas 2D pesada (500 figuras via requestAnimationFrame). */
fun crearAnimatedWebView(context: Context): WebView {
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "WebView console: ${message.message()}")
                return true
            }
        }
        loadDataWithBaseURL(null, HTML_ANIMACION, "text/html", "UTF-8", null)
    }
}
