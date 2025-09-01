package com.mindenit.schedule.ui.home

import android.graphics.Typeface
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

// Represents one subject badge in a day cell
data class SubjectBadge(val label: String, val color: Int)

data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val badges: List<SubjectBadge>
) {
    // Add equals/hashCode for DiffUtil
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalendarDay) return false
        return date == other.date &&
               inCurrentMonth == other.inCurrentMonth &&
               badges == other.badges
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + inCurrentMonth.hashCode()
        result = 31 * result + badges.hashCode()
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

        // Accessibility description with total badges
        holder.itemView.contentDescription = "День ${item.date.dayOfMonth}, позначок: ${item.badges.size}"

        // Set click listener only once per ViewHolder
        if (!holder.container.hasOnClickListeners()) {
            holder.container.setOnClickListener {
                onDayClick(item.date)
            }
        }

        // Today highlighting
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

        // Render vertical badges per subject-type (label only, colored background)
        if (holder.eventsContainer.childCount > 0) holder.eventsContainer.removeAllViews()
        if (item.badges.isNotEmpty()) {
            val ctx = holder.itemView.context
            val corner = dpToPx(holder.itemView, 4f).toFloat()
            val strokeW = dpToPx(holder.itemView, 1f)
            val strokeColor = android.graphics.Color.parseColor("#33000000") // subtle 20% black
            for (badge in item.badges) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    // Programmatic rounded background per badge color
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = corner
                        setColor(badge.color)
                        setStroke(strokeW, strokeColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dpToPx(holder.itemView, 2f)
                    }
                    setPadding(dpToPx(this, 6f), dpToPx(this, 3f), dpToPx(this, 6f), dpToPx(this, 3f))
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val label = TextView(ctx).apply {
                    text = badge.label
                    setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(label)
                holder.eventsContainer.addView(row)
            }
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
