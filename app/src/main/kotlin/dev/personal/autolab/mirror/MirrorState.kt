package dev.personal.autolab.mirror

import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.view.Surface

/**
 * Estado compartido entre GameActivity y ScreenMirrorService (mismo proceso).
 * targetSurface es la Surface del MirrorSurfaceView de GameActivity: el destino
 * "sin copias intermedias" para el VirtualDisplay directo.
 */
object MirrorState {

    enum class CaptureMode { DIRECTO, IMAGE_READER }

    @Volatile
    var captureMode: CaptureMode = CaptureMode.DIRECTO

    @Volatile
    var targetSurface: Surface? = null

    @Volatile
    var activeProjection: MediaProjection? = null

    @Volatile
    var activeVirtualDisplay: VirtualDisplay? = null

    private val stopListeners = mutableListOf<() -> Unit>()

    fun addStopListener(listener: () -> Unit) {
        synchronized(stopListeners) { stopListeners.add(listener) }
    }

    fun removeStopListener(listener: () -> Unit) {
        synchronized(stopListeners) { stopListeners.remove(listener) }
    }

    fun notifyStopped() {
        activeProjection = null
        activeVirtualDisplay = null
        val current = synchronized(stopListeners) { stopListeners.toList() }
        current.forEach { it() }
    }
}
