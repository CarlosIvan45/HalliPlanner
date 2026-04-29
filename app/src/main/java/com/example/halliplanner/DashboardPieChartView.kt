package com.example.halliplanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
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
        val left = (width - size) / 2f + 8f
        val top = (height - size) / 2f + 8f
        bounds.set(left, top, left + size - 16f, top + size - 16f)

        val colors = listOf(
            ContextCompat.getColor(context, R.color.planner_primary),
            ContextCompat.getColor(context, R.color.planner_accent),
            ContextCompat.getColor(context, R.color.planner_secondary)
        )
        val total = values.sum()
        var startAngle = -90f
        val labelRadius = bounds.width() * 0.32f

        values.forEachIndexed { index, value ->
            val sweep = (value / total) * 360f
            paint.color = colors[index]
            canvas.drawArc(bounds, startAngle, sweep, true, paint)

            if (hasRealValues && sweep >= 26f) {
                val percent = ((value / total) * 100f).toInt()
                val angle = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val x = bounds.centerX() + (cos(angle) * labelRadius).toFloat()
                val y = bounds.centerY() + (sin(angle) * labelRadius).toFloat()
                textPaint.color = ContextCompat.getColor(context, R.color.white)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = 24f
                textPaint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("$percent%", x, y + 8f, textPaint)
            }
            startAngle += sweep
        }

        paint.color = ContextCompat.getColor(context, R.color.planner_surface)
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * 0.28f, paint)
    }
}
