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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * A simple week schedule view similar to Google Calendar:
 * - Horizontal: 7 day columns (Mon..Sun)
 * - Vertical: 24-hour timeline
 * - Events drawn as blocks relative to start/end time in their day column
 */
class WeekScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class WeekEvent(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val color: Int? = null
    )

    private var startOfWeek: LocalDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private var events: List<WeekEvent> = emptyList()

    // Dimensions
    private val hourHeightDp = 56f
    private val headerHeightDp = 44f
    private val timeColWidthDp = 56f
    private val hourLineWidth = 1f

    private val density = resources.displayMetrics.density
    private val hourHeightPx = (hourHeightDp * density)
    private val headerHeightPx = (headerHeightDp * density)
    private val timeColWidthPx = (timeColWidthDp * density)

    // Time window (in minutes from 00:00)
    private val startMinutes = 7 * 60 + 30 // 07:30
    private val endMinutes = 18 * 60 + 30  // 18:30

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorOutline)
        strokeWidth = hourLineWidth
    }
    private val halfHourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorOutline)
        alpha = 80 // lighter
        strokeWidth = 1f
    }
    private val weekendFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorSurfaceVariant), 0.10f)
        style = Paint.Style.FILL
    }
    private val todayFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorSecondaryContainer), 0.18f)
        style = Paint.Style.FILL
    }
    private val nowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorPrimary)
        strokeWidth = 2f * density
    }
    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorOnSurface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val headerTodayTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorPrimary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val timeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eventTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, com.google.android.material.R.attr.colorOnPrimary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("uk"))

    // Reusable rect to avoid allocations in onDraw
    private val tmpRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHours = (endMinutes - startMinutes) / 60f // 11h
        val desiredHeight = (headerHeightPx + desiredHours * hourHeightPx).toInt()
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> widthSize
            else -> widthSize
        }
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> max(desiredHeight, suggestedMinimumHeight)
            else -> desiredHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()

        val gridLeft = timeColWidthPx
        val gridTop = headerHeightPx
        val dayWidth = ((width.toFloat() - timeColWidthPx) / 7f).coerceAtLeast(0f)
        val gridRight = timeColWidthPx + 7f * dayWidth
        val gridBottom = h

        val minutePx = hourHeightPx / 60f
        fun minuteToY(min: Int): Float = gridTop + (min - startMinutes) * minutePx

        // Background: shade weekends and highlight today (below header)
        val today = LocalDate.now()
        for (i in 0 until 7) {
            val x0 = gridLeft + i * dayWidth
            val x1 = x0 + dayWidth
            val dayDate = startOfWeek.plusDays(i.toLong())
            if (dayDate.dayOfWeek == DayOfWeek.SATURDAY || dayDate.dayOfWeek == DayOfWeek.SUNDAY) {
                tmpRect.set(x0, gridTop, x1, gridBottom)
                canvas.drawRect(tmpRect, weekendFillPaint)
            }
            if (dayDate == today) {
                tmpRect.set(x0, gridTop, x1, gridBottom)
                canvas.drawRect(tmpRect, todayFillPaint)
            }
        }

        // Header: weekday labels
        for (i in 0 until 7) {
            val dayDate = startOfWeek.plusDays(i.toLong())
            val label = dayDate.format(dayFormatter).uppercase()
            val x = gridLeft + i * dayWidth + dayWidth / 2f
            val y = headerHeightPx / 2f + headerTextPaint.textSize / 2f
            val paint = if (dayDate == today) headerTodayTextPaint else headerTextPaint
            drawCenteredText(canvas, label, x, y, paint)
        }

        // Time labels and hour/half-hour lines within [07:30, 18:30]
        val firstHour = kotlin.math.ceil(startMinutes / 60f).toInt() // 8
        val lastHour = endMinutes / 60 // 18
        // Draw top boundary half-hour at 07:30
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
            val label = String.format(Locale.getDefault(), "%02d:00", hour)
            val tx = timeColWidthPx - 6f * density
            val ty = y + headerTextPaint.textSize
            canvas.drawText(label, tx - timeTextPaint.measureText(label), ty, timeTextPaint)
        }

        // Vertical day dividers
        for (i in 0..7) {
            val x = gridLeft + i * dayWidth
            canvas.drawLine(x, gridTop, x, gridBottom, gridPaint)
        }

        // Now line for current day/time (only if within visible window)
        if (!today.isBefore(startOfWeek) && !today.isAfter(startOfWeek.plusDays(6))) {
            val now = LocalTime.now()
            val minutes = now.hour * 60 + now.minute
            if (minutes in startMinutes..endMinutes) {
                val y = minuteToY(minutes)
                val dayIndex = (today.dayOfWeek.value + 6) % 7
                val x0 = gridLeft + dayIndex * dayWidth
                val x1 = x0 + dayWidth
                canvas.drawLine(x0, y, x1, y, nowLinePaint)
            }
        }

        // Draw events (clamped to visible window)
        for (event in events) {
            val dayIndex = ((event.start.toLocalDate().dayOfWeek.value + 6) % 7)
            if (dayIndex !in 0..6) continue
            val columnLeft = gridLeft + dayIndex * dayWidth
            val columnRight = columnLeft + dayWidth

            val startMin = minutesSinceStartOfDay(event.start)
            val endMin = minutesSinceStartOfDay(event.end)
            val topMin = startMin.coerceAtLeast(startMinutes)
            val bottomMin = endMin.coerceAtMost(endMinutes)
            if (bottomMin <= startMinutes || topMin >= endMinutes) continue

            val top = minuteToY(topMin)
            val bottom = minuteToY(bottomMin)

            val bgColor = event.color ?: MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer)
            eventPaint.color = bgColor
            tmpRect.set(
                columnLeft + 6f * density,
                top + 2f * density,
                columnRight - 6f * density,
                bottom - 2f * density
            )
            canvas.drawRoundRect(tmpRect, 8f * density, 8f * density, eventPaint)

            // Text inside event (clip to event rect)
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

    fun setWeek(start: LocalDate, events: List<WeekEvent>) {
        startOfWeek = start
        this.events = events.sortedBy { it.start }
        requestLayout()
        invalidate()
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: TextPaint) {
        val half = paint.measureText(text) / 2f
        canvas.drawText(text, cx - half, cy, paint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    fun getScrollXForDate(date: LocalDate): Int {
        // With dynamic width and no horizontal scroll container, this is 0
        return 0
    }

    fun getContentWidth(): Int = width

    fun getScrollYForTime(time: LocalTime): Int {
        val minutes = time.hour * 60 + time.minute
        val minutesInWindow = (minutes - startMinutes).coerceIn(0, endMinutes - startMinutes)
        val y = headerHeightPx + minutesInWindow * (hourHeightPx / 60f)
        return y.toInt()
    }
}
