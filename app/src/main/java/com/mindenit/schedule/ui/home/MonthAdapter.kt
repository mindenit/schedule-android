package com.mindenit.schedule.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.mindenit.schedule.R
import java.time.LocalDate

data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val eventCount: Int
) {
    // Add equals/hashCode for DiffUtil
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarDay) return false
        return date == other.date &&
               inCurrentMonth == other.inCurrentMonth &&
               eventCount == other.eventCount
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + inCurrentMonth.hashCode()
        result = 31 * result + eventCount
        return result
    }
}

class MonthAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : ListAdapter<CalendarDay, MonthAdapter.DayVH>(DayDiffCallback()) {

    private var itemHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT

    // Cache expensive drawable and color calculations
    private var todayBackground: GradientDrawable? = null
    private var primaryColor: Int? = null
    private var onPrimaryColor: Int? = null
    private var todaySize: Int = 0

    class DayVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: View = itemView.findViewById(R.id.day_container)
        val dayNumber: TextView = itemView.findViewById(R.id.text_day_number)
        val eventsContainer: LinearLayout = itemView.findViewById(R.id.events_container)

        // Cache original layout params to avoid recreation
        val originalLayoutParams = dayNumber.layoutParams
    }

    // Use DiffUtil for efficient updates instead of notifyDataSetChanged
    private class DayDiffCallback : DiffUtil.ItemCallback<CalendarDay>() {
        override fun areItemsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem.date == newItem.date
        }

        override fun areContentsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)

        // Initialize cached values once
        if (primaryColor == null) {
            primaryColor = MaterialColors.getColor(v, com.google.android.material.R.attr.colorPrimary)
            onPrimaryColor = MaterialColors.getColor(v, com.google.android.material.R.attr.colorOnPrimary)
            todaySize = dpToPx(v, 28f)

            todayBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = todaySize / 2f
                setColor(primaryColor!!)
            }
        }

        // Apply fixed height so rows evenly divide the RecyclerView height
        v.layoutParams = v.layoutParams?.apply { height = itemHeightPx }
            ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeightPx)
        return DayVH(v)
    }

    override fun onBindViewHolder(holder: DayVH, position: Int) {
        val item = getItem(position)

        holder.dayNumber.text = item.date.dayOfMonth.toString()
        holder.itemView.alpha = if (item.inCurrentMonth) 1.0f else 0.38f

        // Optimize height updates - only change if needed
        val lpItem = holder.itemView.layoutParams
        if (lpItem != null && lpItem.height != itemHeightPx) {
            lpItem.height = itemHeightPx
            holder.itemView.layoutParams = lpItem
        }

        // Optimize accessibility - only set once per bind
        holder.itemView.contentDescription = "День ${item.date.dayOfMonth}, подій: ${item.eventCount}"

        // Set click listener only once per ViewHolder
        if (!holder.container.hasOnClickListeners()) {
            holder.container.setOnClickListener {
                onDayClick(item.date)
            }
        }

        // Optimize today highlighting with cached values
        val today = LocalDate.now()
        if (item.date == today) {
            holder.dayNumber.background = todayBackground
            holder.dayNumber.setTextColor(onPrimaryColor!!)

            // Use cached layout params
            val lp = holder.dayNumber.layoutParams
            lp.width = todaySize
            lp.height = todaySize
            holder.dayNumber.layoutParams = lp
        } else {
            // Reset to default appearance efficiently
            holder.dayNumber.background = null
            holder.dayNumber.setTextColor(MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface))

            // Restore original layout params instead of creating new ones
            holder.dayNumber.layoutParams = holder.originalLayoutParams
        }

        // Optimize events container - only clear if there are events to show
        if (item.eventCount > 0) {
            holder.eventsContainer.removeAllViews()
            // Future: add event badges efficiently
        } else if (holder.eventsContainer.childCount > 0) {
            holder.eventsContainer.removeAllViews()
        }
    }

    private fun dpToPx(view: View, dp: Float): Int = (dp * view.resources.displayMetrics.density).toInt()

    // Use submitList instead of manual data management
    fun updateDays(newDays: List<CalendarDay>) {
        submitList(newDays)
    }

    fun setItemHeight(heightPx: Int) {
        if (itemHeightPx != heightPx) {
            itemHeightPx = heightPx
            notifyItemRangeChanged(0, itemCount)
        }
    }

    // Remove deprecated methods that use notifyDataSetChanged
    @Deprecated("Use updateDays() instead", ReplaceWith("updateDays(newDays)"))
    fun submit(newDays: List<CalendarDay>) {
        updateDays(newDays)
    }

    @Deprecated("Use updateDays() instead", ReplaceWith("updateDays(newDays)"))
    fun updateData(newDays: List<CalendarDay>) {
        updateDays(newDays)
    }
}
