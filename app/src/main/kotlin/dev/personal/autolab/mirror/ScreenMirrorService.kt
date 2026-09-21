package dev.personal.autolab.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import dev.personal.autolab.metrics.FrameStats

private const val TAG = "AutoLab"
private const val CHANNEL_ID = "autolab_mirror"
private const val NOTIF_ID = 42

/**
 * Servicio en primer plano (tipo mediaProjection, obligatorio desde Android 14) que
 * sostiene la MediaProjection y crea el VirtualDisplay de espejo, en modo DIRECTO
 * (Surface del MirrorSurfaceView como salida, sin copias) o IMAGE_READER (para
 * comparar el costo de la copia manual via Bitmap).
 */
class ScreenMirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var frameStats: FrameStats? = null

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

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AutoLab espejo de pantalla", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoLab")
            .setContentText("Espejo de pantalla activo")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
            ?: android.app.Activity.RESULT_CANCELED
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)

        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
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

        crearVirtualDisplay(projection)
        return START_NOT_STICKY
    }

    private fun crearVirtualDisplay(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        frameStats = FrameStats(this, "mirror_${MirrorState.captureMode}")

        when (MirrorState.captureMode) {
            MirrorState.CaptureMode.DIRECTO -> {
                val surface = MirrorState.targetSurface
                if (surface == null) {
                    Log.w(TAG, "Modo DIRECTO sin targetSurface, deteniendo")
                    stopSelf()
                    return
                }
                val vd = projection.createVirtualDisplay(
                    "AutoLabMirrorDirecto",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null,
                )
                MirrorState.activeVirtualDisplay = vd
                Log.i(TAG, "Espejo DIRECTO activo: ${width}x${height}@${dpi}dpi hacia la Surface del auto")
            }

            MirrorState.CaptureMode.IMAGE_READER -> {
                val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                imageReader = reader
                reader.setOnImageAvailableListener({ r ->
                    val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        dibujarImagenEnSurface(image, width, height)
                        frameStats?.onFramePosted()
                    } finally {
                        image.close()
                    }
                }, Handler(Looper.getMainLooper()))

                val vd = projection.createVirtualDisplay(
                    "AutoLabMirrorImageReader",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface, null, null,
                )
                MirrorState.activeVirtualDisplay = vd
                Log.i(TAG, "Espejo IMAGE_READER activo: ${width}x${height}@${dpi}dpi con copia manual via Bitmap")
            }
        }

        MirrorState.activeProjection = projection
    }

    private fun dibujarImagenEnSurface(image: android.media.Image, width: Int, height: Int) {
        val surface = MirrorState.targetSurface ?: return
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride

        val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)

        val canvas = surface.lockCanvas(null) ?: run { bitmap.recycle(); return }
        try {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
            bitmap.recycle()
        }
    }

    private fun detener() {
        MirrorState.activeVirtualDisplay?.release()
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        MirrorState.notifyStopped()
        stopSelf()
    }

    override fun onDestroy() {
        detener()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
    }
}
