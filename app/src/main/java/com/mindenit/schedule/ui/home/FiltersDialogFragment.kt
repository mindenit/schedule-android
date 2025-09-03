package com.mindenit.schedule.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.mindenit.schedule.R
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.FiltersStorage
import com.mindenit.schedule.data.ScheduleType
import com.mindenit.schedule.data.SchedulesStorage
import com.mindenit.schedule.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FiltersDialogFragment : BottomSheetDialogFragment() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // UI elements
    private lateinit var chipGroupLessonTypes: ChipGroup
    private lateinit var recyclerTeachers: RecyclerView
    private lateinit var recyclerAuditoriums: RecyclerView
    private lateinit var recyclerSubjects: RecyclerView

    // Count chips
    private lateinit var chipTypesCount: Chip
    private lateinit var chipTeachersCount: Chip
    private lateinit var chipAuditoriumsCount: Chip
    private lateinit var chipSubjectsCount: Chip

    private var teacherAdapter: CheckAdapter? = null
    private var auditoriumAdapter: CheckAdapter? = null
    private var subjectAdapter: CheckAdapter? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val behavior = dialog.behavior
            behavior.isFitToContents = false
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = 600
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottomsheet_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI elements
        chipGroupLessonTypes = view.findViewById(R.id.chip_group_lesson_types)
        recyclerTeachers = view.findViewById(R.id.recycler_teachers)
        recyclerAuditoriums = view.findViewById(R.id.recycler_auditoriums)
        recyclerSubjects = view.findViewById(R.id.recycler_subjects)

        // Count chips
        chipTypesCount = view.findViewById(R.id.chip_types_count)
        chipTeachersCount = view.findViewById(R.id.chip_teachers_count)
        chipAuditoriumsCount = view.findViewById(R.id.chip_auditoriums_count)
        chipSubjectsCount = view.findViewById(R.id.chip_subjects_count)

        // Setup RecyclerViews
        recyclerTeachers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        recyclerAuditoriums.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        recyclerSubjects.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }

        // Setup button listeners
        view.findViewById<View>(R.id.btn_apply_filters).setOnClickListener { saveSelectionsAndClose() }
        view.findViewById<View>(R.id.btn_reset_filters).setOnClickListener { applyAndClose(FiltersStorage.Filters()) }

        loadData()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setContentEnabled(enabled: Boolean) {
        chipGroupLessonTypes.isEnabled = enabled
        recyclerTeachers.isEnabled = enabled
        recyclerAuditoriums.isEnabled = enabled
        recyclerSubjects.isEnabled = enabled
        view?.findViewById<View>(R.id.btn_apply_filters)?.isEnabled = enabled
        view?.findViewById<View>(R.id.btn_reset_filters)?.isEnabled = enabled
    }

    private fun updateCountChips() {
        // Update lesson types count
        val selectedTypesCount = (0 until chipGroupLessonTypes.childCount).count {
            val chip = chipGroupLessonTypes.getChildAt(it) as? Chip
            chip?.isChecked == true
        }
        updateCountChip(chipTypesCount, selectedTypesCount)

        // Update other counts
        updateCountChip(chipTeachersCount, teacherAdapter?.selectedIds()?.size ?: 0)
        updateCountChip(chipAuditoriumsCount, auditoriumAdapter?.selectedIds()?.size ?: 0)
        updateCountChip(chipSubjectsCount, subjectAdapter?.selectedIds()?.size ?: 0)
    }

    private fun updateCountChip(chip: Chip, count: Int) {
        if (count > 0) {
            chip.text = count.toString()
            chip.isVisible = true
        } else {
            chip.isVisible = false
        }
    }

    private fun clearAllFilters() {
        // Clear lesson types
        for (i in 0 until chipGroupLessonTypes.childCount) {
            (chipGroupLessonTypes.getChildAt(i) as? Chip)?.isChecked = false
        }

        // Clear adapters
        teacherAdapter?.clearSelection()
        auditoriumAdapter?.clearSelection()
        subjectAdapter?.clearSelection()

        updateCountChips()
    }

    private fun loadData() {
        val active = SchedulesStorage(requireContext()).getActive()
        if (active == null || active.first != ScheduleType.GROUP) {
            // Only groups supported right now
            setContentEnabled(false)
            return
        }
        setContentEnabled(false)

        val existing = FiltersStorage(requireContext()).get(active.first, active.second)
        val types = listOf("Лк", "Пз", "Лб", "Конс", "Зал", "Екз", "КП/КР")
        chipGroupLessonTypes.removeAllViews()
        types.forEach { t ->
            val chip = Chip(requireContext()).apply {
                text = t
                isCheckable = true
                isChecked = existing.lessonTypes.contains(t)
                setOnCheckedChangeListener { _, _ -> updateCountChips() }
            }
            chipGroupLessonTypes.addView(chip)
        }

        scope.launch {
            try {
                val id = active.second
                // API
                val teachersApi = withContext(Dispatchers.IO) { ApiClient.api.getGroupTeachers(id).data }
                val auditoriumsApi = withContext(Dispatchers.IO) { ApiClient.api.getGroupAuditoriums(id).data }
                val subjectsApi = withContext(Dispatchers.IO) { ApiClient.api.getGroupSubjects(id).data }
                // Cache union
                val teachersCached = withContext(Dispatchers.IO) { EventRepository.getCachedTeachersForActive(requireContext()) }
                val auditoriumsCached = withContext(Dispatchers.IO) { EventRepository.getCachedAuditoriumsForActive(requireContext()) }
                val subjectsCached = withContext(Dispatchers.IO) { EventRepository.getCachedSubjectsForActive(requireContext()) }

                val teachersMap = LinkedHashMap<Long, String>()
                teachersApi.forEach { teachersMap[it.id] = it.shortName }
                teachersCached.forEach { (tid, names) -> teachersMap.putIfAbsent(tid, names.second.ifBlank { names.first }) }
                val teachersList = teachersMap.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }

                val auditoriumsMap = LinkedHashMap<Long, String>()
                auditoriumsApi.forEach { auditoriumsMap[it.id.toLong()] = it.name }
                auditoriumsCached.forEach { (aid, name) -> auditoriumsMap.putIfAbsent(aid, name) }
                val auditoriumsList = auditoriumsMap.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }

                val subjectsMap = LinkedHashMap<Long, String>()
                subjectsApi.forEach { subjectsMap[it.id] = it.brief.ifBlank { it.name } }
                subjectsCached.forEach { (sid, titles) -> subjectsMap.putIfAbsent(sid, titles.second.ifBlank { titles.first }) }
                val subjectsList = subjectsMap.entries.sortedBy { it.value.lowercase() }.map { it.key to it.value }

                teacherAdapter = CheckAdapter(teachersList, existing.teachers) { updateCountChips() }
                auditoriumAdapter = CheckAdapter(auditoriumsList, existing.auditoriums) { updateCountChips() }
                subjectAdapter = CheckAdapter(subjectsList, existing.subjects) { updateCountChips() }

                recyclerTeachers.adapter = teacherAdapter
                recyclerAuditoriums.adapter = auditoriumAdapter
                recyclerSubjects.adapter = subjectAdapter

                // Initial count update
                updateCountChips()
            } finally {
                setContentEnabled(true)
            }
        }
    }

    private fun saveSelectionsAndClose() {
        val selectedTypes = mutableSetOf<String>()
        for (i in 0 until chipGroupLessonTypes.childCount) {
            val c = chipGroupLessonTypes.getChildAt(i)
            if (c is Chip && c.isChecked) selectedTypes.add(c.text.toString())
        }
        val filters = FiltersStorage.Filters(
            lessonTypes = selectedTypes,
            teachers = teacherAdapter?.selectedIds()?.toSet() ?: emptySet(),
            auditoriums = auditoriumAdapter?.selectedIds()?.toSet() ?: emptySet(),
            subjects = subjectAdapter?.selectedIds()?.toSet() ?: emptySet()
        )
        applyAndClose(filters)
    }

    private fun applyAndClose(filters: FiltersStorage.Filters) {
        val active = SchedulesStorage(requireContext()).getActive() ?: return
        FiltersStorage(requireContext()).set(active.first, active.second, filters)
        setFragmentResult("filters_changed", bundleOf())
        dismiss()
    }

    class CheckAdapter(
        private val items: List<Pair<Long, String>>, // id to title
        preselected: Set<Long>,
        private val onSelectionChanged: (() -> Unit)? = null
    ) : RecyclerView.Adapter<CheckAdapter.VH>() {
        private val selected = preselected.toMutableSet()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_filter_check, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (id, title) = items[position]
            holder.title.text = title
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = selected.contains(id)

            holder.itemView.setOnClickListener {
                val now = !holder.check.isChecked
                holder.check.isChecked = now
                if (now) selected.add(id) else selected.remove(id)
                onSelectionChanged?.invoke()
            }

            holder.check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(id) else selected.remove(id)
                onSelectionChanged?.invoke()
            }
        }

        fun selectedIds(): List<Long> = selected.toList()

        fun clearSelection() {
            selected.clear()
            notifyDataSetChanged()
            onSelectionChanged?.invoke()
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val check: MaterialCheckBox = v.findViewById(R.id.checkbox)
            val title: TextView = v.findViewById(R.id.title)
        }
    }
}
