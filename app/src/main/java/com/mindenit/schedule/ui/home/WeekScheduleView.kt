package com.mindenit.schedule.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
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
        val color: Int? = null,
        val meta: Any? = null
    )

    private var startOfWeek: LocalDate = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private var events: List<WeekEvent> = emptyList()

    // Dimensions
    private val hourHeightDp = 56f
    private val headerHeightDp = 44f
    private val timeColWidthDp = 36f
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
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOutline)
        strokeWidth = hourLineWidth
    }
    private val halfHourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOutline)
        alpha = 80 // lighter
        strokeWidth = 1f
    }
    private val weekendFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorSurfaceVariant), 0.10f)
        style = Paint.Style.FILL
    }
    private val todayFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorSecondaryContainer), 0.18f)
        style = Paint.Style.FILL
    }
    private val nowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorPrimary)
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val nowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorPrimary)
        style = Paint.Style.FILL
    }

    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOnSurface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val headerTodayTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorPrimary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
    }
    private val timeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10.8f, resources.displayMetrics)
    }
    // Smaller time text for event cards (half size)
    private val eventTimeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 6f, resources.displayMetrics)
    }
    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOnSurface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private val infoTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@WeekScheduleView, MaterialR.attr.colorSurfaceContainerHigh)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.forLanguageTag("uk"))

    // Reusable rect to avoid allocations in onDraw
    private val tmpRect = RectF()
    // Reusable path for rounded rects (left corners only)
    private val tmpPath = Path()

    // Hit detection rects for events
    private val hitRects: MutableList<Pair<RectF, WeekEvent>> = mutableListOf()

    // Hit detection rects for event groups (3+ concurrent)
    private val groupHitRects: MutableList<Pair<RectF, List<WeekEvent>>> = mutableListOf()

    // Track press within an event to coordinate with parent scroll
    private var downInsideEvent = false

    // Track press within an event or group
    private var downInsideGroup = false

    // Event click listener
    var onEventClick: ((WeekEvent) -> Unit)? = null

    // Group click listener
    var onGroupClick: ((List<WeekEvent>) -> Unit)? = null

    // Periodic ticker to keep the now indicator updated
    private val nowTicker = object : Runnable {
        override fun run() {
            val today = LocalDate.now()
            if (!today.isBefore(startOfWeek) && !today.isAfter(startOfWeek.plusDays((dayCount - 1).toLong()))) {
                invalidate()
            }
            postDelayed(this, 15_000L)
        }
    }

    // Show only Monday–Saturday
    private val dayCount = 6

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(nowTicker)
        post(nowTicker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(nowTicker)
        super.onDetachedFromWindow()
    }

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

        val gridLeft = paddingLeft.toFloat() + timeColWidthPx
        val gridTop = paddingTop.toFloat() + headerHeightPx
        val contentWidth = width.toFloat() - paddingLeft - paddingRight - timeColWidthPx
        val dayWidth = (contentWidth / dayCount.toFloat()).coerceAtLeast(0f)
        val gridRight = paddingLeft.toFloat() + timeColWidthPx + dayCount.toFloat() * dayWidth
        val gridBottom = h - paddingBottom

        val minutePx = hourHeightPx / 60f
        fun minuteToY(min: Int): Float = gridTop + (min - startMinutes) * minutePx

        // Background: shade weekends and highlight today (below header)
        val today = LocalDate.now()
        for (i in 0 until dayCount) {
            val x0 = gridLeft + i * dayWidth
            val x1 = x0 + dayWidth
            val dayDate = startOfWeek.plusDays(i.toLong())
            if (dayDate.dayOfWeek == DayOfWeek.SATURDAY) {
                tmpRect.set(x0, gridTop, x1, gridBottom)
                canvas.drawRect(tmpRect, weekendFillPaint)
            }
            if (dayDate == today) {
                tmpRect.set(x0, gridTop, x1, gridBottom)
                canvas.drawRect(tmpRect, todayFillPaint)
            }
        }

        // Header: weekday labels (Mon–Sat)
        for (i in 0 until dayCount) {
            val dayDate = startOfWeek.plusDays(i.toLong())
            val label = dayDate.format(dayFormatter).lowercase(Locale.forLanguageTag("uk"))
            val x = gridLeft + i * dayWidth + dayWidth / 2f
            val y = paddingTop.toFloat() + headerHeightPx / 2f + headerTextPaint.textSize / 2f
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
            val tx = paddingLeft.toFloat() + timeColWidthPx - 6f * density
            val ty = y + headerTextPaint.textSize
            canvas.drawText(label, tx - timeTextPaint.measureText(label), ty, timeTextPaint)
        }

        // Vertical day dividers
        for (i in 0..dayCount) {
            val x = gridLeft + i * dayWidth
            canvas.drawLine(x, gridTop, x, gridBottom, gridPaint)
        }

        // Draw events per day (Mon–Sat only)
        hitRects.clear()
        groupHitRects.clear()
        for (dayIndex in 0 until dayCount) {
            val dayEvents = events.filter { ((it.start.toLocalDate().dayOfWeek.value + 6) % 7) == dayIndex }
            if (dayEvents.isEmpty()) continue

            val columnLeft = gridLeft + dayIndex * dayWidth
            val columnRight = columnLeft + dayWidth

            val groups = linkedMapOf<Pair<Int, Int>, MutableList<WeekEvent>>()
            for (e in dayEvents) {
                val s = minutesSinceStartOfDay(e.start)
                val en = minutesSinceStartOfDay(e.end)
                val topMin = s.coerceAtLeast(startMinutes)
                val bottomMin = en.coerceAtMost(endMinutes)
                if (bottomMin <= startMinutes || topMin >= endMinutes) continue
                val key = Pair(topMin, bottomMin)
                groups.getOrPut(key) { mutableListOf() }.add(e)
            }

            for ((key, list) in groups) {
                if (list.isEmpty()) continue
                val (topMin, bottomMin) = key
                val top = minuteToY(topMin)
                val bottom = minuteToY(bottomMin)

                val corner = 8f * density
                val hPad = 6f * density
                val vPad = 2f * density
                val accentW = 2f * density
                val colGap = 2f * density

                // If 3+ events overlap, draw a single group button instead (square corners)
                if (list.size >= 3) {
                    val groupLeft = columnLeft + hPad
                    val groupRight = columnRight - hPad
                    tmpRect.set(groupLeft, top + vPad, groupRight, bottom - vPad)
                    canvas.drawRect(tmpRect, cardPaint)

                    // Label: "Пари: N" centered
                    val label = "Пари: ${list.size}"
                    val cx = tmpRect.centerX()
                    val cy = tmpRect.centerY() + titleTextPaint.textSize / 2f
                    drawCenteredText(canvas, label, cx, cy, titleTextPaint)

                    groupHitRects.add(Pair(RectF(tmpRect), list.toList()))
                    continue
                }

                val slotLeft = columnLeft + hPad
                val slotRight = columnRight - hPad
                val cols = list.size
                val totalGap = colGap * (cols - 1)
                val perW = ((slotRight - slotLeft - totalGap) / cols).coerceAtLeast(8f * density)

                // sort for stable order (use subject label, then domain id if available)
                fun subjectOf(ev: WeekEvent): String = ev.title.substringBefore(" • ").lowercase(Locale.getDefault())
                val sorted = list.sortedWith(compareBy<WeekEvent>({ subjectOf(it) }, { (it.meta as? com.mindenit.schedule.data.Event)?.id ?: 0L }))
                for ((idx, event) in sorted.withIndex()) {
                    val left = slotLeft + idx * (perW + colGap)
                    val right = (left + perW).coerceAtMost(slotRight)

                    // Card rect (square corners)
                    tmpRect.set(left, top + vPad, right, bottom - vPad)
                    canvas.drawRect(tmpRect, cardPaint)

                    // Accent (square corners)
                    accentPaint.color = event.color ?: MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
                    val accentRect = RectF(tmpRect.left, tmpRect.top, tmpRect.left + accentW, tmpRect.bottom)
                    canvas.drawRect(accentRect, accentPaint)

                    // Hit rect per sub-card
                    hitRects.add(Pair(RectF(tmpRect), event))

                    // Content area
                    val contentLeft = tmpRect.left + accentW + 4f * density
                    val contentRight = tmpRect.right - 4f * density
                    var cursorY = tmpRect.top + 6f * density + timeTextPaint.textSize

                    // Split title to subject/location if formatted as "subject • location"
                    val parts = event.title.split(" • ", limit = 2)
                    val subject = parts.getOrNull(0) ?: event.title
                    val location = parts.getOrNull(1)

                    // Clip to sub-card
                    val save = canvas.save()
                    canvas.clipRect(tmpRect)

                    // Time: draw single line if width allows (wrap if needed)
                    val timeText = formatTimeRange(event.start.toLocalTime(), event.end.toLocalTime())
                    cursorY = drawWrapped(canvas, timeText, contentLeft, contentRight, cursorY, eventTimeTextPaint, tmpRect.bottom - 6f * density)

                    // Available height below
                    var available = tmpRect.height() - (cursorY - tmpRect.top) - 6f * density

                    // Subject
                    if (available > titleTextPaint.textSize * 0.9f) {
                        cursorY += 4f * density + titleTextPaint.textSize
                        cursorY = drawWrapped(canvas, subject, contentLeft, contentRight, cursorY, titleTextPaint, tmpRect.bottom - 6f * density)
                        available = tmpRect.height() - (cursorY - tmpRect.top) - 6f * density
                    }
                    // Location
                    if (location != null && available > infoTextPaint.textSize * 1.1f + 2f * density) {
                        cursorY += 2f * density + infoTextPaint.textSize
                        cursorY = drawWrapped(canvas, location, contentLeft, contentRight, cursorY, infoTextPaint, tmpRect.bottom - 6f * density)
                    }

                    canvas.restoreToCount(save)
                }
            }
        }

        // Draw now overlay LAST to be on top of everything
        drawNowOverlay(canvas, today, gridLeft, dayWidth, ::minuteToY)
    }

    private fun drawNowOverlay(canvas: Canvas, today: LocalDate, gridLeft: Float, dayWidth: Float, minuteToY: (Int) -> Float) {
        if (today.isBefore(startOfWeek) || today.isAfter(startOfWeek.plusDays((dayCount - 1).toLong()))) return
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        if (minutes !in startMinutes..endMinutes) return
        val y = minuteToY(minutes)
        val dayIndex = (today.dayOfWeek.value + 6) % 7
        if (dayIndex >= dayCount) return // Skip Sunday
        val x0 = gridLeft + dayIndex * dayWidth
        val x1 = x0 + dayWidth

        // Line across current day column
        canvas.drawLine(x0, y, x1, y, nowLinePaint)

        // Dot at leading edge
        val dotR = 3.5f * resources.displayMetrics.density
        val dotCx = x0 + 4f * resources.displayMetrics.density
        canvas.drawCircle(dotCx, y, dotR, nowDotPaint)

        // Remove time chip label
    }

    // Draws text wrapped by characters within [left,right] and up to maxBottom.
    // Returns the last baseline Y used (for chaining further text).
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        left: Float,
        right: Float,
        startBaselineY: Float,
        paint: TextPaint,
        maxBottom: Float
    ): Float {
        val maxWidth = right - left
        val lineAdvance = paint.textSize * 1.2f
        var baselineY = startBaselineY
        var index = 0
        val len = text.length
        val measured = FloatArray(1)

        while (index < len && baselineY <= maxBottom) {
            val count = paint.breakText(text, index, len, true, maxWidth, measured)
            if (count <= 0) break
            val end = (index + count).coerceAtMost(len)
            val line = text.substring(index, end)
            canvas.drawText(line, left, baselineY, paint)
            index = end
            val nextBaseline = baselineY + lineAdvance
            if (nextBaseline > maxBottom && index < len) {
                // Replace last line with ellipsis if truncated
                val ellipsis = "\u2026"
                var ell = line
                var w = paint.measureText(ell + ellipsis)
                while (ell.isNotEmpty() && w > maxWidth) {
                    ell = ell.substring(0, ell.length - 1)
                    w = paint.measureText(ell + ellipsis)
                }
                // Clear the previously drawn line area by overdrawing background? Not needed due to clip and overwrite next line area minimal; just draw over slightly lower
                canvas.drawText("$ell$ellipsis", left, baselineY, paint)
                return baselineY
            }
            baselineY = nextBaseline
        }
        // If we exited because baseline exceeded, step back one line
        return baselineY
    }

    private fun minutesSinceStartOfDay(dt: LocalDateTime): Int {
        val t = dt.toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
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

    private fun formatTimeRange(s: LocalTime, e: LocalTime): String {
        fun fmt(t: LocalTime) = String.format(Locale.getDefault(), "%02d:%02d", t.hour, t.minute)
        return fmt(s) + "–" + fmt(e)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check groups first
                downInsideGroup = groupHitRects.any { it.first.contains(x, y) }
                if (downInsideGroup) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                // If touch starts inside any event, handle it here
                downInsideEvent = hitRects.any { it.first.contains(x, y) }
                if (downInsideEvent) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (downInsideGroup) {
                    val hit = groupHitRects.firstOrNull { it.first.contains(x, y) }?.second
                    downInsideGroup = false
                    if (hit != null) {
                        onGroupClick?.invoke(hit)
                        performClick()
                        return true
                    }
                }
                if (downInsideEvent) {
                    val hit = hitRects.firstOrNull { it.first.contains(x, y) }?.second
                    downInsideEvent = false
                    if (hit != null) {
                        onEventClick?.invoke(hit)
                        performClick()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                downInsideGroup = false
                downInsideEvent = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
