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
import android.app.Dialog
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior

class EventDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomsheetEventDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val behavior = dialog.behavior
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

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

        // Date and time without icons
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

        // Header card stroke tinted by type color
        val strokePx = (requireContext().resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1)
        binding.headerCard.strokeColor = typeColors.background
        binding.headerCard.strokeWidth = strokePx

        // Auditorium text without icon
        val audText = args.getString(ARG_AUD)
        binding.auditoriumText.setOrPlaceholder(audText)

        // Chips for teachers (hide card if empty)
        val teacherNames = args.getString(ARG_TEACHERS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.teachersChips.removeAllViews()
        if (teacherNames.isEmpty()) {
            binding.teachersCard.visibility = View.GONE
        } else {
            binding.teachersCard.visibility = View.VISIBLE
            teacherNames.forEach { binding.teachersChips.addView(makeChip(it)) }
        }

        // Chips for groups (hide card if empty)
        val groupNames = args.getString(ARG_GROUPS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.groupsChips.removeAllViews()
        if (groupNames.isEmpty()) {
            binding.groupsCard.visibility = View.GONE
        } else {
            binding.groupsCard.visibility = View.VISIBLE
            groupNames.forEach { binding.groupsChips.addView(makeChip(it)) }
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
            val groups = event.groups.joinToString { it.name }
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
                }
            }
        }
    }
}
