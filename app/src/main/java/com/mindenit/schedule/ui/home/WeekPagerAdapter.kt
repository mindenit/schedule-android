package com.mindenit.schedule.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import java.time.LocalDate
import java.time.LocalTime

class WeekPagerAdapter(
    private val baseStartOfWeek: LocalDate,
    private val eventsProvider: (LocalDate) -> List<WeekScheduleView.WeekEvent>,
    private val onEventClicked: (WeekScheduleView.WeekEvent) -> Unit
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
        holder.bind(start, eventsProvider(start), onEventClicked)
    }

    class WeekPageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val scroll: NestedScrollView = itemView.findViewById(R.id.week_scroll)
        private val weekView: WeekScheduleView = itemView.findViewById(R.id.week_view)

        fun bind(startOfWeek: LocalDate, events: List<WeekScheduleView.WeekEvent>, onEventClicked: (WeekScheduleView.WeekEvent) -> Unit) {
            weekView.setWeek(startOfWeek, events)
            weekView.onEventClick = onEventClicked
            scroll.post {
                val nowY = weekView.getScrollYForTime(LocalTime.now())
                scroll.scrollTo(0, nowY)
            }
        }
    }
}
