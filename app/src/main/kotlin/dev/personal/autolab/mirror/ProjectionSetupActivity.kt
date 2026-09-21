package dev.personal.autolab.mirror

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

private const val TAG = "AutoLab"
private const val REQUEST_MEDIA_PROJECTION = 2001

/**
 * Fase 7A: Activity normal del telefono (icono propio en el launcher del
 * telefono, taskAffinity propio -- ver manifest -- para no compartir tarea
 * con GameActivity). Pide el consentimiento de MediaProjection UNA vez y
 * arranca ScreenMirrorService. El destino real (la Surface del auto) se
 * conecta despues, desde GameActivity, sin pedir consentimiento de nuevo.
 */
class ProjectionSetupActivity : Activity() {

    private lateinit var estado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titulo = TextView(this).apply {
            text = "AutoLab - Configurar proyeccion"
            setTextColor(Color.WHITE)
            textSize = 20f
        }

        estado = TextView(this).apply {
            text = "Sin iniciar."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }

        val boton = Button(this).apply {
            text = "Iniciar proyeccion"
            setOnClickListener { pedirConsentimiento() }
        }

        root.addView(titulo)
        root.addView(estado)
        root.addView(boton)
        setContentView(root)
    }

    private fun pedirConsentimiento() {
        Log.i(TAG, "Pidiendo consentimiento de MediaProjection desde ProjectionSetupActivity (displayId=${windowManager.defaultDisplay.displayId})")
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return

        if (resultCode == RESULT_OK && data != null) {
            Log.i(TAG, "Consentimiento otorgado, iniciando ScreenMirrorService")
            val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
                putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenMirrorService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)
            estado.text = "Proyeccion activa. Conecta el DHU y abre AutoLab desde su launcher."
        } else {
            Log.w(TAG, "Consentimiento denegado o cancelado")
            estado.text = "Consentimiento denegado o cancelado."
        }
    }
}
