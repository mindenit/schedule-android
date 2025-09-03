package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

class FiltersDialogFragment : DialogFragment() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var sectionTypes: LinearLayout
    private lateinit var sectionTeachers: LinearLayout
    private lateinit var sectionAuditoriums: LinearLayout
    private lateinit var sectionSubjects: LinearLayout

    private lateinit var lessonTypes: ChipGroup
    private lateinit var listTeachers: RecyclerView
    private lateinit var listAuditoriums: RecyclerView
    private lateinit var listSubjects: RecyclerView

    private var teacherAdapter: CheckAdapter? = null
    private var auditoriumAdapter: CheckAdapter? = null
    private var subjectAdapter: CheckAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottomsheet_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sectionTypes = view.findViewById(R.id.section_types)
        sectionTeachers = view.findViewById(R.id.section_teachers)
        sectionAuditoriums = view.findViewById(R.id.section_auditoriums)
        sectionSubjects = view.findViewById(R.id.section_subjects)

        // Build dynamic ChipGroup and RecyclerViews inside sections
        lessonTypes = ChipGroup(requireContext()).apply { isSingleSelection = false }
        sectionTypes.addView(lessonTypes)

        listTeachers = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        sectionTeachers.addView(listTeachers)

        listAuditoriums = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        sectionAuditoriums.addView(listAuditoriums)

        listSubjects = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        sectionSubjects.addView(listSubjects)

        view.findViewById<View>(R.id.btn_apply_filters).setOnClickListener { saveSelectionsAndClose() }
        view.findViewById<View>(R.id.btn_reset_filters).setOnClickListener { applyAndClose(FiltersStorage.Filters()) }

        loadData()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setContentEnabled(enabled: Boolean) {
        lessonTypes.isEnabled = enabled
        listTeachers.isEnabled = enabled
        listAuditoriums.isEnabled = enabled
        listSubjects.isEnabled = enabled
        view?.findViewById<View>(R.id.btn_apply_filters)?.isEnabled = enabled
        view?.findViewById<View>(R.id.btn_reset_filters)?.isEnabled = enabled
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
        lessonTypes.removeAllViews()
        types.forEach { t ->
            val chip = Chip(requireContext()).apply {
                text = t
                isCheckable = true
                isChecked = existing.lessonTypes.contains(t)
            }
            lessonTypes.addView(chip)
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

                teacherAdapter = CheckAdapter(teachersList, existing.teachers)
                auditoriumAdapter = CheckAdapter(auditoriumsList, existing.auditoriums)
                subjectAdapter = CheckAdapter(subjectsList, existing.subjects)

                listTeachers.adapter = teacherAdapter
                listAuditoriums.adapter = auditoriumAdapter
                listSubjects.adapter = subjectAdapter
            } finally {
                setContentEnabled(true)
            }
        }
    }

    private fun saveSelectionsAndClose() {
        val selectedTypes = mutableSetOf<String>()
        for (i in 0 until lessonTypes.childCount) {
            val c = lessonTypes.getChildAt(i)
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
        preselected: Set<Long>
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
            }
            holder.check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(id) else selected.remove(id)
            }
        }
        fun selectedIds(): List<Long> = selected.toList()
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val check: CheckBox = v.findViewById(R.id.checkbox)
            val title: TextView = v.findViewById(R.id.title)
        }
    }
}

