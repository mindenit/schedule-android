package com.mindenit.schedule.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mindenit.schedule.R
import com.mindenit.schedule.data.ScheduleEntry
import com.mindenit.schedule.data.ScheduleType

class SchedulesAdapter : ListAdapter<ScheduleEntry, SchedulesAdapter.VH>(DIFF) {

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
        val prefix = when (item.type) {
            ScheduleType.GROUP -> "[Група] "
            ScheduleType.TEACHER -> "[Викладач] "
            ScheduleType.AUDITORIUM -> "[Аудиторія] "
        }
        holder.title.text = prefix + item.name
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.titleText)
    }
}

