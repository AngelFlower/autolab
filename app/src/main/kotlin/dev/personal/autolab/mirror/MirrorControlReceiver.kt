package dev.personal.autolab.mirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "AutoLab"

/**
 * Control por ADB de la Fase 5 que no requiere una Activity en primer plano:
 *
 * adb shell am broadcast -a dev.personal.autolab.SET_MIRROR_MODE --es mode IMAGE_READER
 * adb shell am broadcast -a dev.personal.autolab.STOP_MIRROR
 *
 * Iniciar el espejo si requiere una Activity (dev.personal.autolab.START_MIRROR,
 * manejado dentro de GameActivity) porque el consentimiento del usuario solo se
 * puede pedir desde un contexto de Activity.
 */
class MirrorControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "dev.personal.autolab.SET_MIRROR_MODE" -> {
                val modo = intent.getStringExtra("mode")?.let {
                    runCatching { MirrorState.CaptureMode.valueOf(it) }.getOrNull()
                }
                if (modo != null) {
                    Log.i(TAG, "SET_MIRROR_MODE modo=$modo")
                    MirrorState.captureMode = modo
                } else {
                    Log.w(TAG, "SET_MIRROR_MODE sin extra 'mode' valido: $intent")
                }
            }

            "dev.personal.autolab.STOP_MIRROR" -> {
                Log.i(TAG, "STOP_MIRROR recibido")
                MirrorState.activeProjection?.stop()
            }
        }
    }
}
