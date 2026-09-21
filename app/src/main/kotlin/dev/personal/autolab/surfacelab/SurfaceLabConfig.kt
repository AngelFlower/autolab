package dev.personal.autolab.surfacelab

/**
 * Estado compartido del laboratorio de Surface. Se controla desde la UI del auto
 * (SurfaceLabScreen) o por ADB (ver SurfaceLabConfigReceiver), y lo consume
 * SurfaceLabController para saber que dibujar y como.
 */
object SurfaceLabConfig {

    enum class Level { N0, N1, N2, N3 }
    enum class LockMode { SOFTWARE, HARDWARE }
    enum class LoopMode { FREE, CHOREOGRAPHER }

    @Volatile
    var level: Level = Level.N0
        private set

    @Volatile
    var lockMode: LockMode = LockMode.SOFTWARE
        private set

    @Volatile
    var loopMode: LoopMode = LoopMode.FREE
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun update(level: Level? = null, lockMode: LockMode? = null, loopMode: LoopMode? = null) {
        level?.let { this.level = it }
        lockMode?.let { this.lockMode = it }
        loopMode?.let { this.loopMode = it }
        val current = synchronized(listeners) { listeners.toList() }
        current.forEach { it() }
    }

    fun etiqueta(): String = "${level}_${lockMode}_${loopMode}"
}
