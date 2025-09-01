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
            countProvider = { date -> EventRepository.getCountForDate(ctx, date) }
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
                        color = null,
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
        prefetchMonthCache(java.time.YearMonth.from(calendarState.selectedDate))

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseStartOfWeek ?: calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val diff = position - WeekPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusWeeks(diff.toLong())
                // Prefetch month cache for the newly visible week
                prefetchMonthCache(java.time.YearMonth.from(calendarState.selectedDate))
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
                    DayScheduleView.DayEvent(
                        title = "${it.subject.title} • ${it.auditorium.name}",
                        start = it.start,
                        end = it.end,
                        color = null,
                        meta = it
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
        prefetchMonthCache(java.time.YearMonth.from(calendarState.selectedDate))

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseDate ?: calendarState.selectedDate
                val diff = position - DayPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusDays(diff.toLong())
                // Prefetch month cache for the newly visible day
                prefetchMonthCache(java.time.YearMonth.from(calendarState.selectedDate))
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
            val targetStart = calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeksDiff = ChronoUnit.WEEKS.between(base, targetStart).toInt()
            val target = WeekPagerAdapter.START_INDEX + weeksDiff
            if (binding.weekPager.currentItem != target) {
                binding.weekPager.setCurrentItem(target, false)
            }
        }
    }

    private fun positionDayPager() {
        if (dayPagerInitialised) {
            val base = pagerBaseDate ?: calendarState.selectedDate
            val daysDiff = ChronoUnit.DAYS.between(base, calendarState.selectedDate).toInt()
            val target = DayPagerAdapter.START_INDEX + daysDiff
            if (binding.dayPager.currentItem != target) {
                binding.dayPager.setCurrentItem(target, false)
            }
        }
    }

    private fun getOptimizedMonthIndex(ym: YearMonth): Int {
        return if (cachedMonthYearMonth == ym && cachedMonthIndex != -1) {
            cachedMonthIndex
        } else {
            val index = ym.year * 12 + ym.monthValue
            cachedMonthIndex = index
            cachedMonthYearMonth = ym
            index
        }
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
