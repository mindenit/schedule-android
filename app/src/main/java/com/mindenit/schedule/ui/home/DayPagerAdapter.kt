package com.mindenit.schedule.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import java.time.LocalDate
import java.time.LocalTime

class DayPagerAdapter(
    private val baseDate: LocalDate
) : RecyclerView.Adapter<DayPagerAdapter.DayPageVH>() {

    companion object {
        const val TOTAL_DAYS = 4000
        val START_INDEX = TOTAL_DAYS / 2
    }

    override fun getItemCount(): Int = TOTAL_DAYS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayPageVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_day_page, parent, false)
        return DayPageVH(v)
    }

    override fun onBindViewHolder(holder: DayPageVH, position: Int) {
        val diff = position - START_INDEX
        val date = baseDate.plusDays(diff.toLong())
        holder.bind(date)
    }

    class DayPageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val scroll: NestedScrollView = itemView.findViewById(R.id.day_scroll)
        private val dayView: DayScheduleView = itemView.findViewById(R.id.day_view)
        private var currentDate: LocalDate? = null

        fun bind(date: LocalDate) {
            // Force update even if it's the same date to ensure visibility after tab switching
            dayView.setDay(date, emptyList())
            currentDate = date

            scroll.post {
                val nowY = dayView.getScrollYForTime(LocalTime.now())
                scroll.scrollTo(0, nowY)
            }
        }
    }
}
