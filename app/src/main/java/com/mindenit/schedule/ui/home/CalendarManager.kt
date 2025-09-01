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

/**
 * Управління календарними pager-ами та їх ініціалізацією
 */
class CalendarManager(
    private val binding: FragmentHomeBinding,
    private val calendarState: CalendarState,
    private val calendarLoader: CalendarLoader,
    private val scope: kotlinx.coroutines.CoroutineScope
) {

    // Pager adapters and state
    private var monthPagerAdapter: MonthPagerAdapter? = null
    private var pagerInitialised = false
    private var pagerBaseYearMonth: YearMonth? = null

    private var weekPagerAdapter: WeekPagerAdapter? = null
    private var weekPagerInitialised = false
    private var pagerBaseStartOfWeek: LocalDate? = null

    private var dayPagerAdapter: DayPagerAdapter? = null
    private var dayPagerInitialised = false
    private var pagerBaseDate: LocalDate? = null

    // Optimization cache
    private var cachedMonthIndex: Int = -1
    private var cachedMonthYearMonth: YearMonth? = null

    private val logTag = "CalendarManager"

    // Callback to notify that header (title) should be updated
    var onHeaderUpdate: (() -> Unit)? = null

    /**
     * Налаштовує відповідний pager з оптимізованим завантаженням
     */
    fun setupPagerWithLoading(mode: CalendarState.ViewMode, onDateClick: (LocalDate) -> Unit) {
        when (mode) {
            CalendarState.ViewMode.MONTH -> setupMonthPagerWithLoading(onDateClick)
            CalendarState.ViewMode.WEEK -> setupWeekPagerWithLoading()
            CalendarState.ViewMode.DAY -> setupDayPagerWithLoading()
        }
    }

    private fun setupMonthPagerWithLoading(onDateClick: (LocalDate) -> Unit) {
        // Якщо вже ініціалізовано - миттєво показуємо
        if (pagerInitialised && calendarState.hasEverBeenInitialized) {
            calendarLoader.showCalendarInstantly(CalendarState.ViewMode.MONTH)
            return
        }

        // Показуємо лоадер тільки при першому завантаженні
        val showedLoader = calendarLoader.showLoadingIfNeeded(calendarState, CalendarState.ViewMode.MONTH)

        // Швидка ініціалізація
        val delay = if (calendarState.isFirstTimeLoad) 200L else 0L
        binding.root.postDelayed({
            setupMonthPagerIfNeeded(onDateClick)
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

    private fun setupWeekPagerWithLoading() {
        if (weekPagerInitialised && calendarState.hasEverBeenInitialized) {
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
        if (dayPagerInitialised && calendarState.hasEverBeenInitialized) {
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

    private fun setupMonthPagerIfNeeded(onDateClick: (LocalDate) -> Unit) {
        if (pagerInitialised) return
        Log.d(logTag, "setupMonthPagerIfNeeded: init with base=${calendarState.selectedYearMonth}")

        val ctx = binding.root.context
        val pager = binding.calendarPager
        pagerBaseYearMonth = calendarState.selectedYearMonth
        monthPagerAdapter = MonthPagerAdapter(
            pagerBaseYearMonth!!,
            onDateClick,
            badgesProvider = { date ->
                val events = EventRepository.getEventsForDate(ctx, date)
                // Group by subject label for one badge per subject
                val grouped = events.groupBy { ev -> ev.subject.brief.ifBlank { ev.subject.title } }
                grouped.entries.sortedBy { it.key }.map { (label, list) ->
                    // Determine dominant type within the subject for the day
                    val dominantType = list
                        .groupingBy { it.type.lowercase() }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key ?: ""
                    SubjectBadge(label = label, color = darkColorForType(dominantType, seed = label))
                }
            }
        )
        pager.adapter = monthPagerAdapter
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(MonthPagerAdapter.START_INDEX, false)
        onHeaderUpdate?.invoke()

        // Ensure cache for initial month and neighbors
        prefetchMonthCache(calendarState.selectedYearMonth)

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseYearMonth ?: calendarState.selectedYearMonth
                val diff = position - MonthPagerAdapter.START_INDEX
                calendarState.selectedYearMonth = base.plusMonths(diff.toLong())
                // Prefetch cache for current and adjacent months
                prefetchMonthCache(calendarState.selectedYearMonth)
                // Notify header update
                onHeaderUpdate?.invoke()
            }
        })
        pagerInitialised = true
    }

    private fun darkColorForType(typeLower: String, seed: String? = null): Int {
        val t = typeLower.trim().lowercase()
        fun hasAny(vararg keys: String) = keys.any { k -> t.contains(k) }
        return when {
            // Лекція (lecture)
            hasAny("лек", "lec", "lecture", "лк") -> android.graphics.Color.parseColor("#0B8043") // green 700
            // Лабораторна (lab)
            hasAny("лаб", "lab", "laboratory", "лб") -> android.graphics.Color.parseColor("#1E3A8A") // indigo 800
            // Практика (practice, PZ)
            hasAny("практ", "пз", "prac", "practice", " pr ", " pr.") -> android.graphics.Color.parseColor("#B45309") // orange 700
            // Семінар (seminar)
            hasAny("сем", "semin") -> android.graphics.Color.parseColor("#6A1B9A") // purple 800
            // Консультація (consultation)
            hasAny("конс", "consult") -> android.graphics.Color.parseColor("#006064") // cyan 900
            // Іспит (exam)
            hasAny("ісп", "екз", "экз", "exam", "examin") -> android.graphics.Color.parseColor("#C62828") // red 800
            // Залік (credit)
            hasAny("залік", "зач", "credit", "pass/fail", "passfail") -> android.graphics.Color.parseColor("#00695C") // teal 800
            // Контрольна/Тест
            hasAny("контр", "тест", "test", "quiz") -> android.graphics.Color.parseColor("#FF6F00") // amber 800
            // Курсова/Курсовий проєкт
            hasAny("курсов", "кп", "кпп", "course work", "course project", "cw ", " cp ") -> android.graphics.Color.parseColor("#4E342E") // brown 800
            // Факультатив/Електив
            hasAny("факульт", "фак ", "elective", "optional") -> android.graphics.Color.parseColor("#1565C0") // blue 700
            t.isNotEmpty() -> {
                // Unknown type: pick from a spaced dark palette based on hash
                val palette = intArrayOf(
                    android.graphics.Color.parseColor("#0B8043"), // green 700
                    android.graphics.Color.parseColor("#1E3A8A"), // indigo 800
                    android.graphics.Color.parseColor("#B45309"), // orange 700
                    android.graphics.Color.parseColor("#6A1B9A"), // purple 800
                    android.graphics.Color.parseColor("#C62828"), // red 800
                    android.graphics.Color.parseColor("#00695C"), // teal 800
                    android.graphics.Color.parseColor("#4E342E"), // brown 800
                    android.graphics.Color.parseColor("#1565C0"), // blue 700
                    android.graphics.Color.parseColor("#2E7D32"), // green 800
                    android.graphics.Color.parseColor("#AD1457"), // pink 800
                    android.graphics.Color.parseColor("#283593"), // indigo 800 (alt)
                    android.graphics.Color.parseColor("#00838F")  // cyan 800
                )
                val idx = kotlin.math.abs(t.hashCode()) % palette.size
                palette[idx]
            }
            else -> {
                val palette = intArrayOf(
                    android.graphics.Color.parseColor("#0B8043"),
                    android.graphics.Color.parseColor("#1E3A8A"),
                    android.graphics.Color.parseColor("#B45309"),
                    android.graphics.Color.parseColor("#6A1B9A"),
                    android.graphics.Color.parseColor("#C62828"),
                    android.graphics.Color.parseColor("#00695C"),
                    android.graphics.Color.parseColor("#4E342E"),
                    android.graphics.Color.parseColor("#1565C0"),
                    android.graphics.Color.parseColor("#2E7D32"),
                    android.graphics.Color.parseColor("#AD1457"),
                    android.graphics.Color.parseColor("#283593"),
                    android.graphics.Color.parseColor("#00838F")
                )
                val s = seed?.lowercase().orEmpty()
                val idx = if (s.isNotEmpty()) kotlin.math.abs(s.hashCode()) % palette.size else 0
                palette[idx]
            }
        }
    }

    private fun setupWeekPagerIfNeeded() {
        if (weekPagerInitialised) return
        Log.d(logTag, "setupWeekPagerIfNeeded: init with startOfWeek")

        val ctx = binding.root.context
        val pager = binding.weekPager
        val startOfWeek = calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        pagerBaseStartOfWeek = startOfWeek
        weekPagerAdapter = WeekPagerAdapter(startOfWeek,
            eventsProvider = { start ->
                val events = EventRepository.getEventsForWeek(ctx, start)
                events.map {
                    val subj = it.subject.brief.ifBlank { it.subject.title }
                    val aud = it.auditorium.name
                    WeekScheduleView.WeekEvent(
                        title = "$subj • $aud",
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
                val diff = position - WeekPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusWeeks(diff.toLong())
                // Prefetch month cache for the newly visible week
                prefetchMonthCache(YearMonth.from(calendarState.selectedDate))
                // Notify header update
                onHeaderUpdate?.invoke()
            }
        })
        weekPagerInitialised = true
    }

    private fun setupDayPagerIfNeeded() {
        if (dayPagerInitialised) return
        Log.d(logTag, "setupDayPagerIfNeeded: init with baseDate=${calendarState.selectedDate}")

        val ctx = binding.root.context
        val pager = binding.dayPager
        pagerBaseDate = calendarState.selectedDate
        dayPagerAdapter = DayPagerAdapter(pagerBaseDate!!,
            eventsProvider = { date ->
                val events = EventRepository.getEventsForDate(ctx, date)
                events.map {
                    val subj = it.subject.brief.ifBlank { it.subject.title }
                    DayScheduleView.DayEvent(
                        title = "${it.subject.title} • ${it.auditorium.name}",
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
                // Prefetch month cache for the newly visible day
                prefetchMonthCache(YearMonth.from(calendarState.selectedDate))
                // Notify header update
                onHeaderUpdate?.invoke()
            }
        })
        dayPagerInitialised = true
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
                    // Refresh visible month page
                    monthPagerAdapter?.let {
                        Log.d(logTag, "Notifying month adapter item changed")
                        binding.calendarPager.adapter?.notifyItemChanged(binding.calendarPager.currentItem)
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
            CalendarState.ViewMode.MONTH -> positionMonthPager()
            CalendarState.ViewMode.WEEK -> positionWeekPager()
            CalendarState.ViewMode.DAY -> positionDayPager()
        }
    }

    private fun positionMonthPager() {
        if (pagerInitialised) {
            val base = pagerBaseYearMonth ?: calendarState.selectedYearMonth
            val diff = getOptimizedMonthIndex(calendarState.selectedYearMonth) - getOptimizedMonthIndex(base)
            val target = MonthPagerAdapter.START_INDEX + diff
            if (binding.calendarPager.currentItem != target) {
                binding.calendarPager.setCurrentItem(target, false)
            }
        }
    }

    private fun positionWeekPager() {
        if (weekPagerInitialised) {
            val base = pagerBaseStartOfWeek ?: calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val diff = ChronoUnit.WEEKS.between(base, calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt()
            val target = WeekPagerAdapter.START_INDEX + diff
            if (binding.weekPager.currentItem != target) {
                binding.weekPager.setCurrentItem(target, false)
            }
        }
    }

    private fun positionDayPager() {
        if (dayPagerInitialised) {
            val base = pagerBaseDate ?: calendarState.selectedDate
            val diff = ChronoUnit.DAYS.between(base, calendarState.selectedDate).toInt()
            val target = DayPagerAdapter.START_INDEX + diff
            if (binding.dayPager.currentItem != target) {
                binding.dayPager.setCurrentItem(target, false)
            }
        }
    }

    private fun getOptimizedMonthIndex(ym: YearMonth): Int {
        // Cache last computed month index to avoid repeated calculations
        if (cachedMonthYearMonth == ym && cachedMonthIndex >= 0) {
            return cachedMonthIndex
        }
        val base = pagerBaseYearMonth ?: calendarState.selectedYearMonth
        val diff = ChronoUnit.MONTHS.between(base, ym).toInt()
        cachedMonthIndex = MonthPagerAdapter.START_INDEX + diff
        cachedMonthYearMonth = ym
        return cachedMonthIndex
    }

    /**
     * Перевіряє чи і��іціалізований pager для вказаного режиму
     */
    fun isPagerInitialized(mode: CalendarState.ViewMode): Boolean {
        return when (mode) {
            CalendarState.ViewMode.MONTH -> pagerInitialised && monthPagerAdapter != null
            CalendarState.ViewMode.WEEK -> weekPagerInitialised && weekPagerAdapter != null
            CalendarState.ViewMode.DAY -> dayPagerInitialised && dayPagerAdapter != null
        }
    }

    /**
     * Очищає адаптери при знищенні view
     */
    fun cleanup() {
        monthPagerAdapter = null
        weekPagerAdapter = null
        dayPagerAdapter = null
        pagerInitialised = false
        weekPagerInitialised = false
        dayPagerInitialised = false
        cachedMonthIndex = -1
        cachedMonthYearMonth = null
    }
}
