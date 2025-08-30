package com.mindenit.schedule.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.mindenit.schedule.R
import java.time.LocalDate

data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val eventCount: Int
)

class MonthAdapter(
    private var days: List<CalendarDay>,
    private val onDayClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<MonthAdapter.DayVH>() {

    private var itemHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT

    class DayVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView.findViewById(R.id.day_container)
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
        val view = holder.itemView

        holder.dayNumber.text = item.date.dayOfMonth.toString()
        holder.itemView.alpha = if (item.inCurrentMonth) 1.0f else 0.38f

        // Ensure height stays in sync if updated after creation
        val lpItem = holder.itemView.layoutParams
        if (lpItem != null && lpItem.height != itemHeightPx) {
            lpItem.height = itemHeightPx
            holder.itemView.layoutParams = lpItem
        }

        // Accessibility description
        holder.itemView.contentDescription = "День ${'$'}{item.date.dayOfMonth}, подій: ${'$'}{item.eventCount}"

        // Click on the container to show ripple and handle navigation
        holder.container.setOnClickListener {
            onDayClick(item.date)
        }

        // Highlight today with a pill background behind the number
        val today = LocalDate.now()
        if (item.date == today) {
            val bgColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
            val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnPrimary)
            val size = dpToPx(view, 28f)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = size / 2f
                setColor(bgColor)
            }
            holder.dayNumber.background = bg
            holder.dayNumber.setTextColor(textColor)
            val lp = holder.dayNumber.layoutParams
            lp.width = size
            lp.height = size
            holder.dayNumber.layoutParams = lp
        } else {
            // Reset to default appearance for non-today cells
            holder.dayNumber.background = null
            holder.dayNumber.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface))
            val lp = holder.dayNumber.layoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.dayNumber.layoutParams = lp
        }

        if (item.eventCount > 0) {
            holder.eventsCount.visibility = View.VISIBLE
            holder.eventsCount.text = item.eventCount.toString()
            val bgColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary)
            val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnPrimary)
            holder.eventsCount.setTextColor(textColor)
            val circlePx = dpToPx(view, 22f)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = circlePx / 2f
                setColor(bgColor)
            }
            holder.eventsCount.background = bg
            val lp = holder.eventsCount.layoutParams
            lp.width = circlePx
            lp.height = circlePx
            holder.eventsCount.layoutParams = lp
        } else {
            holder.eventsCount.visibility = View.GONE
            holder.eventsCount.background = null
        }
    }

    private fun dpToPx(view: View, dp: Float): Int = (dp * view.resources.displayMetrics.density).toInt()

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
