package dev.personal.autolab.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.io.FileWriter

private const val TAG = "AutoLab"
private const val INTERVALO_MUESTREO_MS = 5_000L

/**
 * Fase 6: registra PowerManager.currentThermalStatus + temperatura de bateria
 * cada 5s (mas cada vez que cambia el estado termico), en logcat y en un CSV,
 * para poder cruzarlo despues con los FPS de FrameStats por timestamp.
 */
class ThermalMonitor(private val context: Context, etiqueta: String) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val csvFile = File(
        context.getExternalFilesDir(null),
        "thermal_${etiqueta}_${System.currentTimeMillis()}.csv",
    ).apply {
        parentFile?.mkdirs()
        writeText("timestamp_ms,thermal_status,battery_temp_c,cargando\n")
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { estado ->
        Log.i(TAG, "Estado termico cambio a ${nombreEstado(estado)} ($estado)")
        registrar(estado)
    }

    private val muestreoPeriodico = object : Runnable {
        override fun run() {
            registrar(powerManager.currentThermalStatus)
            handler.postDelayed(this, INTERVALO_MUESTREO_MS)
        }
    }

    fun iniciar() {
        powerManager.addThermalStatusListener(thermalListener)
        handler.post(muestreoPeriodico)
    }

    fun detener() {
        powerManager.removeThermalStatusListener(thermalListener)
        handler.removeCallbacks(muestreoPeriodico)
    }

    private fun registrar(estadoTermico: Int) {
        val bateriaIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempDecimasGrado = bateriaIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = tempDecimasGrado / 10.0
        val estadoBateria = bateriaIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val cargando = estadoBateria == BatteryManager.BATTERY_STATUS_CHARGING

        Log.i(TAG, "Termico: ${nombreEstado(estadoTermico)} bateria=${tempC}C cargando=$cargando")

        runCatching {
            FileWriter(csvFile, true).use {
                it.append("${System.currentTimeMillis()},$estadoTermico,$tempC,$cargando\n")
            }
        }.onFailure { e -> Log.w(TAG, "No se pudo escribir el CSV termico", e) }
    }

    private fun nombreEstado(estado: Int): String = when (estado) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "DESCONOCIDO($estado)"
    }
}
