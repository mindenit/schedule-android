package com.mindenit.schedule.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
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
        val color: Int? = null,
        val meta: Any? = null,
        val type: String? = null
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
    private val timeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val titleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnSurface)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private val infoTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val tmpRect = RectF()
    private val hitRects: MutableList<Pair<RectF, DayEvent>> = mutableListOf()

    var onEventClick: ((DayEvent) -> Unit)? = null

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
            val label = String.format(Locale.getDefault(), "%02d:00", hour)
            val tx = timeColWidthPx - 6f * density
            val ty = y + titleTextPaint.textSize
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
        hitRects.clear()
        // Only draw if events exist
        if (events.isEmpty()) return

        val corner = 10f * density
        val horizPad = 8f * density
        val vertPad = 6f * density
        val accentW = 4f * density
        val colGap = 4f * density
        val rowGap = 2f * density
        val badgeTimeGap = 4f * density

        // Group events by identical visible slot (topMin..bottomMin)
        val groups = linkedMapOf<Pair<Int, Int>, MutableList<DayEvent>>()
        for (event in events) {
            val startMin = minutesSinceStartOfDay(event.start)
            val endMin = minutesSinceStartOfDay(event.end)
            val topMin = startMin.coerceAtLeast(startMinutes)
            val bottomMin = endMin.coerceAtMost(endMinutes)
            if (bottomMin <= startMinutes || topMin >= endMinutes) continue
            val key = Pair(topMin, bottomMin)
            groups.getOrPut(key) { mutableListOf() }.add(event)
        }

        for ((key, list) in groups) {
            if (list.isEmpty()) continue
            val (topMin, bottomMin) = key
            val top = minuteToY(topMin)
            val bottom = minuteToY(bottomMin)

            val slotLeft = gridLeft + horizPad
            val slotRight = gridRight - horizPad

            val cols = list.size
            val totalGap = if (cols > 1) colGap * (cols - 1) else 0f
            val perW = if (cols > 1) ((slotRight - slotLeft - totalGap) / cols).coerceAtLeast(8f * density) else (slotRight - slotLeft)

            // Stable order (by title then start)
            val sorted = list.sortedWith(compareBy({ it.title }, { it.start }))

            for ((idx, event) in sorted.withIndex()) {
                val left = if (cols > 1) slotLeft + idx * (perW + colGap) else slotLeft
                val right = if (cols > 1) (left + perW).coerceAtMost(slotRight) else slotRight

                // Background card
                tmpRect.set(
                    left,
                    top + 2f * density,
                    right,
                    bottom - 2f * density
                )
                canvas.drawRoundRect(tmpRect, corner, corner, cardPaint)

                // Accent strip
                accentPaint.color = event.color ?: MaterialColors.getColor(this@DayScheduleView, com.google.android.material.R.attr.colorPrimary)
                val accentRect = RectF(tmpRect.left, tmpRect.top, tmpRect.left + accentW, tmpRect.bottom)
                canvas.drawRoundRect(accentRect, corner, corner, accentPaint)

                // Make hit target inside the card (excluding accent)
                val hitRect = RectF(tmpRect.left, tmpRect.top, tmpRect.right, tmpRect.bottom)
                hitRects.add(Pair(hitRect, event))

                // Content paddings and initial baselines
                val contentLeft = tmpRect.left + accentW + 6f * density
                val contentRight = tmpRect.right - 6f * density

                // Baselines for lines: 1) type badge, 2) time, 3) subject, 4) location
                val line1Baseline = tmpRect.top + vertPad + timeTextPaint.textSize
                var line2Baseline = line1Baseline + timeTextPaint.textSize + rowGap // will be adjusted if chip is taller
                // line3/4 will be computed after final line2Baseline

                val rawType = event.type?.trim().orEmpty()
                if (rawType.isNotEmpty()) {
                    // Draw type badge (chip) on line 1
                    val typeColors = EventColorResolver.colorsForType(this@DayScheduleView, rawType)
                    val padH = 6f * density  // зменшуємо горизонтальний відступ
                    val padV = 4f * density  // зменшуємо вертикальний відступ
                    val chipCorner = 6f * density  // зменшуємо радіус кутів

                    val baseLabel = shortTypeLabel(rawType)
                    val maxTextW = (contentRight - contentLeft) - 2 * padH
                    var shownLabel = baseLabel
                    var canDrawChip = maxTextW > 0
                    if (canDrawChip && timeTextPaint.measureText(shownLabel) > maxTextW) {
                        val count = timeTextPaint.breakText(shownLabel, 0, shownLabel.length, true, maxTextW, null)
                        val ellipsis = "\u2026"
                        when {
                            count >= 2 -> shownLabel = shownLabel.substring(0, count - 1) + ellipsis
                            count == 1 -> shownLabel = shownLabel.substring(0, 1)
                            else -> canDrawChip = false
                        }
                    }

                    // Default chip bottom if we can't draw
                    var chipBottom = line1Baseline + padV

                    if (canDrawChip) {
                        val chipW = timeTextPaint.measureText(shownLabel) + 2 * padH
                        val chipLeft = contentLeft
                        // Правильно обчислюємо верх і низ чіпа з меншими відступами
                        val chipTop = line1Baseline - timeTextPaint.textSize * 0.8f - padV
                        chipBottom = line1Baseline + padV * 0.6f
                        val chipRect = RectF(chipLeft, chipTop, chipLeft + chipW, chipBottom)

                        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = typeColors.background }
                        canvas.drawRoundRect(chipRect, chipCorner, chipCorner, chipPaint)

                        val old = timeTextPaint.color
                        timeTextPaint.color = typeColors.foreground
                        canvas.drawText(shownLabel, chipLeft + padH, line1Baseline, timeTextPaint)
                        timeTextPaint.color = old
                    }
                    // Збільшуємо відступ між бейджем і часом
                    line2Baseline = max(line2Baseline, chipBottom + badgeTimeGap + 2f * density)
                }

                // Line 2: time range
                val timeText = formatTimeRange(event.start.toLocalTime(), event.end.toLocalTime())
                if (line2Baseline <= tmpRect.bottom - vertPad) {
                    canvas.drawText(timeText, contentLeft, line2Baseline, timeTextPaint)
                }

                // Now compute baselines for title and location using final line2Baseline
                val line3Baseline = line2Baseline + titleTextPaint.textSize + rowGap // subject
                val line4Baseline = line3Baseline + infoTextPaint.textSize + rowGap  // location

                // Extract subject and location from title (expected format: "subject • location")
                val parts = event.title.split(" • ", limit = 2)
                val subject = parts.getOrNull(0) ?: event.title
                val location = parts.getOrNull(1)

                // Line 3: short subject
                if (line3Baseline <= tmpRect.bottom - vertPad) {
                    drawEllipsized(canvas, subject, contentLeft, contentRight, line3Baseline, titleTextPaint)
                }
                // Line 4: auditorium/location
                if (!location.isNullOrEmpty() && line4Baseline <= tmpRect.bottom - vertPad) {
                    drawEllipsized(canvas, location, contentLeft, contentRight, line4Baseline, infoTextPaint)
                }
            }
        }
    }

    private fun drawEllipsized(canvas: Canvas, text: String, left: Float, right: Float, baselineY: Float, paint: TextPaint) {
        var drawText = text
        val maxW = right - left
        var width = paint.measureText(drawText)
        if (width > maxW) {
            // Rough ellipsize: drop chars until it fits and add ellipsis
            val ellipsis = "\u2026"
            var end = drawText.length
            while (end > 0 && width > maxW) {
                end -= 1
                drawText = text.substring(0, end) + ellipsis
                width = paint.measureText(drawText)
            }
        }
        canvas.drawText(drawText, left, baselineY, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            val hit = hitRects.firstOrNull { it.first.contains(x, y) }?.second
            if (hit != null) {
                onEventClick?.invoke(hit)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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

    private fun formatTimeRange(s: LocalTime, e: LocalTime): String {
        fun fmt(t: LocalTime) = String.format(Locale.getDefault(), "%02d:%02d", t.hour, t.minute)
        return fmt(s) + "–" + fmt(e)
    }

    // Produce a short label for type badges
    private fun shortTypeLabel(raw: String): String {
        val t = raw.trim().lowercase()
        return when {
            t.contains("лек") || t.contains("lec") || t == "лк" -> "Лек."
            t.contains("лаб") || t.contains("lab") || t == "лб" -> "Лаб."
            t.contains("практ") || t.contains("пз") || t.contains("prac") || t == "пр" -> "Практ."
            t.contains("сем") || t.contains("semin") -> "Сем."
            t.contains("конс") || t.contains("consult") -> "Конс."
            t.contains("ісп") || t.contains("екз") || t.contains("экз") || t.contains("exam") -> "Ісп."
            t.contains("залік") || t.contains("зач") || t.contains("credit") -> "Залік"
            t.contains("контр") || t.contains("тест") || t.contains("test") || t.contains("quiz") -> "Тест"
            t.contains("курсов") || t.contains("кп") || t.contains("course") -> "Курсов."
            t.contains("факульт") || t.contains("elective") || t.contains("optional") -> "Елек."
            else -> raw.split(" ", limit = 2).firstOrNull()?.let {
                if (it.length > 12) it.take(9) + "…" else it
            } ?: raw
        }
    }
}
