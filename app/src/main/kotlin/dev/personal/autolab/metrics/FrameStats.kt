package dev.personal.autolab.metrics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import kotlin.math.roundToInt

private const val TAG = "AutoLab"
private const val JANK_UMBRAL_MS = 33.0
private const val VENTANA_NANOS = 1_000_000_000L

/**
 * Mide el intervalo entre frames y agrega FPS/percentiles cada segundo.
 *
 * Se debe llamar a [onFramePosted] justo despues de la llamada que bloquea por vsync
 * (p.ej. unlockCanvasAndPost), porque ese bloqueo es lo que revela el techo real de FPS.
 * Cada instancia escribe su propio CSV para poder comparar niveles/variantes (Fase 2).
 */
class FrameStats(context: Context, etiqueta: String) {

    data class Snapshot(
        val fps: Double,
        val avgMs: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val framesJank: Int,
        val frameCount: Int,
    )

    @Volatile
    var ultimoSnapshot: Snapshot? = null
        private set

    private val duracionesNanos = mutableListOf<Long>()
    private var inicioVentanaNanos = 0L
    private var ultimoFrameNanos = 0L

    private val csvFile: File = File(
        context.getExternalFilesDir(null),
        "frame_stats_${etiqueta}_${System.currentTimeMillis()}.csv"
    ).apply {
        parentFile?.mkdirs()
        writeText("timestamp_ms,fps,avg_ms,p50_ms,p95_ms,p99_ms,frames_jank,frame_count\n")
    }

    fun onFramePosted() {
        val ahora = System.nanoTime()
        if (ultimoFrameNanos != 0L) {
            duracionesNanos.add(ahora - ultimoFrameNanos)
        }
        ultimoFrameNanos = ahora

        if (inicioVentanaNanos == 0L) {
            inicioVentanaNanos = ahora
        } else if (ahora - inicioVentanaNanos >= VENTANA_NANOS) {
            volcarVentana()
            inicioVentanaNanos = ahora
        }
    }

    private fun volcarVentana() {
        if (duracionesNanos.isEmpty()) return

        val ms = duracionesNanos.map { it / 1_000_000.0 }.sorted()
        val avg = ms.average()
        val p50 = percentil(ms, 50.0)
        val p95 = percentil(ms, 95.0)
        val p99 = percentil(ms, 99.0)
        val jank = ms.count { it > JANK_UMBRAL_MS }

        val snapshot = Snapshot(
            fps = ms.size.toDouble(),
            avgMs = avg,
            p50Ms = p50,
            p95Ms = p95,
            p99Ms = p99,
            framesJank = jank,
            frameCount = ms.size,
        )
        ultimoSnapshot = snapshot

        Log.i(
            TAG,
            "fps=${snapshot.fps} avg=${"%.2f".format(avg)}ms p50=${"%.2f".format(p50)}ms " +
                "p95=${"%.2f".format(p95)}ms p99=${"%.2f".format(p99)}ms jank=$jank/${ms.size}"
        )

        runCatching {
            FileWriter(csvFile, true).use { writer ->
                writer.append(
                    "${System.currentTimeMillis()},${snapshot.fps},$avg,$p50,$p95,$p99,$jank,${ms.size}\n"
                )
            }
        }.onFailure { e -> Log.w(TAG, "No se pudo escribir el CSV de metricas", e) }

        duracionesNanos.clear()
    }

    private fun percentil(msOrdenados: List<Double>, p: Double): Double {
        if (msOrdenados.isEmpty()) return 0.0
        val indice = ((p / 100.0) * (msOrdenados.size - 1)).roundToInt()
        return msOrdenados[indice.coerceIn(0, msOrdenados.size - 1)]
    }
}
