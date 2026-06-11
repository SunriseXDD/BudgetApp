package com.Popov.budgetapp.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.Popov.budgetapp.R
import kotlin.math.max

/**
 * Простой линейный график по точкам [0..1] без библиотек.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Значения расходов по дням (произвольная длина). */
    var values: List<Float> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = 1f
    }

    var lineColorRes: Int = R.color.chart_line
        set(value) {
            field = value
            applyLineColor()
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        applyLineColor()
    }

    private fun applyLineColor() {
        val color = ContextCompat.getColor(context, lineColorRes)
        linePaint.color = color
        fillPaint.color = ColorUtils.setAlphaComponent(color, 45)
    }

    private val path = Path()
    private val fillPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || values.isEmpty()) return

        val pad = 12f * resources.displayMetrics.density
        val w = width - 2 * pad
        val h = height - 2 * pad
        val maxV = max(values.maxOrNull() ?: 0f, 1f)

        // Сетка — несколько горизонталей
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = pad + h * i / gridLines
            canvas.drawLine(pad, y, width - pad, y, gridPaint)
        }

        if (values.size == 1) {
            val y = pad + h * (1f - values[0] / maxV)
            canvas.drawLine(pad, y, width - pad, y, linePaint)
            return
        }

        path.reset()
        fillPath.reset()
        val step = w / (values.size - 1).coerceAtLeast(1)

        values.forEachIndexed { i, v ->
            val x = pad + step * i
            val y = pad + h * (1f - (v / maxV).coerceIn(0f, 1f))
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        val baseY = pad + h
        fillPath.lineTo(pad + step * (values.size - 1), baseY)
        fillPath.lineTo(pad, baseY)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
