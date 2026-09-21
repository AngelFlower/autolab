package dev.personal.autolab.virtuallab

/** Estado compartido de la Fase 3: que View se presenta sobre el VirtualDisplay. */
object VirtualDisplayLabConfig {

    enum class Case { WEBVIEW, CUSTOM_VIEW }

    @Volatile
    var case: Case = Case.WEBVIEW
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun update(case: Case) {
        this.case = case
        val current = synchronized(listeners) { listeners.toList() }
        current.forEach { it() }
    }
}
