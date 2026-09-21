package dev.personal.autolab.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/** Marcador de posicion para laboratorios que aun no se implementan (Fases 2 a 6). */
class LabPendingScreen(carContext: CarContext, private val nombreLab: String) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder("$nombreLab todavia no esta implementado.")
            .setHeader(
                Header.Builder()
                    .setTitle(nombreLab)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
