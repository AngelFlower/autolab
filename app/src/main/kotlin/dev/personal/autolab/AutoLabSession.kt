package dev.personal.autolab

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import dev.personal.autolab.screens.MainScreen

class AutoLabSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return MainScreen(carContext)
    }
}
