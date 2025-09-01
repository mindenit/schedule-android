package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.BottomsheetEventDetailsBinding
import java.time.format.DateTimeFormatter
import android.content.res.ColorStateList

class EventDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomsheetEventDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetEventDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val placeholder = getString(R.string.placeholder_empty)

        fun TextView.setOrPlaceholder(value: String?) {
            text = if (value.isNullOrBlank()) placeholder else value
        }

        binding.title.setOrPlaceholder(args.getString(ARG_TITLE))

        val subtitleText = args.getString(ARG_SUBTITLE)
        if (subtitleText.isNullOrBlank()) {
            binding.subtitle.visibility = View.GONE
        } else {
            binding.subtitle.visibility = View.VISIBLE
            binding.subtitle.text = subtitleText
        }

        // Date and time split with icons
        binding.dateText.setOrPlaceholder(args.getString(ARG_DATE))
        binding.timeText.setOrPlaceholder(args.getString(ARG_TIME))

        // Type chip with themed color
        val typeText = args.getString(ARG_TYPE)
        val typeColors = EventColorResolver.colorsForType(requireView(), typeText ?: "")
        binding.typeChip.text = if (typeText.isNullOrBlank()) placeholder else typeText
        binding.typeChip.setTextColor(typeColors.foreground)
        binding.typeChip.chipBackgroundColor = ColorStateList.valueOf(typeColors.background)
        binding.typeChip.isClickable = false
        binding.typeChip.isCheckable = false

        // Auditorium text row
        val audText = args.getString(ARG_AUD)
        binding.auditoriumText.setOrPlaceholder(audText)

        // Chips for teachers (hide block if empty)
        val teacherNames = args.getString(ARG_TEACHERS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.teachersChips.removeAllViews()
        if (teacherNames.isEmpty()) {
            binding.labelTeachers.visibility = View.GONE
            binding.teachersChips.visibility = View.GONE
        } else {
            binding.labelTeachers.visibility = View.VISIBLE
            binding.teachersChips.visibility = View.VISIBLE
            teacherNames.forEach { binding.teachersChips.addView(makeChip(it)) }
        }
        // Teacher IDs line (hidden if empty)
        val teacherIdsText = args.getString(ARG_TEACHER_IDS)
        if (teacherIdsText.isNullOrBlank()) {
            binding.teacherIds.visibility = View.GONE
        } else {
            binding.teacherIds.visibility = View.VISIBLE
            binding.teacherIds.text = getString(R.string.teacher_ids_format, teacherIdsText)
        }

        // Chips for groups (hide block if empty)
        val groupNames = args.getString(ARG_GROUPS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.groupsChips.removeAllViews()
        if (groupNames.isEmpty()) {
            binding.labelGroups.visibility = View.GONE
            binding.groupsChips.visibility = View.GONE
        } else {
            binding.labelGroups.visibility = View.VISIBLE
            binding.groupsChips.visibility = View.VISIBLE
            groupNames.forEach { binding.groupsChips.addView(makeChip(it)) }
        }
        // Group IDs line (hidden if empty)
        val groupIdsText = args.getString(ARG_GROUP_IDS)
        if (groupIdsText.isNullOrBlank()) {
            binding.groupIds.visibility = View.GONE
        } else {
            binding.groupIds.visibility = View.VISIBLE
            binding.groupIds.text = getString(R.string.group_ids_format, groupIdsText)
        }
    }

    private fun makeChip(text: String): Chip = Chip(requireContext()).apply {
        this.text = text
        isClickable = false
        isCheckable = false
        isCloseIconVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_TYPE = "type"
        private const val ARG_AUD = "aud"
        private const val ARG_TEACHERS = "teachers"
        private const val ARG_GROUPS = "groups"
        private const val ARG_DATE = "date"
        private const val ARG_TIME = "time"
        private const val ARG_TEACHER_IDS = "teacher_ids"
        private const val ARG_GROUP_IDS = "group_ids"

        fun from(event: com.mindenit.schedule.data.Event): EventDetailsBottomSheet {
            val dfDate = DateTimeFormatter.ofPattern("EEE, d MMM")
            val dfTime = DateTimeFormatter.ofPattern("HH:mm")
            val sameDay = event.start.toLocalDate() == event.end.toLocalDate()
            val date = if (sameDay) {
                event.start.format(dfDate)
            } else {
                "${event.start.format(dfDate)} — ${event.end.format(dfDate)}"
            }
            val time = if (sameDay) {
                "${event.start.format(dfTime)}–${event.end.format(dfTime)}"
            } else {
                "${event.start.format(dfTime)} — ${event.end.format(dfTime)}"
            }
            val teachers = event.teachers.joinToString { it.shortName }
            val teacherIds = event.teachers.joinToString { it.id.toString() }
            val groups = event.groups.joinToString { it.name }
            val groupIds = event.groups.joinToString { it.id.toString() }
            return EventDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, event.subject.title)
                    putString(ARG_SUBTITLE, event.subject.brief)
                    putString(ARG_TYPE, event.type)
                    putString(ARG_AUD, event.auditorium.name)
                    putString(ARG_TEACHERS, teachers)
                    putString(ARG_GROUPS, groups)
                    putString(ARG_DATE, date)
                    putString(ARG_TIME, time)
                    putString(ARG_TEACHER_IDS, teacherIds)
                    putString(ARG_GROUP_IDS, groupIds)
                }
            }
        }
    }
}
