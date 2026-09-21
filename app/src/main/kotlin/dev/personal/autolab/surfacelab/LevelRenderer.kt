package dev.personal.autolab.surfacelab

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Cada nivel de carga dibuja algo distinto sobre el Canvas del Surface del auto. */
interface LevelRenderer {
    fun draw(canvas: Canvas, elapsedMs: Long)
}

fun crearRenderer(nivel: SurfaceLabConfig.Level): LevelRenderer = when (nivel) {
    SurfaceLabConfig.Level.N0 -> N0Renderer()
    SurfaceLabConfig.Level.N1 -> N1Renderer()
    SurfaceLabConfig.Level.N2 -> N2Renderer()
    SurfaceLabConfig.Level.N3 -> N3Renderer()
}

/** N0: solo limpiar el fondo. */
class N0Renderer : LevelRenderer {
    override fun draw(canvas: Canvas, elapsedMs: Long) {
        canvas.drawColor(Color.BLACK)
    }
}

/** N1: 1000 rectangulos y circulos animados rebotando. */
class N1Renderer : LevelRenderer {
    private data class Figura(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val radio: Float, val esCirculo: Boolean, val color: Int,
    )

    private var figuras: MutableList<Figura>? = null
    private val paint = Paint()

    override fun draw(canvas: Canvas, elapsedMs: Long) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val lista = figuras ?: crearFiguras(w, h).also { figuras = it }

        canvas.drawColor(Color.BLACK)
        for (f in lista) {
            f.x += f.vx
            f.y += f.vy
            if (f.x < 0f || f.x > w) f.vx = -f.vx
            if (f.y < 0f || f.y > h) f.vy = -f.vy
            paint.color = f.color
            if (f.esCirculo) {
                canvas.drawCircle(f.x, f.y, f.radio, paint)
            } else {
                canvas.drawRect(f.x - f.radio, f.y - f.radio, f.x + f.radio, f.y + f.radio, paint)
            }
        }
    }

    private fun crearFiguras(w: Float, h: Float): MutableList<Figura> {
        val random = Random(42)
        return MutableList(1000) {
            Figura(
                x = random.nextFloat() * w,
                y = random.nextFloat() * h,
                vx = (random.nextFloat() - 0.5f) * 12f,
                vy = (random.nextFloat() - 0.5f) * 12f,
                radio = 6f + random.nextFloat() * 10f,
                esCirculo = random.nextBoolean(),
                color = Color.HSVToColor(floatArrayOf(random.nextFloat() * 360f, 0.8f, 1f)),
            )
        }
    }
}

/** N2: blit de un bitmap del tamano completo del Surface (simula espejo de pantalla). */
class N2Renderer : LevelRenderer {
    private var bitmap: Bitmap? = null

    override fun draw(canvas: Canvas, elapsedMs: Long) {
        val bmp = bitmap ?: crearBitmap(canvas.width, canvas.height).also { bitmap = it }
        canvas.drawBitmap(bmp, 0f, 0f, null)
    }

    private fun crearBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.MAGENTA, Color.CYAN, Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }
}

/** N3: texto, gradientes, paths y sombras. */
class N3Renderer : LevelRenderer {
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 64f
        isAntiAlias = true
        setShadowLayer(12f, 4f, 4f, Color.BLACK)
    }
    private val pathPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        color = Color.YELLOW
        setShadowLayer(8f, 0f, 0f, Color.RED)
    }

    override fun draw(canvas: Canvas, elapsedMs: Long) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        val fondo = Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, Color.BLUE, Color.rgb(20, 0, 40), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, fondo)

        val angulo = (elapsedMs % 4000L) / 4000f * 2f * Math.PI.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 3f
        val path = Path()
        path.moveTo(cx + r * cos(angulo), cy + r * sin(angulo))
        for (i in 1..8) {
            val a = angulo + i * (2f * Math.PI.toFloat() / 8f)
            path.lineTo(cx + r * cos(a), cy + r * sin(a))
        }
        path.close()
        canvas.drawPath(path, pathPaint)

        canvas.drawText("AutoLab N3", 40f, h - 60f, textPaint)
    }
}
