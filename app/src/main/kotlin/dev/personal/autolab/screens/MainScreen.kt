package dev.personal.autolab.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import dev.personal.autolab.surfacelab.SurfaceLabScreen

private val LABORATORIOS_PENDIENTES = listOf(
    "Fase 3: Views en VirtualDisplay",
    "Fase 4: Parked app (juego)",
    "Fase 5: Espejo con MediaProjection",
    "Fase 6: Estres termico",
)

/** Pantalla inicial de AutoLab: menu para lanzar cada laboratorio de la Fase correspondiente. */
class MainScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder()

        itemListBuilder.addItem(
            Row.Builder()
                .setTitle("Fase 2: Surface Lab (Canvas)")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(SurfaceLabScreen(carContext)) }
                .build()
        )

        LABORATORIOS_PENDIENTES.forEach { titulo ->
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(titulo)
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(LabPendingScreen(carContext, titulo))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("AutoLab")
                    .build()
            )
            .build()
    }
}
