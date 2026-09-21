package dev.personal.autolab.parkedgame

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import dev.personal.autolab.mirror.MirrorSurfaceView
import dev.personal.autolab.mirror.ScreenMirrorService
import dev.personal.autolab.thermal.ThermalMonitor

private const val TAG = "AutoLab"

/**
 * Activity "parked" (fuera de la Car App Library) que se abre desde el
 * lanzador del auto via la categoria CAR_LAUNCHER. Es el visor de la
 * proyeccion: se conecta (bind, sin AUTO_CREATE) a ScreenMirrorService, que
 * ya debe estar corriendo porque el consentimiento se pidio antes desde
 * ProjectionSetupActivity en el telefono. En cuanto su MirrorSurfaceView
 * tiene una Surface valida se la pasa al servicio -- nunca crea su propia
 * MediaProjection ni VirtualDisplay, y nunca vuelve a pedir consentimiento.
 */
class GameActivity : Activity() {

    private lateinit var mirrorView: MirrorSurfaceView
    private lateinit var esperando: TextView
    private lateinit var thermalMonitor: ThermalMonitor

    private var servicioEspejo: ScreenMirrorService? = null

    private val conexionServicio = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val servicio = (service as ScreenMirrorService.LocalBinder).getService()
            servicioEspejo = servicio
            Log.i(TAG, "Conectado a ScreenMirrorService (activa=${servicio.sesionActiva()})")
            actualizarEstado()
            if (servicio.sesionActiva()) {
                mirrorView.surfaceActual?.let { (surface, w, h, dpi) ->
                    servicio.conectarSurface(surface, w, h, dpi)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "ScreenMirrorService se desconecto inesperadamente")
            servicioEspejo = null
            actualizarEstado()
        }
    }

    private val stopMirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "STOP_MIRROR recibido: deteniendo la sesion de proyeccion")
            servicioEspejo?.detenerSesion()
            actualizarEstado()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        mirrorView = MirrorSurfaceView(this).apply {
            onSurfaceReady = { surface, w, h, dpi ->
                Log.i(TAG, "MirrorSurfaceView lista: ${w}x${h}@${dpi}dpi (displayId=${windowManager.defaultDisplay.displayId})")
                servicioEspejo?.conectarSurface(surface, w, h, dpi)
            }
            onSurfaceGone = {
                Log.i(TAG, "MirrorSurfaceView destruida")
                servicioEspejo?.desconectarSurface()
            }
        }
        root.addView(
            mirrorView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        esperando = TextView(this).apply {
            text = "Conecta la proyección desde tu teléfono"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(
            esperando,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        setContentView(root)

        thermalMonitor = ThermalMonitor(this, "projection_view")
        thermalMonitor.iniciar()

        registrarReceiver(stopMirrorReceiver, "dev.personal.autolab.STOP_MIRROR")

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

    private fun actualizarEstado() {
        esperando.visibility = if (servicioEspejo?.sesionActiva() == true) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        thermalMonitor.detener()
        unregisterReceiver(stopMirrorReceiver)
        if (servicioEspejo != null) {
            unbindService(conexionServicio)
        }
        super.onDestroy()
    }
}
