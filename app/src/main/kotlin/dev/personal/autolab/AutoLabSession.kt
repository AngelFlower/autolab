package dev.personal.autolab

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.personal.autolab.screens.MainScreen
import dev.personal.autolab.thermal.ThermalMonitor

class AutoLabSession : Session(), DefaultLifecycleObserver {

    private lateinit var thermalMonitor: ThermalMonitor

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        thermalMonitor = ThermalMonitor(carContext, "session")
        thermalMonitor.iniciar()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        thermalMonitor.detener()
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainScreen(carContext)
    }
}
