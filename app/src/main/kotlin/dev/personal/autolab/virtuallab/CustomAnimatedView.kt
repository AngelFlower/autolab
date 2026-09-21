package dev.personal.autolab.virtuallab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

private const val TAG = "AutoLab"

/** View propia con animacion continua (rotacion), para comparar contra el caso WebView. */
class CustomAnimatedView(context: Context) : View(context) {

    private val paint = Paint().apply { isAntiAlias = true; color = Color.CYAN }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 32f
    }
    private var angulo = 0f

    override fun onDraw(canvas: Canvas) {
        angulo = (angulo + 4f) % 360f
        canvas.drawColor(Color.DKGRAY)

        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 3f

        canvas.save()
        canvas.rotate(angulo, cx, cy)
        canvas.drawRoundRect(cx - r, cy - r / 2f, cx + r, cy + r / 2f, 24f, 24f, paint)
        canvas.restore()

        canvas.drawText("AutoLab VirtualDisplay - Custom View", 24f, height - 24f, textPaint)

        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            Log.i(TAG, "CustomAnimatedView recibio toque x=${event.x} y=${event.y}")
        }
        return true
    }
}
