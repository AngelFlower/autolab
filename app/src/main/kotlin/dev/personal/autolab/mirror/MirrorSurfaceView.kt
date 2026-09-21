package dev.personal.autolab.mirror

import android.content.Context
import android.util.DisplayMetrics
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Surface pasiva: no dibuja nada por si misma, solo expone su Surface (y las
 * dimensiones/dpi reales) para que ScreenMirrorService la use como destino del
 * VirtualDisplay de espejo via setSurface()/resize().
 */
class MirrorSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    data class SurfaceInfo(val surface: Surface, val width: Int, val height: Int, val dpi: Int)

    var onSurfaceReady: ((Surface, Int, Int, Int) -> Unit)? = null
    var onSurfaceGone: (() -> Unit)? = null

    /** Ultima Surface entregada, por si el bind al servicio llega despues que esta. */
    var surfaceActual: SurfaceInfo? = null
        private set

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(metrics)
        val dpi = if (metrics.densityDpi != 0) metrics.densityDpi else resources.displayMetrics.densityDpi
        val info = SurfaceInfo(holder.surface, width, height, dpi)
        surfaceActual = info
        onSurfaceReady?.invoke(info.surface, info.width, info.height, info.dpi)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceActual = null
        onSurfaceGone?.invoke()
    }
}
