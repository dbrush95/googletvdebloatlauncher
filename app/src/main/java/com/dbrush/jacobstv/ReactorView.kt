package com.dbrush.jacobstv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ReactorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val gold = Color.parseColor("#E4B15A")
    private val cyan = Color.parseColor("#5FD0E6")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val core = Path()
    private var angle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncSpin()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncSpin()
    }

    private fun syncSpin() {
        if (!isAttachedToWindow) return
        if (isShown) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.92f
        if (radius <= 0f) return

        paint.style = Paint.Style.STROKE
        paint.color = gold
        paint.alpha = 160
        paint.strokeWidth = radius * 0.018f
        canvas.drawCircle(cx, cy, radius, paint)
        paint.alpha = 90
        canvas.drawCircle(cx, cy, radius * 0.78f, paint)

        canvas.save()
        canvas.rotate(angle, cx, cy)
        paint.color = gold
        paint.alpha = 220
        paint.strokeWidth = radius * 0.025f
        for (i in 0 until 12) {
            val theta = Math.toRadians((i * 30).toDouble())
            val inner = radius * 0.62f
            val outer = radius * 0.74f
            canvas.drawLine(
                cx + cos(theta).toFloat() * inner,
                cy + sin(theta).toFloat() * inner,
                cx + cos(theta).toFloat() * outer,
                cy + sin(theta).toFloat() * outer,
                paint,
            )
        }
        canvas.restore()

        canvas.save()
        canvas.rotate(-angle * 1.5f, cx, cy)
        paint.color = cyan
        paint.alpha = 230
        paint.strokeWidth = radius * 0.035f
        val arc = RectF(cx - radius * 0.56f, cy - radius * 0.56f, cx + radius * 0.56f, cy + radius * 0.56f)
        canvas.drawArc(arc, 18f, 78f, false, paint)
        canvas.drawArc(arc, 198f, 78f, false, paint)
        canvas.restore()

        paint.color = cyan
        paint.alpha = 180
        paint.strokeWidth = radius * 0.016f
        canvas.drawCircle(cx, cy, radius * 0.34f, paint)

        paint.style = Paint.Style.FILL
        paint.color = gold
        paint.alpha = 235
        val tri = radius * 0.16f
        core.reset()
        core.moveTo(cx, cy - tri)
        core.lineTo(cx + tri * 0.9f, cy + tri * 0.72f)
        core.lineTo(cx - tri * 0.9f, cy + tri * 0.72f)
        core.close()
        canvas.drawPath(core, paint)
    }
}
