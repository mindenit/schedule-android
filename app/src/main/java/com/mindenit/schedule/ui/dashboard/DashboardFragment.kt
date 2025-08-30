package com.mindenit.schedule.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mindenit.schedule.R
import com.mindenit.schedule.data.ScheduleEntry
import com.mindenit.schedule.data.SchedulesStorage
import com.mindenit.schedule.databinding.FragmentDashboardBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var storage: SchedulesStorage
    private lateinit var adapter: SchedulesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        storage = SchedulesStorage(requireContext())

        adapter = SchedulesAdapter { entry -> showDetails(entry) }
        binding.schedulesList.layoutManager = LinearLayoutManager(requireContext())
        binding.schedulesList.adapter = adapter

        binding.fabAddSchedule.setOnClickListener {
            AddScheduleBottomSheet().show(parentFragmentManager, "add_schedule")
        }

        setFragmentResultListener(AddScheduleBottomSheet.REQUEST_KEY) { _, bundle ->
            val type = bundle.getString(AddScheduleBottomSheet.RESULT_TYPE) ?: return@setFragmentResultListener
            val id = bundle.getLong(AddScheduleBottomSheet.RESULT_ID)
            val name = bundle.getString(AddScheduleBottomSheet.RESULT_NAME) ?: return@setFragmentResultListener
            val payload = bundle.getString(AddScheduleBottomSheet.RESULT_PAYLOAD)
            val entry = ScheduleEntry.from(type, id, name, payload)
            storage.add(entry)
            refresh()
        }
        // Refresh list after deletion from details sheet
        setFragmentResultListener(ScheduleDetailsBottomSheet.REQUEST_REFRESH) { _, _ ->
            refresh()
        }

        refresh()
    }

    private fun showDetails(entry: ScheduleEntry) {
        ScheduleDetailsBottomSheet.newInstance(entry)
            .show(parentFragmentManager, "schedule_details")
    }

    private fun refresh() {
        val items = storage.getAll()
        adapter.submitList(items)
        // Provide active selection to adapter so it can highlight it
        adapter.setActiveSelection(storage.getActive())
        binding.emptyText.isVisible = items.isEmpty()
        binding.schedulesList.isGone = items.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}