package com.mindenit.schedule.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * A single-day schedule view: vertical 24h timeline with events drawn by start/end time.
 * Styled to match WeekScheduleView (hour/half-hour lines, now line, themed colors).
 */
class DayScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class DayEvent(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val color: Int? = null
    )

    private var date: LocalDate = LocalDate.now()
    private var events: List<DayEvent> = emptyList()

    // Dimensions
    private val hourHeightDp = 56f
    private val headerHeightDp = 0f // remove internal header space
    private val timeColWidthDp = 36f
    private val hourLineWidth = 1f

    private val density = resources.displayMetrics.density
    private val hourHeightPx = (hourHeightDp * density)
    private val headerHeightPx = (headerHeightDp * density)
    private val timeColWidthPx = (timeColWidthDp * density)

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOutline)
        strokeWidth = hourLineWidth
    }
    private val halfHourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOutline)
        alpha = 80
        strokeWidth = 1f
    }
    private val nowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorPrimary)
        strokeWidth = 2f * density
    }
    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnSurface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
    }
    private val timeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eventTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnPrimary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }

    private val headerFormatter = DateTimeFormatter.ofPattern("EEEE, d LLLL", Locale.forLanguageTag("uk"))

    private val tmpRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (headerHeightPx + 24f * hourHeightPx).toInt()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> max(desiredHeight, suggestedMinimumHeight)
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val gridLeft = timeColWidthPx
        val gridTop = headerHeightPx
        val gridRight = width.toFloat()

        // Hour grid and time labels
        for (hour in 0..24) {
            val y = gridTop + hour * hourHeightPx
            canvas.drawLine(gridLeft, y, gridRight, y, gridPaint)
            if (hour < 24) {
                val halfY = y + hourHeightPx / 2f
                canvas.drawLine(gridLeft, halfY, gridRight, halfY, halfHourPaint)
                val label = String.format(java.util.Locale.getDefault(), "%02d:00", hour)
                val tx = timeColWidthPx - 6f * density
                val ty = y + headerTextPaint.textSize
                canvas.drawText(label, tx - timeTextPaint.measureText(label), ty, timeTextPaint)
            }
        }

        // Now line, if same date
        val today = java.time.LocalDate.now()
        if (date == today) {
            val now = java.time.LocalTime.now()
            val minutes = now.hour * 60 + now.minute
            val y = gridTop + minutes * (hourHeightPx / 60f)
            canvas.drawLine(gridLeft, y, gridRight, y, nowLinePaint)
        }

        // Events
        val minutePx = hourHeightPx / 60f
        for (event in events) {
            val startMinutes = minutesSinceStartOfDay(event.start)
            val endMinutes = minutesSinceStartOfDay(event.end)
            val top = gridTop + startMinutes * minutePx
            val bottom = gridTop + endMinutes * minutePx

            val bgColor = event.color ?: com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
            eventPaint.color = bgColor
            tmpRect.set(
                gridLeft + 8f * density,
                top + 2f * density,
                gridRight - 8f * density,
                bottom - 2f * density
            )
            canvas.drawRoundRect(tmpRect, 8f * density, 8f * density, eventPaint)

            val text = event.title
            val textX = tmpRect.left + 6f * density
            val textY = tmpRect.top + eventTextPaint.textSize + 4f * density
            val save = canvas.save()
            canvas.clipRect(tmpRect)
            canvas.drawText(text, textX, textY, eventTextPaint)
            canvas.restoreToCount(save)
        }
    }

    private fun minutesSinceStartOfDay(dt: LocalDateTime): Int {
        val t = dt.toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun setDay(date: LocalDate, events: List<DayEvent>) {
        this.date = date
        this.events = events.sortedBy { it.start }
        requestLayout()
        invalidate()
    }

    fun getScrollYForTime(time: LocalTime): Int {
        val minutes = time.hour * 60 + time.minute
        val y = headerHeightPx + minutes * (hourHeightPx / 60f)
        return y.toInt()
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: TextPaint) {
        val half = paint.measureText(text) / 2f
        canvas.drawText(text, cx - half, cy, paint)
    }
}
