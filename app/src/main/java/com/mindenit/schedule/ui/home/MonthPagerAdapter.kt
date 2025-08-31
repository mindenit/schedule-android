package com.mindenit.schedule.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Pager adapter that provides month pages with a 7x5/6 grid like Google Calendar.
 */
class MonthPagerAdapter(
    private val baseYearMonth: YearMonth,
    private val onDayClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<MonthPagerAdapter.MonthPageVH>() {

    companion object {
        // Center index so current month is in the middle; allow ~200 years of months
        const val TOTAL_MONTHS = 2400
        val START_INDEX = TOTAL_MONTHS / 2
    }

    override fun getItemCount(): Int = TOTAL_MONTHS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthPageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_month_page, parent, false)
        return MonthPageVH(v)
    }

    override fun onBindViewHolder(holder: MonthPageVH, position: Int) {
        val diff = position - START_INDEX
        val ym = baseYearMonth.plusMonths(diff.toLong())
        holder.bind(ym, onDayClick)
    }

    class MonthPageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val grid: RecyclerView = itemView.findViewById(R.id.month_grid)
        private var adapter: MonthAdapter? = null
        private var currentYearMonth: YearMonth? = null
        private var isLayoutManagerSet = false

        fun bind(ym: YearMonth, onDayClick: (LocalDate) -> Unit) {
            // Only regenerate data if month actually changed
            if (currentYearMonth == ym && adapter != null) {
                return
            }

            // Setup layout manager only once
            if (!isLayoutManagerSet) {
                grid.layoutManager = GridLayoutManager(itemView.context, 7)
                grid.setHasFixedSize(true)
                grid.itemAnimator = null // disable animations for performance
                isLayoutManagerSet = true
            }

            val data = generateMonthGrid(ym)

            // Create adapter only once and reuse it
            val monthAdapter = adapter ?: MonthAdapter(onDayClick).also {
                adapter = it
                grid.adapter = it
            }

            // Use optimized update method instead of forcing regeneration
            monthAdapter.updateDays(data.days)
            currentYearMonth = ym

            // Optimize height calculation - only do it once or when really needed
            if (monthAdapter.itemCount > 0) {
                grid.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        grid.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val totalH = grid.height
                        val rows = data.rows
                        if (totalH > 0 && rows > 0) {
                            val rowH = totalH / rows
                            monthAdapter.setItemHeight(rowH)
                        }
                    }
                })
            }
        }

        private data class MonthData(val days: List<CalendarDay>, val rows: Int)

        private fun generateMonthGrid(yearMonth: YearMonth): MonthData {
            val firstOfMonth = yearMonth.atDay(1)
            val lastOfMonth = yearMonth.atEndOfMonth()
            val start = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val days = mutableListOf<CalendarDay>()
            var d = start
            while (!d.isAfter(end)) {
                val inCurrent = d.month == yearMonth.month
                days += CalendarDay(d, inCurrentMonth = inCurrent, eventCount = 0)
                d = d.plusDays(1)
            }
            val rows = days.size / 7
            return MonthData(days, rows)
        }
    }
}
