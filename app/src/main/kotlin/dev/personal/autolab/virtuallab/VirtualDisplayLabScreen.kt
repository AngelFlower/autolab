package dev.personal.autolab.virtuallab

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Fase 3: renderiza una View completa (WebView o una View propia) sobre el
 * Surface del auto via VirtualDisplay + Presentation, en vez de dibujar con
 * Canvas directamente (esa es la Fase 2).
 */
class VirtualDisplayLabScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val controller = VirtualDisplayLabController(carContext)
    private val configListener: () -> Unit = { invalidate() }

    init {
        lifecycle.addObserver(controller)
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        VirtualDisplayLabConfig.addListener(configListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        VirtualDisplayLabConfig.removeListener(configListener)
    }

    override fun onGetTemplate(): Template {
        val estado = Row.Builder()
            .setTitle("Vista actual: ${VirtualDisplayLabConfig.case}")
            .addText("FPS (aprox. via OnDrawListener) en logcat, tag AutoLab")
            .build()

        val cambiarVista = Row.Builder()
            .setTitle("Cambiar vista (WebView/Custom View)")
            .setOnClickListener {
                val siguiente = if (VirtualDisplayLabConfig.case == VirtualDisplayLabConfig.Case.WEBVIEW) {
                    VirtualDisplayLabConfig.Case.CUSTOM_VIEW
                } else {
                    VirtualDisplayLabConfig.Case.WEBVIEW
                }
                VirtualDisplayLabConfig.update(siguiente)
            }
            .build()

        val ayudaAdb = Row.Builder()
            .setTitle("Control por ADB")
            .addText("am broadcast -a dev.personal.autolab.SET_VIEW_CASE --es case CUSTOM_VIEW")
            .build()

        val lista = ItemList.Builder()
            .addItem(estado)
            .addItem(cambiarVista)
            .addItem(ayudaAdb)
            .build()

        val mapController = MapController.Builder()
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .build()

        return MapWithContentTemplate.Builder()
            .setMapController(mapController)
            .setContentTemplate(
                ListTemplate.Builder()
                    .setSingleList(lista)
                    .setHeader(
                        Header.Builder()
                            .setTitle("VirtualDisplay Lab")
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
