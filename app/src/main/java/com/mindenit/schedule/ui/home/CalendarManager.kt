package com.mindenit.schedule.ui.home

import android.util.Log
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

/**
 * Управління календарними pager-ами та їх ініціалізацією
 */
class CalendarManager(
    private val binding: FragmentHomeBinding,
    private val calendarState: CalendarState,
    private val calendarLoader: CalendarLoader
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
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.MONTH)
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
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.WEEK)
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
                }
            } else {
                calendarLoader.showCalendarInstantly(CalendarState.ViewMode.DAY)
            }
        }, delay)
    }

    private fun setupMonthPagerIfNeeded(onDateClick: (LocalDate) -> Unit) {
        if (pagerInitialised) return
        Log.d(logTag, "setupMonthPagerIfNeeded: init with base=${calendarState.selectedYearMonth}")

        val pager = binding.calendarPager
        pagerBaseYearMonth = calendarState.selectedYearMonth
        monthPagerAdapter = MonthPagerAdapter(pagerBaseYearMonth!!, onDateClick)
        pager.adapter = monthPagerAdapter
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(MonthPagerAdapter.START_INDEX, false)

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseYearMonth ?: calendarState.selectedYearMonth
                val diff = position - MonthPagerAdapter.START_INDEX
                calendarState.selectedYearMonth = base.plusMonths(diff.toLong())
            }
        })
        pagerInitialised = true
    }

    private fun setupWeekPagerIfNeeded() {
        if (weekPagerInitialised) return
        Log.d(logTag, "setupWeekPagerIfNeeded: init with startOfWeek")

        val pager = binding.weekPager
        val startOfWeek = calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        pagerBaseStartOfWeek = startOfWeek
        weekPagerAdapter = WeekPagerAdapter(startOfWeek)
        pager.adapter = weekPagerAdapter
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(WeekPagerAdapter.START_INDEX, false)

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseStartOfWeek ?: calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val diff = position - WeekPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusWeeks(diff.toLong())
            }
        })
        weekPagerInitialised = true
    }

    private fun setupDayPagerIfNeeded() {
        if (dayPagerInitialised) return
        Log.d(logTag, "setupDayPagerIfNeeded: init with baseDate=${calendarState.selectedDate}")

        val pager = binding.dayPager
        pagerBaseDate = calendarState.selectedDate
        dayPagerAdapter = DayPagerAdapter(pagerBaseDate!!)
        pager.adapter = dayPagerAdapter
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(DayPagerAdapter.START_INDEX, false)

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseDate ?: calendarState.selectedDate
                val diff = position - DayPagerAdapter.START_INDEX
                calendarState.selectedDate = base.plusDays(diff.toLong())
            }
        })
        dayPagerInitialised = true
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
     * Перевіряє чи ініціалізований pager для вказаного режиму
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
