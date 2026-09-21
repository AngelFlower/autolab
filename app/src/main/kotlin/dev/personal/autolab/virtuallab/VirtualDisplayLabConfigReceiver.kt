package dev.personal.autolab.virtuallab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "AutoLab"

/**
 * adb shell am broadcast -a dev.personal.autolab.SET_VIEW_CASE --es case CUSTOM_VIEW
 */
class VirtualDisplayLabConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val case = intent.getStringExtra("case")?.let {
            runCatching { VirtualDisplayLabConfig.Case.valueOf(it) }.getOrNull()
        }
        if (case == null) {
            Log.w(TAG, "SET_VIEW_CASE recibido sin extra 'case' valido: $intent")
            return
        }
        Log.i(TAG, "SET_VIEW_CASE case=$case")
        VirtualDisplayLabConfig.update(case)
    }
}
