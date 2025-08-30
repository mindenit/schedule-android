package com.mindenit.schedule.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import java.time.LocalDate

data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val eventCount: Int
)

class MonthAdapter(
    private var days: List<CalendarDay>
) : RecyclerView.Adapter<MonthAdapter.DayVH>() {

    private var itemHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT

    class DayVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayNumber: TextView = itemView.findViewById(R.id.text_day_number)
        val eventsCount: TextView = itemView.findViewById(R.id.text_events_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        // Apply fixed height so rows evenly divide the RecyclerView height
        v.layoutParams = v.layoutParams?.apply { height = itemHeightPx }
            ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeightPx)
        return DayVH(v)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: DayVH, position: Int) {
        val item = days[position]
        holder.dayNumber.text = item.date.dayOfMonth.toString()
        holder.eventsCount.text = item.eventCount.toString()

        // Dim out days not in the current month
        holder.itemView.alpha = if (item.inCurrentMonth) 1.0f else 0.38f

        // Ensure height stays in sync if updated after creation
        val lp = holder.itemView.layoutParams
        if (lp != null && lp.height != itemHeightPx) {
            lp.height = itemHeightPx
            holder.itemView.layoutParams = lp
        }

        // Accessibility description
        holder.itemView.contentDescription = "День ${'$'}{item.date.dayOfMonth}, подій: ${'$'}{item.eventCount}"
    }

    fun setItemHeight(heightPx: Int) {
        if (heightPx > 0 && heightPx != itemHeightPx) {
            itemHeightPx = heightPx
            notifyDataSetChanged()
        }
    }

    fun submit(days: List<CalendarDay>) {
        this.days = days
        notifyDataSetChanged()
    }
}
