package com.example.halliplanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

class DashboardPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var values = listOf(1f, 1f, 1f)
    private var hasRealValues = false

    fun setValues(operations: Int, tasks: Int, meetings: Int) {
        values = listOf(
            operations.coerceAtLeast(0).toFloat(),
            tasks.coerceAtLeast(0).toFloat(),
            meetings.coerceAtLeast(0).toFloat()
        )
        hasRealValues = values.sum() > 0f
        if (values.sum() <= 0f) {
            values = listOf(1f, 1f, 1f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = width.coerceAtMost(height).toFloat()
        val inset = 10f
        val left = (width - size) / 2f + inset
        val top = (height - size) / 2f + inset
        bounds.set(left, top, left + size - inset * 2f, top + size - inset * 2f)

        val colors = listOf(
            ContextCompat.getColor(context, R.color.planner_primary),
            ContextCompat.getColor(context, R.color.planner_accent),
            ContextCompat.getColor(context, R.color.planner_secondary)
        )
        val total = values.sum()
        var startAngle = -90f
        val labelRadius = bounds.width() * 0.36f
        val labels = mutableListOf<ChartLabel>()

        values.forEachIndexed { index, value ->
            val sweep = (value / total) * 360f
            paint.color = colors[index]
            canvas.drawArc(bounds, startAngle, sweep, true, paint)

            if (hasRealValues && sweep >= 26f) {
                val percent = ((value / total) * 100f).toInt()
                val angle = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val x = bounds.centerX() + (cos(angle) * labelRadius).toFloat()
                val y = bounds.centerY() + (sin(angle) * labelRadius).toFloat()
                labels.add(ChartLabel("$percent%", x, y))
            }
            startAngle += sweep
        }

        paint.color = ContextCompat.getColor(context, R.color.planner_surface)
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * 0.28f, paint)

        textPaint.color = ContextCompat.getColor(context, R.color.white)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            15f,
            resources.displayMetrics
        )
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val textCenterOffset = -(textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        labels.forEach { label ->
            canvas.drawText(label.text, label.x, label.y + textCenterOffset, textPaint)
        }
    }

    private data class ChartLabel(
        val text: String,
        val x: Float,
        val y: Float
    )
}
