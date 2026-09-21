package dev.personal.autolab.parkedgame

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

private const val TAG = "AutoLab"

/**
 * Fase 4: Activity "parked" (fuera de la Car App Library) que se abre desde el
 * lanzador del auto via la categoria CAR_LAUNCHER. Muestra info del Display y
 * del tipo de CarConnection, y corre un juego minimo en SurfaceView.
 */
class GameActivity : Activity() {

    private val carConnection by lazy { CarConnection(this) }
    private var infoText: TextView? = null

    private val carConnectionObserver = Observer<Int> { tipo ->
        val nombre = when (tipo) {
            CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "NOT_CONNECTED"
            CarConnection.CONNECTION_TYPE_NATIVE -> "NATIVE (Android Automotive OS)"
            CarConnection.CONNECTION_TYPE_PROJECTION -> "PROJECTION (Android Auto)"
            else -> "desconocido ($tipo)"
        }
        Log.i(TAG, "CarConnection tipo: $nombre")
        infoText?.append("\nCarConnection: $nombre")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        val gameView = GameSurfaceView(this)
        root.addView(
            gameView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        val info = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(24, 24, 24, 24)
            textSize = 12f
        }
        infoText = info
        root.addView(
            info,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )

        setContentView(root)

        mostrarInfoDisplay(info)
        carConnection.type.observeForever(carConnectionObserver)
    }

    @Suppress("DEPRECATION")
    private fun mostrarInfoDisplay(info: TextView) {
        val display: Display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)

        val modos = display.supportedModes.joinToString("\n") {
            "  ${it.physicalWidth}x${it.physicalHeight} @ ${"%.1f".format(it.refreshRate)}Hz"
        }

        val texto = buildString {
            appendLine("displayId: ${display.displayId}")
            appendLine("tamano: ${metrics.widthPixels}x${metrics.heightPixels} px")
            appendLine("densidad: ${metrics.densityDpi} dpi")
            appendLine("refreshRate actual: ${"%.1f".format(display.refreshRate)} Hz")
            appendLine("modos soportados:")
            append(modos)
        }
        Log.i(TAG, "Info de Display:\n$texto")
        info.text = texto
    }

    override fun onDestroy() {
        carConnection.type.removeObserver(carConnectionObserver)
        super.onDestroy()
    }
}
