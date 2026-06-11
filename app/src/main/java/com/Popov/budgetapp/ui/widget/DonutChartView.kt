package com.Popov.budgetapp.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.Popov.budgetapp.R
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Кольцевой график без сторонних библиотек (Canvas + дуги).
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Segment(
        val value: Float,
        val color: Int,
        val label: String = "",
    )

    var segments: List<Segment> = emptyList()
        set(value) {
            field = value
            segmentAngles = emptyList()
            invalidate()
        }

    var onSegmentClick: ((Int, Segment) -> Unit)? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.card_beige_soft)
    }

    private var segmentAngles: List<Pair<Float, Float>> = emptyList()
    private var chartCx = 0f
    private var chartCy = 0f
    private var chartMaxR = 0f
    private var chartStroke = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (width <= 0 || height <= 0 || total <= 0f || segments.isEmpty()) {
            segmentAngles = emptyList()
            return
        }

        chartCx = width / 2f
        chartCy = height / 2f
        chartMaxR = min(width, height) / 2f * 0.92f
        chartStroke = chartMaxR * 0.32f
        arcPaint.strokeWidth = chartStroke
        val oval = RectF(chartCx - chartMaxR, chartCy - chartMaxR, chartCx + chartMaxR, chartCy + chartMaxR)

        var start = -90f
        val angles = mutableListOf<Pair<Float, Float>>()
        for (seg in segments) {
            if (seg.value <= 0f) continue
            val sweep = 360f * (seg.value / total)
            arcPaint.color = seg.color
            canvas.drawArc(oval, start, sweep, false, arcPaint)
            val touchStart = (start + 90f + 360f) % 360f
            val touchEnd = (start + sweep + 90f + 360f) % 360f
            angles += touchStart to touchEnd
            start += sweep
        }
        segmentAngles = angles

        val holeR = chartMaxR - chartStroke + 1f
        canvas.drawCircle(chartCx, chartCy, holeR.coerceAtLeast(0f), holePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || onSegmentClick == null) return super.onTouchEvent(event)
        val idx = segmentIndexAt(event.x, event.y) ?: return super.onTouchEvent(event)
        val seg = segments.getOrNull(idx) ?: return super.onTouchEvent(event)
        onSegmentClick?.invoke(idx, seg)
        return true
    }

    private fun segmentIndexAt(x: Float, y: Float): Int? {
        if (segmentAngles.isEmpty() || chartMaxR <= 0f) return null
        val dx = x - chartCx
        val dy = y - chartCy
        val dist = sqrt(dx * dx + dy * dy)
        val inner = chartMaxR - chartStroke
        if (dist < inner || dist > chartMaxR) return null

        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        angle = (angle + 90f + 360f) % 360f

        segmentAngles.forEachIndexed { index, (start, end) ->
            val hit = if (start <= end) angle in start..end else angle >= start || angle <= end
            if (hit) return index
        }
        return null
    }
}
