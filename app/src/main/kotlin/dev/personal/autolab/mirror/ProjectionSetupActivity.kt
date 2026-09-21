package dev.personal.autolab.mirror

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.personal.autolab.player.ContentState

private const val TAG = "AutoLab"
private const val REQUEST_MEDIA_PROJECTION = 2001
private const val REQUEST_PICK_VIDEO = 2002

/**
 * Fase 7A: Activity normal del telefono (icono propio en el launcher del
 * telefono, taskAffinity propio -- ver manifest -- para no compartir tarea
 * con GameActivity). Pide el consentimiento de MediaProjection UNA vez y
 * arranca ScreenMirrorService. El destino real (la Surface del auto) se
 * conecta despues, desde GameActivity, sin pedir consentimiento de nuevo.
 *
 * Fase 8: tambien controla que muestra GameActivity en el auto -- el espejo
 * completo, una pagina web propia, o un video local elegido aqui mismo (con
 * el selector de fotos del sistema, que incluye la galeria y Google Fotos).
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
            text = "AutoLab - Configurar proyección"
            setTextColor(Color.WHITE)
            textSize = 20f
        }

        estado = TextView(this).apply {
            text = "Sin iniciar."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }

        val botonProyeccion = Button(this).apply {
            text = "Iniciar proyección (espejo completo)"
            setOnClickListener { pedirConsentimiento() }
        }

        val separador1 = TextView(this).apply {
            text = "— o mostrar contenido propio en el auto —"
            setTextColor(Color.GRAY)
            textSize = 13f
            setPadding(0, 32, 0, 16)
        }

        val campoUrl = EditText(this).apply {
            hint = "https://m.youtube.com/..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val botonUrl = Button(this).apply {
            text = "Cargar página en el auto"
            setOnClickListener {
                val texto = campoUrl.text.toString().trim()
                if (texto.isNotEmpty()) {
                    Log.i(TAG, "Enviando URL al auto: $texto")
                    ContentState.mostrarWeb(texto)
                    estado.text = "Mostrando página en el auto: $texto"
                }
            }
        }

        val botonVideo = Button(this).apply {
            text = "Elegir video (galería / Google Fotos)"
            setOnClickListener { elegirVideo() }
        }

        val botonVolverEspejo = Button(this).apply {
            text = "Volver al espejo"
            setOnClickListener {
                ContentState.mostrarEspejo()
                estado.text = "Auto mostrando el espejo de nuevo."
            }
        }

        root.addView(titulo)
        root.addView(estado)
        root.addView(botonProyeccion)
        root.addView(separador1)
        root.addView(campoUrl)
        root.addView(botonUrl)
        root.addView(botonVideo)
        root.addView(botonVolverEspejo)
        setContentView(root)
    }

    private fun pedirConsentimiento() {
        Log.i(TAG, "Pidiendo consentimiento de MediaProjection desde ProjectionSetupActivity (displayId=${windowManager.defaultDisplay.displayId})")
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    private fun elegirVideo() {
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply { type = "video/*" }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" }
        }
        Log.i(TAG, "Abriendo selector de video (${if (Build.VERSION.SDK_INT >= 33) "photo picker" else "GET_CONTENT"})")
        startActivityForResult(intent, REQUEST_PICK_VIDEO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Log.i(TAG, "Consentimiento otorgado, iniciando ScreenMirrorService")
                val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
                    putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenMirrorService.EXTRA_DATA, data)
                }
                startForegroundService(serviceIntent)
                ContentState.mostrarEspejo()
                estado.text = "Proyección activa. Conecta el DHU y abre AutoLab desde su launcher."
            } else {
                Log.w(TAG, "Consentimiento denegado o cancelado")
                estado.text = "Consentimiento denegado o cancelado."
            }
            return
        }

        if (requestCode == REQUEST_PICK_VIDEO) {
            val uri: Uri? = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                Log.i(TAG, "Video elegido: $uri")
                ContentState.mostrarVideo(uri)
                estado.text = "Reproduciendo video local en el auto."
            } else {
                Log.w(TAG, "No se eligio ningun video")
            }
        }
    }
}
