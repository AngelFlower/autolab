package dev.personal.autolab.virtuallab

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.personal.autolab.metrics.FrameStats

private const val TAG = "AutoLab"

/**
 * Renderiza una View completa (WebView o una View propia) sobre el Surface del auto
 * usando VirtualDisplay + Presentation, en vez de dibujar directamente con Canvas
 * como en la Fase 2. El FPS se mide contando pasadas de dibujo de la View
 * (ViewTreeObserver.OnDrawListener), no un contador de buffers del compositor:
 * es una aproximacion, documentada como tal en RESULTS.md.
 */
class VirtualDisplayLabController(private val carContext: CarContext) : DefaultLifecycleObserver {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var contentView: View? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private var frameStats: FrameStats? = null
    private var surfaceContainerActual: SurfaceContainer? = null

    private val configListener: () -> Unit = { reconstruir() }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.i(
                TAG,
                "Surface disponible (VirtualDisplay lab): ancho=${surfaceContainer.width} " +
                    "alto=${surfaceContainer.height} dpi=${surfaceContainer.dpi}"
            )
            surfaceContainerActual = surfaceContainer
            crearVirtualDisplay(surfaceContainer)
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            Log.i(TAG, "Surface destruido (VirtualDisplay lab)")
            destruir()
            surfaceContainerActual = null
        }

        override fun onClick(x: Float, y: Float) {
            Log.i(TAG, "Gesto onClick x=$x y=$y - reenviando como MotionEvent sintetico a la View")
            reenviarClick(x, y)
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        VirtualDisplayLabConfig.addListener(configListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        VirtualDisplayLabConfig.removeListener(configListener)
        destruir()
    }

    private fun reconstruir() {
        val sc = surfaceContainerActual ?: return
        crearVirtualDisplay(sc)
    }

    private fun crearVirtualDisplay(surfaceContainer: SurfaceContainer) {
        destruir()

        val displayManager = carContext.getSystemService(DisplayManager::class.java)
        val vd = displayManager.createVirtualDisplay(
            "AutoLabVirtualDisplay",
            surfaceContainer.width,
            surfaceContainer.height,
            surfaceContainer.dpi,
            surfaceContainer.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        )
        virtualDisplay = vd

        val nuevaPresentation = Presentation(carContext, vd.display)
        presentation = nuevaPresentation

        val etiqueta = VirtualDisplayLabConfig.case.name
        frameStats = FrameStats(carContext, "vd_$etiqueta")

        val view: View = when (VirtualDisplayLabConfig.case) {
            VirtualDisplayLabConfig.Case.WEBVIEW -> crearAnimatedWebView(carContext)
            VirtualDisplayLabConfig.Case.CUSTOM_VIEW -> CustomAnimatedView(carContext)
        }
        contentView = view

        nuevaPresentation.setContentView(view)
        nuevaPresentation.show()

        val listener = ViewTreeObserver.OnDrawListener { frameStats?.onFramePosted() }
        drawListener = listener
        view.viewTreeObserver.addOnDrawListener(listener)
    }

    private fun reenviarClick(x: Float, y: Float) {
        val view = contentView ?: return
        val ahora = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(ahora, ahora, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(ahora, ahora + 10, MotionEvent.ACTION_UP, x, y, 0)
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun destruir() {
        val view = contentView
        val listener = drawListener
        if (view != null && listener != null) {
            val vto = view.viewTreeObserver
            if (vto.isAlive) vto.removeOnDrawListener(listener)
        }
        drawListener = null
        contentView = null

        presentation?.dismiss()
        presentation = null

        virtualDisplay?.release()
        virtualDisplay = null
    }
}
