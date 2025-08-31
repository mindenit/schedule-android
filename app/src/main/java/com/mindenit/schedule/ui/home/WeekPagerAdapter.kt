package com.mindenit.schedule.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

class WeekPagerAdapter(
    private val baseStartOfWeek: LocalDate
) : RecyclerView.Adapter<WeekPagerAdapter.WeekPageVH>() {

    companion object {
        const val TOTAL_WEEKS = 2000
        val START_INDEX = TOTAL_WEEKS / 2
    }

    override fun getItemCount(): Int = TOTAL_WEEKS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeekPageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_week_page, parent, false)
        return WeekPageVH(v)
    }

    override fun onBindViewHolder(holder: WeekPageVH, position: Int) {
        val diff = position - START_INDEX
        val start = baseStartOfWeek.plusWeeks(diff.toLong())
        holder.bind(start)
    }

    class WeekPageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val scroll: NestedScrollView = itemView.findViewById(R.id.week_scroll)
        private val weekView: WeekScheduleView = itemView.findViewById(R.id.week_view)
        private var currentStartOfWeek: LocalDate? = null

        fun bind(startOfWeek: LocalDate) {
            // Force update even if it's the same week to ensure visibility after tab switching
            weekView.setWeek(startOfWeek, emptyList())
            currentStartOfWeek = startOfWeek

            scroll.post {
                val nowY = weekView.getScrollYForTime(LocalTime.now())
                scroll.scrollTo(0, nowY)
            }
        }
    }
}
