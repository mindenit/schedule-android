package com.mindenit.schedule.ui.home

import android.util.Log
import com.mindenit.schedule.databinding.FragmentHomeBinding
import com.mindenit.schedule.data.EventRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mindenit.schedule.R
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition

/**
 * Управління календарними режимами та їх ініціалізацією
 */
class CalendarManager(
    private val binding: FragmentHomeBinding,
    private val calendarState: CalendarState,
    private val calendarLoader: CalendarLoader,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

    // Month CalendarView state
    private var monthCalendarInitialized = false
    private var calendarBaseYearMonth: YearMonth? = null

    private var weekPagerAdapter: WeekPagerAdapter? = null
    private var weekPagerInitialed = false
    private var pagerBaseStartOfWeek: LocalDate? = null

    private var dayPagerAdapter: DayPagerAdapter? = null
    private var dayPagerInitialed = false
    private var pagerBaseDate: LocalDate? = null

    // Optimization cache
    private var cachedMonthIndex: Int = -1
    private var cachedMonthYearMonth: YearMonth? = null

    private val logTag = "CalendarManager"

    // Callback to notify that header (title) should be updated
    var onHeaderUpdate: (() -> Unit)? = null

    /**
     * Налаштовує відповідний режим з оптимізованим завантаженням
     */
    fun setupPagerWithLoading(mode: CalendarState.ViewMode, onDateClick: (LocalDate) -> Unit) {
        when (mode) {
            CalendarState.ViewMode.MONTH -> setupMonthCalendarWithLoading(onDateClick)
            CalendarState.ViewMode.WEEK -> setupWeekPagerWithLoading()
            CalendarState.ViewMode.DAY -> setupDayPagerWithLoading()
        }
    }

    private fun setupMonthCalendarWithLoading(onDateClick: (LocalDate) -> Unit) {
        if (monthCalendarInitialized && calendarState.hasEverBeenInitialized) {
            calendarLoader.showCalendarInstantly(CalendarState.ViewMode.MONTH)
            return
        }

        val showedLoader = calendarLoader.showLoadingIfNeeded(calendarState, CalendarState.ViewMode.MONTH)
        val delay = if (calendarState.isFirstTimeLoad) 200L else 0L
        binding.root.postDelayed({
            setupMonthCalendarIfNeeded(onDateClick)
            if (showedLoader) {
                calendarLoader.hideLoading(calendarState) {
                    calendarLoader.showCalendarInstantly(CalendarState.ViewMode.MONTH)
                    onHeaderUpdate?.invoke()
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.MONTH)
                onHeaderUpdate?.invoke()
            }
        }, delay)
    }

    private fun setupMonthCalendarIfNeeded(onDateClick: (LocalDate) -> Unit) {
        if (monthCalendarInitialized) return
        Log.d(logTag, "setupMonthCalendarIfNeeded: init with base=${calendarState.selectedYearMonth}")

        val cv: CalendarView = binding.calendarView
        calendarBaseYearMonth = calendarState.selectedYearMonth

        val startMonth = calendarState.selectedYearMonth.minusYears(100)
        val endMonth = calendarState.selectedYearMonth.plusYears(100)
        val firstDayOfWeek = DayOfWeek.MONDAY // Fixed Monday as in UI header

        // Ensure visible before setup to avoid layout glitches
        binding.weekdayHeader.isVisible = true
        binding.calendarView.isVisible = true

        cv.setup(startMonth, endMonth, firstDayOfWeek)
        cv.scrollToMonth(calendarState.selectedYearMonth)
        // Use our day layout (already set in XML too)
        cv.dayViewResource = R.layout.item_calendar_day

        // Prefetch initial month and neighbors
        prefetchMonthCache(calendarState.selectedYearMonth)

        // Day binder with our existing styles and badges
        cv.dayBinder = object : MonthDayBinder<MonthDayContainer> {
            override fun create(view: android.view.View): MonthDayContainer = MonthDayContainer(view)
            override fun bind(container: MonthDayContainer, day: CalendarDay) {
                val date = day.date
                val inCurrentMonth = day.position == DayPosition.MonthDate
                val badges = provideBadges(date)
                container.bind(date, inCurrentMonth, badges, onDateClick)
            }
        }

        // Update title and cache on month scroll
        cv.monthScrollListener = { month ->
            calendarState.selectedYearMonth = month.yearMonth
            prefetchMonthCache(month.yearMonth)
            onHeaderUpdate?.invoke()
        }

        // Force initial rebind to ensure cells are drawn on first show
        cv.post { cv.notifyCalendarChanged() }

        monthCalendarInitialized = true
    }

    private fun provideBadges(date: LocalDate): List<SubjectBadge> {
        val ctx = binding.root.context
        val events = EventRepository.getEventsForDateFast(ctx, date)
        if (events.isEmpty()) return emptyList()
        val grouped = events.groupBy { ev -> ev.subject.brief.ifBlank { ev.subject.title } }
        val sorted = grouped.entries.sortedBy { it.key }.map { (label, list) ->
            val dominantType = list
                .groupingBy { it.type.lowercase() }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: ""
            SubjectBadge(label = label, color = darkColorForType(dominantType, seed = label))
        }
        // Cap badges to at most 3 and show overflow counter
        val maxBadges = 3
        return if (sorted.size <= maxBadges) sorted else sorted.take(maxBadges - 1) + listOf(
            SubjectBadge(label = "+${sorted.size - (maxBadges - 1)}", color = Color.parseColor("#9E9E9E"))
        )
    }

    // Container for one day cell matching item_calendar_day
    private inner class MonthDayContainer(view: android.view.View) : ViewContainer(view) {
        private val container: android.view.View = view.findViewById(R.id.day_container)
        private val dayNumber: TextView = view.findViewById(R.id.text_day_number)
        private val eventsContainer: LinearLayout = view.findViewById(R.id.events_container)

        // Cached visuals
        private var todayBackground: GradientDrawable? = null
        private var onPrimaryColor: Int = 0
        private var todaySize: Int = 0
        private var onBackgroundColor: Int = 0
        private var initialized = false

        private fun ensureInit() {
            if (initialized) return
            val ctx = view.context
            val primaryColor = ContextCompat.getColor(ctx, R.color.purple_500)
            onPrimaryColor = ContextCompat.getColor(ctx, android.R.color.white)
            onBackgroundColor = ContextCompat.getColor(ctx, R.color.calendar_on_background)
            todaySize = dpToPx(28f)
            todayBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = todaySize / 2f
                setColor(primaryColor)
            }
            initialized = true
        }

        fun bind(date: LocalDate, inCurrentMonth: Boolean, badges: List<SubjectBadge>, onClick: (LocalDate) -> Unit) {
            ensureInit()
            dayNumber.text = date.dayOfMonth.toString()
            view.alpha = if (inCurrentMonth) 1.0f else 0.38f

            // Today highlighting
            if (date == LocalDate.now()) {
                dayNumber.background = todayBackground
                dayNumber.setTextColor(onPrimaryColor)
                dayNumber.layoutParams = dayNumber.layoutParams.apply {
                    width = todaySize
                    height = todaySize
                }
            } else {
                dayNumber.background = null
                dayNumber.setTextColor(onBackgroundColor)
                // Reset to wrap content
                dayNumber.layoutParams = dayNumber.layoutParams.apply {
                    width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }

            // Click
            container.setOnClickListener { onClick(date) }

            // Badges
            if (eventsContainer.childCount > 0) eventsContainer.removeAllViews()
            if (badges.isNotEmpty()) {
                val corner = dpToPx(4f).toFloat()
                val strokeW = dpToPx(1f)
                val strokeColor = Color.parseColor("#33000000")
                badges.forEach { badge ->
                    val ctx = view.context
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = corner
                            setColor(badge.color)
                            setStroke(strokeW, strokeColor)
                        }
                        layoutParams = LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = dpToPx(2f)
                        }
                        setPadding(dpToPx(6f), dpToPx(3f), dpToPx(6f), dpToPx(3f))
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val label = TextView(ctx).apply {
                        text = badge.label
                        setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        isSingleLine = true
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    row.addView(label)
                    eventsContainer.addView(row)
                }
            }
        }

        private fun dpToPx(dp: Float): Int = (dp * view.resources.displayMetrics.density).toInt()
    }

    private fun darkColorForType(typeLower: String, seed: String? = null): Int {
        val t = typeLower.trim().lowercase()
        fun hasAny(vararg keys: String) = keys.any { k -> t.contains(k) }
        return when {
            // Лекція (lecture)
            hasAny("лек", "lec", "lecture", "лк") -> Color.parseColor("#0B8043") // green 700
            // Лабораторна (lab)
            hasAny("лаб", "lab", "laboratory", "лб") -> Color.parseColor("#1E3A8A") // indigo 800
            // Практика (practice, PZ)
            hasAny("практ", "пз", "prac", "practice", " pr ", " pr.") -> Color.parseColor("#B45309") // orange 700
            // Семінар (seminar)
            hasAny("сем", "semin") -> Color.parseColor("#6A1B9A") // purple 800
            // Консультація (consultation)
            hasAny("конс", "consult") -> Color.parseColor("#006064") // cyan 900
            // Іспит (exam)
            hasAny("ісп", "екз", "экз", "exam", "examin") -> Color.parseColor("#C62828") // red 800
            // Залік (credit)
            hasAny("залік", "зач", "credit", "pass/fail", "passfail") -> Color.parseColor("#00695C") // teal 800
            // Контрольна/Тест
            hasAny("контр", "тест", "test", "quiz") -> Color.parseColor("#FF6F00") // amber 800
            // Курсова/Курсовий проєкт
            hasAny("курсов", "кп", "кпп", "course work", "course project", "cw ", " cp ") -> Color.parseColor("#4E342E") // brown 800
            // Факультатив/Електив
            hasAny("факульт", "фак ", "elective", "optional") -> Color.parseColor("#1565C0") // blue 700
            t.isNotEmpty() -> {
                // Unknown type: pick from a spaced dark palette based on hash
                val palette = intArrayOf(
                    Color.parseColor("#0B8043"), // green 700
                    Color.parseColor("#1E3A8A"), // indigo 800
                    Color.parseColor("#B45309"), // orange 700
                    Color.parseColor("#6A1B9A"), // purple 800
                    Color.parseColor("#C62828"), // red 800
                    Color.parseColor("#00695C"), // teal 800
                    Color.parseColor("#4E342E"), // brown 800
                    Color.parseColor("#1565C0"), // blue 700
                    Color.parseColor("#2E7D32"), // green 800
                    Color.parseColor("#AD1457"), // pink 800
                    Color.parseColor("#283593"), // indigo 800 (alt)
                    Color.parseColor("#00838F")  // cyan 800
                )
                val idx = kotlin.math.abs(t.hashCode()) % palette.size
                palette[idx]
            }
            else -> {
                val palette = intArrayOf(
                    Color.parseColor("#0B8043"),
                    Color.parseColor("#1E3A8A"),
                    Color.parseColor("#B45309"),
                    Color.parseColor("#6A1B9A"),
                    Color.parseColor("#C62828"),
                    Color.parseColor("#00695C"),
                    Color.parseColor("#4E342E"),
                    Color.parseColor("#1565C0"),
                    Color.parseColor("#2E7D32"),
                    Color.parseColor("#AD1457"),
                    Color.parseColor("#283593"),
                    Color.parseColor("#00838F")
                )
                val s = seed?.lowercase().orEmpty()
                val idx = if (s.isNotEmpty()) kotlin.math.abs(s.hashCode()) % palette.size else 0
                palette[idx]
            }
        }
    }

    private fun setupWeekPagerWithLoading() {
        if (weekPagerInitialed && calendarState.hasEverBeenInitialized) {
            calendarLoader.showCalendarInstantly(CalendarState.ViewMode.WEEK)
            return
        }

        val showedLoader = calendarLoader.showLoadingIfNeeded(calendarState, CalendarState.ViewMode.WEEK)

        val delay = if (calendarState.isFirstTimeLoad) 150L else 0L
        binding.root.postDelayed({
            setupWeekPagerIfNeeded()
            if (showedLoader) {
                calendarLoader.hideLoading(calendarState) {
                    calendarLoader.showCalendarInstantly(CalendarState.ViewMode.WEEK)
                    onHeaderUpdate?.invoke()
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.WEEK)
                onHeaderUpdate?.invoke()
            }
        }, delay)
    }

    private fun setupDayPagerWithLoading() {
        if (dayPagerInitialed && calendarState.hasEverBeenInitialized) {
            calendarLoader.showCalendarInstantly(CalendarState.ViewMode.DAY)
            return
        }

        val showedLoader = calendarLoader.showLoadingIfNeeded(calendarState, CalendarState.ViewMode.DAY)

        val delay = if (calendarState.isFirstTimeLoad) 100L else 0L
        binding.root.postDelayed({
            setupDayPagerIfNeeded()
            if (showedLoader) {
                calendarLoader.hideLoading(calendarState) {
                    calendarLoader.showCalendarInstantly(CalendarState.ViewMode.DAY)
                    onHeaderUpdate?.invoke()
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.DAY)
                onHeaderUpdate?.invoke()
            }
        }, delay)
    }

    private fun setupWeekPagerIfNeeded() {
        if (weekPagerInitialed) return
        Log.d(logTag, "setupWeekPagerIfNeeded: init with startOfWeek")

        val ctx = binding.root.context
        val pager = binding.weekPager
        val startOfWeek = calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        pagerBaseStartOfWeek = startOfWeek
        weekPagerAdapter = WeekPagerAdapter(startOfWeek,
            eventsProvider = { start ->
                val events = EventRepository.getEventsForWeekFast(ctx, start)
                events.map {
                    val subj = it.subject.brief.ifBlank { it.subject.title }
                    WeekScheduleView.WeekEvent(
                        title = "$subj • ${it.auditorium.name}",
                        start = it.start,
                        end = it.end,
                        color = darkColorForType(it.type, seed = subj),
                        meta = it
                    )
                }
            },
            onEventClicked = { we ->
                val domain = we.meta as? com.mindenit.schedule.data.Event ?: return@WeekPagerAdapter
                val fm = (binding.root.context as? androidx.appcompat.app.AppCompatActivity)?.supportFragmentManager ?: return@WeekPagerAdapter
                EventDetailsBottomSheet.from(domain).show(fm, "event_details")
            }
        )
        pager.adapter = weekPagerAdapter
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(WeekPagerAdapter.START_INDEX, false)
        onHeaderUpdate?.invoke()

        // Ensure cache for the month of initial selected date
        prefetchMonthCache(YearMonth.from(calendarState.selectedDate))

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseStartOfWeek ?: calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                calendarState.selectedDate = base.plusWeeks((position - WeekPagerAdapter.START_INDEX).toLong())
                prefetchMonthCache(YearMonth.from(calendarState.selectedDate))
                onHeaderUpdate?.invoke()
            }
        })
        weekPagerInitialed = true
    }

    private fun setupDayPagerIfNeeded() {
        if (dayPagerInitialed) return
        Log.d(logTag, "setupDayPagerIfNeeded: init with baseDate=${calendarState.selectedDate}")

        val ctx = binding.root.context
        val pager = binding.dayPager
        pagerBaseDate = calendarState.selectedDate
        dayPagerAdapter = DayPagerAdapter(pagerBaseDate!!,
            eventsProvider = { date ->
                val events = EventRepository.getEventsForDateFast(ctx, date)
                events.map {
                    val subj = it.subject.brief.ifBlank { it.subject.title }
                    DayScheduleView.DayEvent(
                        title = "$subj • ${it.auditorium.name}",
                        start = it.start,
                        end = it.end,
                        color = darkColorForType(it.type, seed = subj),
                        meta = it,
                        type = it.type
                    )
                }
            },
            onEventClicked = { de ->
                val domain = de.meta as? com.mindenit.schedule.data.Event ?: return@DayPagerAdapter
                val fm = (binding.root.context as? androidx.appcompat.app.AppCompatActivity)?.supportFragmentManager ?: return@DayPagerAdapter
                EventDetailsBottomSheet.from(domain).show(fm, "event_details")
            }
        )
        pager.adapter = dayPagerAdapter
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(DayPagerAdapter.START_INDEX, false)
        onHeaderUpdate?.invoke()

        // Ensure cache for the month of initial day
        prefetchMonthCache(YearMonth.from(calendarState.selectedDate))

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseDate ?: calendarState.selectedDate
                val diff = position - DayPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusDays(diff.toLong())
                prefetchMonthCache(YearMonth.from(calendarState.selectedDate))
                onHeaderUpdate?.invoke()
            }
        })
        dayPagerInitialed = true
    }

    // Prefetch current and adjacent months with daily guard inside repository
    private fun prefetchMonthCache(ym: YearMonth) {
        val ctx = binding.root.context
        scope.launch(Dispatchers.IO) {
            var cachedCurrent = false
            try {
                Log.d(logTag, "Starting prefetch for month: $ym")
                EventRepository.ensureMonthCached(ctx, ym)
                cachedCurrent = true
                Log.d(logTag, "Successfully cached month: $ym")
            } catch (e: Throwable) {
                Log.e(logTag, "Failed to cache month: $ym", e)
            }
            // Prefetch neighbors to minimize future waits
            try { EventRepository.ensureMonthCached(ctx, ym.minusMonths(1)) } catch (_: Throwable) {}
            try { EventRepository.ensureMonthCached(ctx, ym.plusMonths(1)) } catch (_: Throwable) {}

            if (cachedCurrent) {
                withContext(Dispatchers.Main) {
                    Log.d(logTag, "Refreshing UI after cache update")
                    // Refresh month view
                    if (monthCalendarInitialized) {
                        binding.calendarView.notifyCalendarChanged()
                    }
                    // If the selected date is in this month, refresh week/day pages too
                    if (YearMonth.from(calendarState.selectedDate) == ym) {
                        weekPagerAdapter?.let {
                            Log.d(logTag, "Notifying week adapter item changed")
                            binding.weekPager.adapter?.notifyItemChanged(binding.weekPager.currentItem)
                        }
                        dayPagerAdapter?.let {
                            Log.d(logTag, "Notifying day adapter item changed")
                            binding.dayPager.adapter?.notifyItemChanged(binding.dayPager.currentItem)
                        }
                    }
                }
            }
        }
    }

    /**
     * Позиціонує календар у правильну позицію
     */
    fun positionPager(mode: CalendarState.ViewMode) {
        when (mode) {
            CalendarState.ViewMode.MONTH -> positionMonthCalendar()
            CalendarState.ViewMode.WEEK -> positionWeekPager()
            CalendarState.ViewMode.DAY -> positionDayPager()
        }
    }

    private fun positionMonthCalendar() {
        if (monthCalendarInitialized) {
            binding.calendarView.scrollToMonth(calendarState.selectedYearMonth)
        }
    }

    private fun positionWeekPager() {
        if (weekPagerInitialed) {
            val base = pagerBaseStartOfWeek ?: calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val diff = ChronoUnit.WEEKS.between(base, calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt()
            val target = WeekPagerAdapter.START_INDEX + diff
            if (binding.weekPager.currentItem != target) {
                binding.weekPager.setCurrentItem(target, false)
            }
        }
    }

    private fun positionDayPager() {
        if (dayPagerInitialed) {
            val base = pagerBaseDate ?: calendarState.selectedDate
            val diff = ChronoUnit.DAYS.between(base, calendarState.selectedDate).toInt()
            val target = DayPagerAdapter.START_INDEX + diff
            if (binding.dayPager.currentItem != target) {
                binding.dayPager.setCurrentItem(target, false)
            }
        }
    }

    /**
     * Перевіряє чи ініціалізований режим для вказаного ViewMode
     */
    fun isPagerInitialized(mode: CalendarState.ViewMode): Boolean {
        return when (mode) {
            CalendarState.ViewMode.MONTH -> monthCalendarInitialized
            CalendarState.ViewMode.WEEK -> weekPagerInitialed && weekPagerAdapter != null
            CalendarState.ViewMode.DAY -> dayPagerInitialed && dayPagerAdapter != null
        }
    }

    /**
     * Очищає адаптери при знищенні view
     */
    fun cleanup() {
        weekPagerAdapter = null
        dayPagerAdapter = null
        monthCalendarInitialized = false
        weekPagerInitialed = false
        dayPagerInitialed = false
        cachedMonthIndex = -1
        cachedMonthYearMonth = null
    }

    fun refreshVisible() {
        // Rebind visible parts without altering scroll positions
        if (monthCalendarInitialized) {
            binding.calendarView.notifyCalendarChanged()
        }
        weekPagerAdapter?.let {
            binding.weekPager.adapter?.notifyItemChanged(binding.weekPager.currentItem)
        }
        dayPagerAdapter?.let {
            binding.dayPager.adapter?.notifyItemChanged(binding.dayPager.currentItem)
        }
        onHeaderUpdate?.invoke()
    }

    /**
     * Jump to today's date depending on current mode and refresh UI.
     */
    fun goToToday(mode: CalendarState.ViewMode = calendarState.viewMode) {
        when (mode) {
            CalendarState.ViewMode.MONTH -> {
                val ym = YearMonth.now()
                calendarState.selectedYearMonth = ym
                binding.calendarView.scrollToMonth(ym)
                binding.calendarView.post { binding.calendarView.notifyCalendarChanged() }
            }
            CalendarState.ViewMode.WEEK -> {
                calendarState.selectedDate = LocalDate.now()
                positionWeekPager()
                weekPagerAdapter?.let { binding.weekPager.adapter?.notifyItemChanged(binding.weekPager.currentItem) }
            }
            CalendarState.ViewMode.DAY -> {
                calendarState.selectedDate = LocalDate.now()
                positionDayPager()
                dayPagerAdapter?.let { binding.dayPager.adapter?.notifyItemChanged(binding.dayPager.currentItem) }
            }
        }
        onHeaderUpdate?.invoke()
    }
}
