package dev.personal.autolab.parkedgame

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import dev.personal.autolab.mirror.MirrorSurfaceView
import dev.personal.autolab.mirror.ScreenMirrorService
import dev.personal.autolab.thermal.ThermalMonitor

private const val TAG = "AutoLab"

/**
 * Fase 4: Activity "parked" (fuera de la Car App Library) que se abre desde el
 * lanzador del auto via la categoria CAR_LAUNCHER. Muestra info del Display y
 * del tipo de CarConnection, y corre un juego minimo en SurfaceView.
 *
 * Fase 7A: se conecta (bind, sin AUTO_CREATE) a ScreenMirrorService, que ya debe
 * estar corriendo porque el consentimiento se pidio antes desde
 * ProjectionSetupActivity en el telefono. Cuando su MirrorSurfaceView tiene una
 * Surface valida, se la pasa al servicio con conectarSurface() -- nunca crea su
 * propia MediaProjection ni VirtualDisplay.
 */
class GameActivity : Activity() {

    private val carConnection by lazy { CarConnection(this) }
    private var infoText: TextView? = null

    private lateinit var gameView: GameSurfaceView
    private lateinit var mirrorView: MirrorSurfaceView
    private lateinit var thermalMonitor: ThermalMonitor

    private var servicioEspejo: ScreenMirrorService? = null

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

    private val conexionServicio = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "Conectado a ScreenMirrorService (activa=${(service as ScreenMirrorService.LocalBinder).getService().sesionActiva()})")
            servicioEspejo = service.getService()
            if (mirrorView.visibility == View.VISIBLE) {
                mirrorView.surfaceActual?.let { (surface, w, h, dpi) ->
                    servicioEspejo?.conectarSurface(surface, w, h, dpi)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "ScreenMirrorService se desconecto inesperadamente")
            servicioEspejo = null
        }
    }

    private val startMirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mostrarEspejo()
        }
    }

    private val stopMirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ocultarEspejo()
        }
    }

    private val secureTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mostrarPruebaFlagSecure()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        gameView = GameSurfaceView(this)
        root.addView(
            gameView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        mirrorView = MirrorSurfaceView(this).apply {
            visibility = View.GONE
            onSurfaceReady = { surface, w, h, dpi ->
                Log.i(TAG, "MirrorSurfaceView lista: ${w}x${h}@${dpi}dpi (displayId actual=${windowManager.defaultDisplay.displayId})")
                servicioEspejo?.conectarSurface(surface, w, h, dpi)
            }
            onSurfaceGone = {
                Log.i(TAG, "MirrorSurfaceView destruida (displayId actual=${windowManager.defaultDisplay.displayId})")
                servicioEspejo?.desconectarSurface()
            }
        }
        root.addView(
            mirrorView,
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

        thermalMonitor = ThermalMonitor(this, "parked_game")
        thermalMonitor.iniciar()

        registrarReceiver(startMirrorReceiver, "dev.personal.autolab.START_MIRROR")
        registrarReceiver(stopMirrorReceiver, "dev.personal.autolab.STOP_MIRROR")
        registrarReceiver(secureTestReceiver, "dev.personal.autolab.SHOW_SECURE_TEST")

        // Sin BIND_AUTO_CREATE: si el servicio no esta corriendo (no se hizo el
        // consentimiento en ProjectionSetupActivity todavia), esto no lo arranca.
        val conectado = bindService(Intent(this, ScreenMirrorService::class.java), conexionServicio, 0)
        Log.i(TAG, "Intento de bind a ScreenMirrorService: $conectado")
    }

    private fun registrarReceiver(receiver: BroadcastReceiver, accion: String) {
        val filter = IntentFilter(accion)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun mostrarEspejo() {
        if (servicioEspejo == null) {
            Log.w(TAG, "No hay ScreenMirrorService conectado -- concede el consentimiento primero en ProjectionSetupActivity")
            return
        }
        gameView.visibility = View.GONE
        mirrorView.visibility = View.VISIBLE
    }

    private fun ocultarEspejo() {
        mirrorView.visibility = View.GONE
        gameView.visibility = View.VISIBLE
    }

    /** Ventana con FLAG_SECURE para verificar que MediaProjection la capture en negro. */
    private fun mostrarPruebaFlagSecure() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val texto = TextView(this).apply {
            text = "CONTENIDO SECRETO (FLAG_SECURE)"
            setBackgroundColor(Color.MAGENTA)
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        dialog.setContentView(
            texto,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        dialog.show()
        Log.i(TAG, "Mostrando ventana FLAG_SECURE de prueba por 6s")
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) dialog.dismiss()
        }, 6000)
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
        Log.i(TAG, "Info de Display (onCreate): $texto")
        info.text = texto
    }

    override fun onDestroy() {
        carConnection.type.removeObserver(carConnectionObserver)
        thermalMonitor.detener()
        unregisterReceiver(startMirrorReceiver)
        unregisterReceiver(stopMirrorReceiver)
        unregisterReceiver(secureTestReceiver)
        if (servicioEspejo != null) {
            unbindService(conexionServicio)
        }
        super.onDestroy()
    }
}
