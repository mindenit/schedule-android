package com.mindenit.schedule.ui.dashboard

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.mindenit.schedule.R
import com.mindenit.schedule.data.ScheduleEntry
import com.mindenit.schedule.data.ScheduleType

class SchedulesAdapter(private val onItemClick: (ScheduleEntry) -> Unit) : ListAdapter<ScheduleEntry, SchedulesAdapter.VH>(DIFF) {

    private var active: Pair<ScheduleType, Long>? = null

    fun setActiveSelection(active: Pair<ScheduleType, Long>?) {
        this.active = active
        notifyDataSetChanged()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScheduleEntry>() {
            override fun areItemsTheSame(oldItem: ScheduleEntry, newItem: ScheduleEntry): Boolean =
                oldItem.type == newItem.type && oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ScheduleEntry, newItem: ScheduleEntry): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_title, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = item.name

        // Type visuals
        val (chipText, iconRes) = when (item.type) {
            ScheduleType.GROUP -> holder.itemView.context.getString(R.string.schedule_type_group) to R.drawable.ic_people_24
            ScheduleType.TEACHER -> holder.itemView.context.getString(R.string.schedule_type_teacher) to R.drawable.ic_person_24
            ScheduleType.AUDITORIUM -> holder.itemView.context.getString(R.string.schedule_type_auditorium) to R.drawable.ic_home_24
        }
        holder.typeChip.text = chipText
        holder.typeChip.visibility = View.VISIBLE
        holder.avatarContainer.visibility = View.VISIBLE
        holder.typeIconImage.setImageResource(iconRes)

        // Active state visuals
        val isActive = active?.first == item.type && active?.second == item.id
        val card = holder.card
        val colorPrimary = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
        val outline = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        val bgLow = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow)
        val bgHigh = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerHigh)

        if (isActive) {
            holder.activeChip.visibility = View.VISIBLE
            holder.activeChip.text = holder.itemView.context.getString(R.string.active)
            card.strokeColor = colorPrimary
            card.setCardBackgroundColor(bgHigh)
        } else {
            holder.activeChip.visibility = View.GONE
            card.strokeColor = outline
            card.setCardBackgroundColor(bgLow)
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: MaterialCardView = v as MaterialCardView
        val title: TextView = v.findViewById(R.id.titleText)
        val avatarContainer: View = v.findViewById(R.id.avatarContainer)
        val typeIconImage: ImageView = v.findViewById(R.id.typeIconImage)
        val typeChip: Chip = v.findViewById(R.id.typeChip)
        val activeChip: Chip = v.findViewById(R.id.activeChip)
    }
}
