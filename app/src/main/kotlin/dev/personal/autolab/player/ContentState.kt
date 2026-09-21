package dev.personal.autolab.player

import android.net.Uri

/**
 * Fase 8: en vez de espejar todo el telefono, GameActivity puede mostrar
 * contenido propio directamente en la pantalla del auto (ya corre ahi):
 * un WebView (para YouTube u otras webs) o un reproductor de video local
 * (elegido en el telefono desde la galeria/Google Fotos via el selector
 * de fotos del sistema). Se elige desde ProjectionSetupActivity.
 */
object ContentState {

    enum class Mode { ESPEJO, WEB, VIDEO }

    @Volatile
    var mode: Mode = Mode.ESPEJO
        private set

    @Volatile
    var url: String? = null
        private set

    @Volatile
    var videoUri: Uri? = null
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) {
        synchronized(listeners) { listeners.add(l) }
    }

    fun removeListener(l: () -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    fun mostrarEspejo() {
        mode = Mode.ESPEJO
        notificar()
    }

    fun mostrarWeb(nuevaUrl: String) {
        mode = Mode.WEB
        url = nuevaUrl
        notificar()
    }

    fun mostrarVideo(uri: Uri) {
        mode = Mode.VIDEO
        videoUri = uri
        notificar()
    }

    private fun notificar() {
        val actuales = synchronized(listeners) { listeners.toList() }
        actuales.forEach { it() }
    }
}
