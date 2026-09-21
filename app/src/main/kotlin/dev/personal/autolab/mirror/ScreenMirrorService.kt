package dev.personal.autolab.mirror

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface

private const val TAG = "AutoLab"
private const val CHANNEL_ID = "autolab_mirror"
private const val NOTIF_ID = 42

/**
 * Fase 7A: servicio *bound* que sostiene un unico VirtualDisplay durante toda la
 * vida de la sesion de MediaProjection. El consentimiento se pide una vez (desde
 * ProjectionSetupActivity, en el telefono). GameActivity (en el auto) se conecta
 * despues y le engancha/desengancha su propia Surface con VirtualDisplay.setSurface(),
 * sin crear un segundo VirtualDisplay (Android 14+ lo prohibe).
 */
class ScreenMirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenMirrorService = this@ScreenMirrorService
    }

    private val binder = LocalBinder()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection.Callback onStop: sesion invalidada (politica de un solo uso / pantalla bloqueada / etc.)")
            detener()
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.i(TAG, "Contenido capturado visible=$isVisible")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.i(TAG, "Contenido capturado cambio de tamano a ${width}x$height")
        }
    }

    private val virtualDisplayCallback = object : VirtualDisplay.Callback() {
        override fun onPaused() {
            Log.i(TAG, "VirtualDisplay.Callback onPaused (sin Surface destino conectada)")
        }

        override fun onResumed() {
            Log.i(TAG, "VirtualDisplay.Callback onResumed (Surface destino conectada)")
        }

        override fun onStopped() {
            Log.i(TAG, "VirtualDisplay.Callback onStopped")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AutoLab espejo de pantalla", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoLab")
            .setContentText("Proyeccion de pantalla activa")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaProjection != null) {
            Log.i(TAG, "ScreenMirrorService ya tiene una MediaProjection activa, se ignora el nuevo intent")
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "ScreenMirrorService: resultCode/data invalidos, deteniendo")
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, data)
        if (projection == null) {
            Log.w(TAG, "getMediaProjection() devolvio null, deteniendo")
            stopSelf()
            return START_NOT_STICKY
        }
        mediaProjection = projection

        // Obligatorio registrar el callback ANTES de createVirtualDisplay, o esta lanza
        // IllegalStateException (cambio de politica documentado para Android 14+).
        projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

        crearVirtualDisplayUnico(projection)
        return START_STICKY
    }

    /**
     * Crea el (unico) VirtualDisplay de toda la sesion, sin Surface destino todavia
     * (arranca "pausado"). GameActivity lo conecta despues con conectarSurface().
     */
    private fun crearVirtualDisplayUnico(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val vd = projection.createVirtualDisplay(
            "AutoLabMirror",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null,
            virtualDisplayCallback,
            Handler(Looper.getMainLooper()),
        )
        virtualDisplay = vd
        if (vd == null) {
            Log.w(TAG, "createVirtualDisplay() devolvio null, deteniendo")
            stopSelf()
            return
        }
        Log.i(
            TAG,
            "VirtualDisplay unico creado sin Surface: ${metrics.widthPixels}x${metrics.heightPixels}" +
                "@${metrics.densityDpi}dpi (id=${vd.display?.displayId})",
        )
    }

    /** Llamado por GameActivity cuando su SurfaceView tiene una Surface valida. */
    fun conectarSurface(surface: Surface, width: Int, height: Int, dpi: Int) {
        val vd = virtualDisplay
        if (vd == null) {
            Log.w(TAG, "conectarSurface() sin VirtualDisplay activo")
            return
        }
        Log.i(TAG, "Conectando Surface del auto: resize(${width}x${height}@${dpi}dpi) + setSurface()")
        vd.resize(width, height, dpi)
        vd.setSurface(surface)
    }

    /** Llamado por GameActivity cuando su Surface se destruye (pantalla oculta, etc). */
    fun desconectarSurface() {
        Log.i(TAG, "Desconectando Surface del auto (setSurface(null))")
        virtualDisplay?.setSurface(null)
    }

    fun sesionActiva(): Boolean = mediaProjection != null

    private fun detener() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        stopSelf()
    }

    override fun onDestroy() {
        detener()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }
}
