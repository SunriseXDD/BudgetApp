package com.Popov.budgetapp.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.Popov.budgetapp.R
import kotlin.math.min

/**
 * Кольцевой график без сторонних библиотек (Canvas + дуги).
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Segment(val value: Float, val color: Int)

    var segments: List<Segment> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.card_beige_soft)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (width <= 0 || height <= 0 || total <= 0f || segments.isEmpty()) {
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f * 0.92f
        val stroke = maxR * 0.32f
        arcPaint.strokeWidth = stroke
        val oval = RectF(cx - maxR, cy - maxR, cx + maxR, cy + maxR)

        var start = -90f
        for (seg in segments) {
            if (seg.value <= 0f) continue
            val sweep = 360f * (seg.value / total)
            arcPaint.color = seg.color
            canvas.drawArc(oval, start, sweep, false, arcPaint)
            start += sweep
        }

        val holeR = maxR - stroke + 1f
        canvas.drawCircle(cx, cy, holeR.coerceAtLeast(0f), holePaint)
    }
}
