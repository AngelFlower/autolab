package dev.personal.autolab.surfacelab

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.personal.autolab.metrics.FrameStats

private const val TAG = "AutoLab"

/**
 * Registra el SurfaceCallback ante el host, ejecuta el bucle de dibujo segun
 * SurfaceLabConfig (nivel + lockCanvas/lockHardwareCanvas + libre/Choreographer),
 * y mide cada frame con FrameStats.
 */
class SurfaceLabController(private val carContext: CarContext) : DefaultLifecycleObserver {

    private var surface: Surface? = null
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null

    private var renderThread: Thread? = null
    @Volatile private var loopActivo = false

    private var frameStats: FrameStats? = null
    private var renderer: LevelRenderer? = null
    private var etiquetaActual: String = ""

    private val overlayPaint = Paint().apply {
        color = Color.GREEN
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val configListener: () -> Unit = { reiniciarBucle() }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val choreographerCallback = object : Choreographer.FrameCallback {
        private var inicioNanos = 0L
        fun reset() {
            inicioNanos = System.nanoTime()
        }
        override fun doFrame(frameTimeNanos: Long) {
            if (!loopActivo) return
            val s = surface
            if (s == null || !s.isValid) return
            dibujarFrame(s, inicioNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.i(
                TAG,
                "Surface disponible: ancho=${surfaceContainer.width} alto=${surfaceContainer.height} " +
                    "dpi=${surfaceContainer.dpi}"
            )
            surface = surfaceContainer.surface
            reiniciarBucle()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            Log.i(TAG, "Area visible cambio a $visibleArea (area estable actual: $stableArea)")
            this@SurfaceLabController.visibleArea = visibleArea
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            Log.i(TAG, "Area estable cambio a $stableArea (area visible actual: $visibleArea)")
            this@SurfaceLabController.stableArea = stableArea
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            Log.i(TAG, "Surface destruido")
            detenerBucle()
            surface = null
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            Log.i(TAG, "Gesto onScroll distanceX=$distanceX distanceY=$distanceY")
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            Log.i(TAG, "Gesto onFling velocityX=$velocityX velocityY=$velocityY")
        }

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            Log.i(TAG, "Gesto onScale focusX=$focusX focusY=$focusY scaleFactor=$scaleFactor")
        }

        override fun onClick(x: Float, y: Float) {
            Log.i(TAG, "Gesto onClick x=$x y=$y")
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        SurfaceLabConfig.addListener(configListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        SurfaceLabConfig.removeListener(configListener)
        detenerBucle()
    }

    private fun reiniciarBucle() {
        detenerBucle()
        val s = surface ?: return
        if (!s.isValid) return

        etiquetaActual = SurfaceLabConfig.etiqueta()
        frameStats = FrameStats(carContext, etiquetaActual)
        renderer = crearRenderer(SurfaceLabConfig.level)
        loopActivo = true

        when (SurfaceLabConfig.loopMode) {
            SurfaceLabConfig.LoopMode.FREE -> iniciarBucleLibre(s)
            SurfaceLabConfig.LoopMode.CHOREOGRAPHER -> iniciarBucleChoreographer(s)
        }
    }

    private fun detenerBucle() {
        loopActivo = false
        renderThread?.join(500)
        renderThread = null
        mainHandler.post { Choreographer.getInstance().removeFrameCallback(choreographerCallback) }
    }

    private fun iniciarBucleLibre(surface: Surface) {
        val inicioNanos = System.nanoTime()
        renderThread = Thread({
            while (loopActivo && surface.isValid) {
                dibujarFrame(surface, inicioNanos)
            }
        }, "AutoLabSurfaceLoop").apply { start() }
    }

    private fun iniciarBucleChoreographer(surface: Surface) {
        choreographerCallback.reset()
        mainHandler.post { Choreographer.getInstance().postFrameCallback(choreographerCallback) }
    }

    private fun dibujarFrame(surface: Surface, inicioNanos: Long) {
        val canvas: Canvas = try {
            if (SurfaceLabConfig.lockMode == SurfaceLabConfig.LockMode.HARDWARE) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo bloquear el canvas ($etiquetaActual)", e)
            return
        }
        try {
            val elapsedMs = (System.nanoTime() - inicioNanos) / 1_000_000
            renderer?.draw(canvas, elapsedMs)
            dibujarOverlay(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
            frameStats?.onFramePosted()
        }
    }

    private fun dibujarOverlay(canvas: Canvas) {
        val snapshot = frameStats?.ultimoSnapshot ?: return
        canvas.drawText(
            "AutoLab $etiquetaActual fps=${snapshot.fps.toInt()} p95=${"%.1f".format(snapshot.p95Ms)}ms",
            24f, 48f, overlayPaint,
        )
    }
}
