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

    // Visible time window (in minutes from 00:00) – match WeekScheduleView
    private val startMinutes = 7 * 60 + 30 // 07:30
    private val endMinutes = 18 * 60 + 30  // 18:30

    // Cache for performance optimization
    private var lastDrawnDate: LocalDate? = null
    private var cachedToday: LocalDate? = null
    private var cachedNowY: Float = -1f
    private var cachedVisibleHours: Float = (endMinutes - startMinutes) / 60f
    private var cachedDesiredHeight: Int = (headerHeightPx + cachedVisibleHours * hourHeightPx).toInt()

    // Paints with optimized initialization
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

    private val tmpRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Use cached height calculation
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> max(cachedDesiredHeight, suggestedMinimumHeight)
            else -> cachedDesiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val gridLeft = timeColWidthPx
        val gridTop = headerHeightPx
        val gridRight = width.toFloat()

        val minutePx = hourHeightPx / 60f
        fun minuteToY(min: Int): Float = gridTop + (min - startMinutes) * minutePx

        // Optimize grid drawing - cache hour calculations
        drawTimeGrid(canvas, gridLeft, gridRight, ::minuteToY)

        // Optimize now line drawing with caching
        drawNowLine(canvas, gridLeft, gridRight, ::minuteToY)

        // Optimize event drawing
        drawEvents(canvas, gridLeft, gridRight, ::minuteToY)
    }

    private fun drawTimeGrid(canvas: Canvas, gridLeft: Float, gridRight: Float, minuteToY: (Int) -> Float) {
        val firstHour = kotlin.math.ceil(startMinutes / 60f).toInt()
        val lastHour = endMinutes / 60

        // Top boundary half-hour at 07:30
        val topHalfY = minuteToY(startMinutes)
        canvas.drawLine(gridLeft, topHalfY, gridRight, topHalfY, halfHourPaint)

        for (hour in firstHour..lastHour) {
            val y = minuteToY(hour * 60)
            canvas.drawLine(gridLeft, y, gridRight, y, gridPaint)

            val isLastHour = hour == lastHour
            if (!isLastHour) {
                val halfY = minuteToY(hour * 60 + 30)
                canvas.drawLine(gridLeft, halfY, gridRight, halfY, halfHourPaint)
            } else {
                // Bottom boundary half-hour at 18:30
                val bottomHalfY = minuteToY(endMinutes)
                canvas.drawLine(gridLeft, bottomHalfY, gridRight, bottomHalfY, halfHourPaint)
            }

            // Draw time labels
            val label = String.format(java.util.Locale.getDefault(), "%02d:00", hour)
            val tx = timeColWidthPx - 6f * density
            val ty = y + headerTextPaint.textSize
            canvas.drawText(label, tx - timeTextPaint.measureText(label), ty, timeTextPaint)
        }
    }

    private fun drawNowLine(canvas: Canvas, gridLeft: Float, gridRight: Float, minuteToY: (Int) -> Float) {
        // Cache today calculation and now line position
        val today = cachedToday ?: LocalDate.now().also { cachedToday = it }

        if (date == today) {
            val now = LocalTime.now()
            val minutes = now.hour * 60 + now.minute
            if (minutes in startMinutes..endMinutes) {
                // Use cached position or calculate new one
                val y = if (cachedNowY >= 0 && lastDrawnDate == date) {
                    cachedNowY
                } else {
                    minuteToY(minutes).also { cachedNowY = it }
                }
                canvas.drawLine(gridLeft, y, gridRight, y, nowLinePaint)
            }
        }
    }

    private fun drawEvents(canvas: Canvas, gridLeft: Float, gridRight: Float, minuteToY: (Int) -> Float) {
        // Only draw if events exist
        if (events.isEmpty()) return

        for (event in events) {
            val startMin = minutesSinceStartOfDay(event.start)
            val endMin = minutesSinceStartOfDay(event.end)
            val topMin = startMin.coerceAtLeast(startMinutes)
            val bottomMin = endMin.coerceAtMost(endMinutes)
            if (bottomMin <= startMinutes || topMin >= endMinutes) continue

            val top = minuteToY(topMin)
            val bottom = minuteToY(bottomMin)

            val bgColor = event.color
                ?: MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorPrimaryContainer)
            eventPaint.color = bgColor
            tmpRect.set(
                gridLeft + 8f * density,
                top + 2f * density,
                gridRight - 8f * density,
                bottom - 2f * density
            )
            canvas.drawRoundRect(tmpRect, 8f * density, 8f * density, eventPaint)

            // Only draw text if there's enough space
            if (tmpRect.height() > eventTextPaint.textSize * 1.5f) {
                val text = event.title
                val textX = tmpRect.left + 6f * density
                val textY = tmpRect.top + eventTextPaint.textSize + 4f * density
                val save = canvas.save()
                canvas.clipRect(tmpRect)
                canvas.drawText(text, textX, textY, eventTextPaint)
                canvas.restoreToCount(save)
            }
        }
    }

    private fun minutesSinceStartOfDay(dt: LocalDateTime): Int {
        val t = dt.toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun setDay(date: LocalDate, events: List<DayEvent>) {
        val needsRedraw = this.date != date || this.events != events
        this.date = date
        this.events = events.sortedBy { it.start }

        // Reset cache when date changes
        if (lastDrawnDate != date) {
            cachedNowY = -1f
            cachedToday = null
            lastDrawnDate = date
        }

        if (needsRedraw) {
            invalidate()
        }
    }

    fun getScrollYForTime(time: LocalTime): Int {
        val minutes = time.hour * 60 + time.minute
        val minutesInWindow = (minutes - startMinutes).coerceIn(0, endMinutes - startMinutes)
        val y = headerHeightPx + minutesInWindow * (hourHeightPx / 60f)
        return y.toInt()
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: TextPaint) {
        val half = paint.measureText(text) / 2f
        canvas.drawText(text, cx - half, cy, paint)
    }
}
