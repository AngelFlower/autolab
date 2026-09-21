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
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import dev.personal.autolab.mirror.MirrorSurfaceView
import dev.personal.autolab.mirror.ScreenMirrorService
import dev.personal.autolab.player.ContentState
import dev.personal.autolab.thermal.ThermalMonitor

private const val TAG = "AutoLab"

/**
 * Activity "parked" (fuera de la Car App Library) que se abre desde el
 * lanzador del auto via la categoria CAR_LAUNCHER. Es el visor de contenido
 * en el auto, con tres modos (ContentState.Mode):
 *
 * - ESPEJO: se conecta (bind, sin AUTO_CREATE) a ScreenMirrorService, que ya
 *   debe estar corriendo porque el consentimiento se pidio antes desde
 *   ProjectionSetupActivity en el telefono. Refleja TODA la pantalla del
 *   telefono (o una sola app, segun lo elegido en el dialogo del sistema).
 * - WEB: un WebView propio carga una URL directo en la pantalla del auto --
 *   sin espejar nada del telefono, sin barra de navegador ni notificaciones.
 * - VIDEO: reproduce un video local (elegido en el telefono, por ejemplo
 *   desde la galeria o Google Fotos) directo en la pantalla del auto.
 *
 * GameActivity ya corre en el display del auto, asi que WEB y VIDEO no
 * necesitan VirtualDisplay ni Presentation -- son vistas normales agregadas
 * a su propio layout.
 */
class GameActivity : Activity() {

    private lateinit var mirrorView: MirrorSurfaceView
    private lateinit var webView: WebView
    private lateinit var videoView: VideoView
    private lateinit var esperando: TextView
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var thermalMonitor: ThermalMonitor
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var servicioEspejo: ScreenMirrorService? = null

    private val conexionServicio = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val servicio = (service as ScreenMirrorService.LocalBinder).getService()
            servicioEspejo = servicio
            Log.i(TAG, "Conectado a ScreenMirrorService (activa=${servicio.sesionActiva()})")
            aplicarModo()
            if (servicio.sesionActiva()) {
                mirrorView.surfaceActual?.let { (surface, w, h, dpi) ->
                    servicio.conectarSurface(surface, w, h, dpi)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "ScreenMirrorService se desconecto inesperadamente")
            servicioEspejo = null
            aplicarModo()
        }
    }

    private val stopMirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "STOP_MIRROR recibido: deteniendo la sesion de proyeccion")
            servicioEspejo?.detenerSesion()
            aplicarModo()
        }
    }

    private val contentListener: () -> Unit = { runOnUiThread { aplicarModo() } }

    private val openChromeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            abrirChromeEnElAuto()
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
        root.addView(mirrorView, matchParent())

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Sin WebViewClient propio, Android deja que enlaces como los de YouTube
            // se abran en la app nativa en vez de quedarse en este WebView.
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i(TAG, "WebView console: ${message.message()}")
                    return true
                }

                // Video HTML5 en pantalla completa (lo que usa el reproductor movil
                // de YouTube): el WebView por si solo no tiene "pantalla completa",
                // hay que darle un contenedor propio a ocupar toda la Activity.
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenContainer.childCount > 0) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreenCallback = callback
                    fullscreenContainer.addView(view, matchParent())
                    fullscreenContainer.visibility = View.VISIBLE
                    Log.i(TAG, "WebView entro a pantalla completa (HTML5)")
                }

                override fun onHideCustomView() {
                    fullscreenContainer.removeAllViews()
                    fullscreenContainer.visibility = View.GONE
                    fullscreenCallback?.onCustomViewHidden()
                    fullscreenCallback = null
                    Log.i(TAG, "WebView salio de pantalla completa (HTML5)")
                }
            }
            visibility = View.GONE
        }
        root.addView(webView, matchParent())

        videoView = VideoView(this).apply {
            visibility = View.GONE
            setOnPreparedListener { it.start() }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "VideoView error what=$what extra=$extra")
                true
            }
        }
        root.addView(videoView, matchParent())

        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(fullscreenContainer, matchParent())

        esperando = TextView(this).apply {
            text = "Conecta la proyección desde tu teléfono"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(esperando, matchParent())

        setContentView(root)

        thermalMonitor = ThermalMonitor(this, "projection_view")
        thermalMonitor.iniciar()

        registrarReceiver(stopMirrorReceiver, "dev.personal.autolab.STOP_MIRROR")
        registrarReceiver(openChromeReceiver, "dev.personal.autolab.OPEN_CHROME")
        ContentState.addListener(contentListener)

        // Sin BIND_AUTO_CREATE: si el servicio no esta corriendo (no se hizo el
        // consentimiento en ProjectionSetupActivity todavia), esto no lo arranca.
        val conectado = bindService(Intent(this, ScreenMirrorService::class.java), conexionServicio, 0)
        Log.i(TAG, "Intento de bind a ScreenMirrorService: $conectado")

        aplicarModo()
    }

    private fun matchParent() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun registrarReceiver(receiver: BroadcastReceiver, accion: String) {
        val filter = IntentFilter(accion)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun aplicarModo() {
        when (ContentState.mode) {
            ContentState.Mode.ESPEJO -> {
                webView.visibility = View.GONE
                videoView.stopPlayback()
                videoView.visibility = View.GONE
                mirrorView.visibility = View.VISIBLE
                esperando.visibility = if (servicioEspejo?.sesionActiva() == true) View.GONE else View.VISIBLE
            }

            ContentState.Mode.WEB -> {
                mirrorView.visibility = View.GONE
                videoView.stopPlayback()
                videoView.visibility = View.GONE
                esperando.visibility = View.GONE
                webView.visibility = View.VISIBLE
                ContentState.url?.let {
                    Log.i(TAG, "WebView cargando $it")
                    webView.loadUrl(it)
                }
            }

            ContentState.Mode.VIDEO -> {
                mirrorView.visibility = View.GONE
                webView.visibility = View.GONE
                esperando.visibility = View.GONE
                videoView.visibility = View.VISIBLE
                ContentState.videoUri?.let { uri ->
                    Log.i(TAG, "VideoView reproduciendo $uri")
                    videoView.setMediaController(MediaController(this))
                    videoView.setVideoURI(uri)
                }
            }
        }
    }

    /**
     * No se puede embeber Chrome dentro de esta Activity (limite duro de Android:
     * cada app corre en su propio proceso). Esto lanza Chrome como su propia
     * Activity, sin pedirle un display distinto -- si el sistema respeta el
     * comportamiento normal, deberia quedarse en el display actual (el del auto).
     */
    private fun abrirChromeEnElAuto() {
        val displayIdAntes = windowManager.defaultDisplay.displayId
        Log.i(TAG, "Intentando abrir Chrome sin especificar display (displayId actual=$displayIdAntes)")
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://m.youtube.com")).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir Chrome", e)
        }
    }

    override fun onDestroy() {
        thermalMonitor.detener()
        unregisterReceiver(stopMirrorReceiver)
        unregisterReceiver(openChromeReceiver)
        ContentState.removeListener(contentListener)
        if (servicioEspejo != null) {
            unbindService(conexionServicio)
        }
        super.onDestroy()
    }
}
