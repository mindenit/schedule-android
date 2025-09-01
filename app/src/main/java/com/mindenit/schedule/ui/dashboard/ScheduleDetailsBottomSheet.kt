package com.mindenit.schedule.ui.dashboard

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.mindenit.schedule.R
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.ScheduleEntry
import com.mindenit.schedule.data.ScheduleType
import com.mindenit.schedule.data.SchedulesStorage
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class ScheduleDetailsBottomSheet : BottomSheetDialogFragment() {

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
        return inflater.inflate(R.layout.bottomsheet_schedule_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val id = requireArguments().getLong(ARG_ID)
        val name = requireArguments().getString(ARG_NAME).orEmpty()
        val type = ScheduleType.from(requireArguments().getString(ARG_TYPE).orEmpty())
        val createdAt = requireArguments().getLong(ARG_CREATED_AT, -1L).takeIf { it > 0 }

        view.findViewById<TextView>(R.id.value_id).text = id.toString()
        view.findViewById<TextView>(R.id.value_name).text = name
        view.findViewById<TextView>(R.id.value_type).text = when (type) {
            ScheduleType.GROUP -> view.context.getString(R.string.tab_groups)
            ScheduleType.TEACHER -> view.context.getString(R.string.tab_teachers)
            ScheduleType.AUDITORIUM -> view.context.getString(R.string.tab_auditoriums)
        }
        view.findViewById<TextView>(R.id.value_added).text = createdAt?.let {
            val dt = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(dt)
        } ?: "—"

        view.findViewById<MaterialButton>(R.id.btn_set_active).setOnClickListener {
            SchedulesStorage(requireContext()).setActive(type, id)
            // Prefetch current month cache in background to accelerate first render
            viewLifecycleOwner.lifecycleScope.launch {
                try { EventRepository.ensureMonthCached(requireContext(), YearMonth.now()) } catch (_: Throwable) {}
            }
            parentFragmentManager.setFragmentResult(REQUEST_REFRESH, Bundle())
            dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btn_delete).setOnClickListener {
            val entry = ScheduleEntry(type = type, id = id, name = name)
            SchedulesStorage(requireContext()).remove(entry)
            parentFragmentManager.setFragmentResult(REQUEST_REFRESH, Bundle())
            dismiss()
        }
    }

    companion object {
        const val REQUEST_REFRESH = "schedule_details_refresh"
        private const val ARG_ID = "arg_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_CREATED_AT = "arg_created_at"

        fun newInstance(entry: ScheduleEntry): ScheduleDetailsBottomSheet = ScheduleDetailsBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, entry.id)
                putString(ARG_NAME, entry.name)
                putString(ARG_TYPE, entry.type.toString())
                entry.createdAt?.let { putLong(ARG_CREATED_AT, it) }
            }
        }
    }
}
