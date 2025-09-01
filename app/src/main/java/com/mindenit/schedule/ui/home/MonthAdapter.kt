package com.mindenit.schedule.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
            primaryColor = ContextCompat.getColor(v.context, R.color.purple_500)
            onPrimaryColor = ContextCompat.getColor(v.context, android.R.color.white)
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
            holder.dayNumber.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.calendar_on_background))

            // Restore original layout params instead of creating new ones
            holder.dayNumber.layoutParams = holder.originalLayoutParams
        }

        // Render a compact badge with the number of events (if any)
        if (item.eventCount > 0) {
            if (holder.eventsContainer.childCount > 0) holder.eventsContainer.removeAllViews()
            val ctx = holder.itemView.context
            val badge = TextView(ctx).apply {
                text = item.eventCount.toString()
                setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                textSize = 12f
                setPadding(dpToPx(this, 6f), dpToPx(this, 2f), dpToPx(this, 6f), dpToPx(this, 2f))
                background = ResourcesCompat.getDrawable(holder.itemView.resources, R.drawable.bg_event_badge_sample, ctx.theme)
                maxLines = 1
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(holder.itemView, 2f)
                }
            }
            holder.eventsContainer.addView(badge)
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
