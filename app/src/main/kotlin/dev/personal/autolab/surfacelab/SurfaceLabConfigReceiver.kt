package dev.personal.autolab.surfacelab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "AutoLab"

/**
 * Permite cambiar nivel/variante del Surface Lab por ADB sin tocar la UI del auto:
 *
 * adb shell am broadcast -a dev.personal.autolab.SET_CONFIG \
 *   --es level N2 --es lock HARDWARE --es loop CHOREOGRAPHER
 */
class SurfaceLabConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getStringExtra("level")?.let {
            runCatching { SurfaceLabConfig.Level.valueOf(it) }.getOrNull()
        }
        val lock = intent.getStringExtra("lock")?.let {
            runCatching { SurfaceLabConfig.LockMode.valueOf(it) }.getOrNull()
        }
        val loop = intent.getStringExtra("loop")?.let {
            runCatching { SurfaceLabConfig.LoopMode.valueOf(it) }.getOrNull()
        }

        if (level == null && lock == null && loop == null) {
            Log.w(TAG, "SET_CONFIG recibido sin extras validos: $intent")
            return
        }

        Log.i(TAG, "SET_CONFIG level=$level lock=$lock loop=$loop")
        SurfaceLabConfig.update(level = level, lockMode = lock, loopMode = loop)
    }
}
