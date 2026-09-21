package dev.personal.autolab.parkedgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.personal.autolab.metrics.FrameStats

private const val TAG = "AutoLab"

/**
 * Juego minimo (una pelota rebotando) sobre SurfaceView con su propio hilo de
 * render, reutilizando FrameStats de la Fase 2. Mide tambien la latencia entre
 * el toque (ACTION_DOWN) y el siguiente frame dibujado que lo refleja.
 */
class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var renderThread: Thread? = null
    @Volatile private var corriendo = false
    private var frameStats: FrameStats? = null

    private var ballX = 0f
    private var ballY = 0f
    private var ballVx = 6f
    private var ballVy = 4f
    private val ballRadius = 40f
    private val ballPaint = Paint().apply { color = Color.CYAN; isAntiAlias = true }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 36f; isAntiAlias = true }

    @Volatile private var ultimoToqueNanos: Long = 0
    @Volatile private var ultimaLatenciaMs: Double = 0.0

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        frameStats = FrameStats(context, "parked_game")
        ballX = width / 2f
        ballY = height / 2f
        corriendo = true
        renderThread = Thread({ bucle(holder) }, "AutoLabGameLoop").apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        corriendo = false
        renderThread?.join(500)
        renderThread = null
    }

    private fun bucle(holder: SurfaceHolder) {
        while (corriendo) {
            val canvas = holder.lockCanvas() ?: continue
            try {
                dibujarFrame(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
                frameStats?.onFramePosted()
            }
        }
    }

    private fun dibujarFrame(canvas: Canvas) {
        canvas.drawColor(Color.DKGRAY)

        ballX += ballVx
        ballY += ballVy
        if (ballX - ballRadius < 0 || ballX + ballRadius > canvas.width) ballVx = -ballVx
        if (ballY - ballRadius < 0 || ballY + ballRadius > canvas.height) ballVy = -ballVy
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)

        val toqueNanos = ultimoToqueNanos
        if (toqueNanos != 0L) {
            val latenciaMs = (System.nanoTime() - toqueNanos) / 1_000_000.0
            ultimaLatenciaMs = latenciaMs
            ultimoToqueNanos = 0
            Log.i(TAG, "Latencia de toque hasta el siguiente frame: ${"%.2f".format(latenciaMs)}ms")
        }

        canvas.drawText(
            "AutoLab Parked Game - ultima latencia de toque: ${"%.1f".format(ultimaLatenciaMs)}ms",
            24f, 48f, textPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            ultimoToqueNanos = System.nanoTime()
            ballPaint.color = Color.YELLOW
            postDelayed({ ballPaint.color = Color.CYAN }, 150)
        }
        return true
    }
}
