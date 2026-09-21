package dev.personal.autolab.mirror

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Surface pasiva: no dibuja nada por si misma, solo expone su Surface para que
 * ScreenMirrorService la use como destino directo del VirtualDisplay de espejo.
 */
class MirrorSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onSurfaceGone: (() -> Unit)? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        onSurfaceReady?.invoke(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        onSurfaceGone?.invoke()
    }
}
