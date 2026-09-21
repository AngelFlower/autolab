package dev.personal.autolab.surfacelab

import android.util.Log
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

private const val TAG = "AutoLab"

/**
 * Fase 2: dibuja directamente sobre el Surface del auto (categoria POI via
 * MapWithContentTemplate). El nivel de carga y la variante de dibujo se
 * controlan desde esta lista o por ADB (ver SurfaceLabConfigReceiver).
 */
class SurfaceLabScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val controller = SurfaceLabController(carContext)
    private val configListener: () -> Unit = { invalidate() }

    init {
        lifecycle.addObserver(controller)
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        SurfaceLabConfig.addListener(configListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        SurfaceLabConfig.removeListener(configListener)
    }

    override fun onGetTemplate(): Template {
        val estado = Row.Builder()
            .setTitle("Nivel ${SurfaceLabConfig.level} · ${SurfaceLabConfig.lockMode} · ${SurfaceLabConfig.loopMode}")
            .addText("FPS y percentiles en el overlay del mapa y en logcat (tag AutoLab)")
            .build()

        val cambiarNivel = Row.Builder()
            .setTitle("Cambiar nivel (N0-N3)")
            .setOnClickListener {
                val niveles = SurfaceLabConfig.Level.entries
                val siguiente = niveles[(SurfaceLabConfig.level.ordinal + 1) % niveles.size]
                SurfaceLabConfig.update(level = siguiente)
            }
            .build()

        val cambiarLock = Row.Builder()
            .setTitle("Cambiar lock (software/hardware)")
            .setOnClickListener {
                val modos = SurfaceLabConfig.LockMode.entries
                val siguiente = modos[(SurfaceLabConfig.lockMode.ordinal + 1) % modos.size]
                SurfaceLabConfig.update(lockMode = siguiente)
            }
            .build()

        val cambiarLoop = Row.Builder()
            .setTitle("Cambiar loop (libre/Choreographer)")
            .setOnClickListener {
                val modos = SurfaceLabConfig.LoopMode.entries
                val siguiente = modos[(SurfaceLabConfig.loopMode.ordinal + 1) % modos.size]
                SurfaceLabConfig.update(loopMode = siguiente)
            }
            .build()

        val ayudaAdb = Row.Builder()
            .setTitle("Control por ADB")
            .addText("am broadcast -a dev.personal.autolab.SET_CONFIG --es level N2 --es lock HARDWARE --es loop CHOREOGRAPHER")
            .build()

        val lista = ItemList.Builder()
            .addItem(estado)
            .addItem(cambiarNivel)
            .addItem(cambiarLock)
            .addItem(cambiarLoop)
            .addItem(ayudaAdb)
            .build()

        // El host de Android Auto solo reenvia gestos al SurfaceCallback si el usuario
        // entra explicitamente en "modo pan" (boton dedicado); evita interacciones
        // accidentales con el mapa mientras se conduce.
        val mapController = MapController.Builder()
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { enPan -> Log.i(TAG, "Modo pan cambio a $enPan") }
            .build()

        return MapWithContentTemplate.Builder()
            .setMapController(mapController)
            .setContentTemplate(
                ListTemplate.Builder()
                    .setSingleList(lista)
                    .setHeader(
                        Header.Builder()
                            .setTitle("Surface Lab")
                            .setStartHeaderAction(Action.BACK)
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
