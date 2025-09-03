package com.mindenit.schedule.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.mindenit.schedule.R
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.HiddenSubjectsStorage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class ManageHiddenSubjectsFragment : Fragment() {
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var importBtn: View
    private lateinit var exportBtn: View

    private val gson = Gson()
    private var items: MutableList<Long> = mutableListOf()
    private var subjectNames: Map<Long, String> = emptyMap()

    private val pickImportFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importFromUri(uri)
    }
    private val pickExportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) exportToUri(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_manage_hidden_subjects, container, false)
        list = root.findViewById<RecyclerView>(R.id.hidden_list)
        empty = root.findViewById<TextView>(R.id.hidden_empty)
        importBtn = root.findViewById<View>(R.id.btn_import_hidden)
        exportBtn = root.findViewById<View>(R.id.btn_export_hidden)

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = HiddenAdapter()

        importBtn.setOnClickListener { pickImportFile.launch(arrayOf("application/json")) }
        exportBtn.setOnClickListener { pickExportFile.launch("hidden_subjects.json") }

        loadData()
        return root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.hidden_subjects_screen_title)
    }

    private fun loadData() {
        val ctx = requireContext()
        val storage = HiddenSubjectsStorage(ctx)
        items = storage.getAll().sorted().toMutableList()
        subjectNames = EventRepository.getSubjectNamesFromCache(ctx)
        (list.adapter as HiddenAdapter).notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class HiddenAdapter : RecyclerView.Adapter<VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_hidden_subject, parent, false)
            return VH(v)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.hidden_title)
        private val delete: ImageButton = v.findViewById(R.id.btn_delete_hidden)
        fun bind(subjectId: Long) {
            val name = subjectNames[subjectId] ?: "ID: $subjectId"
            title.text = name
            delete.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.delete_hidden_subject_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        HiddenSubjectsStorage(requireContext()).remove(subjectId)
                        // Notify listeners (e.g., HomeFragment) to refresh immediately
                        parentFragmentManager.setFragmentResult("hidden_subjects_changed", bundleOf())
                        loadData()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun exportToUri(uri: Uri) {
        val ctx = requireContext()
        val ids = HiddenSubjectsStorage(ctx).getAll().toList()
        val json = gson.toJson(mapOf("subjectIds" to ids))
        try {
            requireActivity().contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { it.write(json) }
            }
            Toast.makeText(ctx, R.string.export_complete, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(ctx, R.string.export_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUri(uri: Uri) {
        val ctx = requireContext()
        try {
            val text = requireActivity().contentResolver.openInputStream(uri)?.use { ins ->
                BufferedReader(InputStreamReader(ins)).readText()
            } ?: ""
            if (text.isBlank()) throw IllegalArgumentException("empty file")
            val root = gson.fromJson(text, Map::class.java)
            val array = (root["subjectIds"] as? List<*>) ?: emptyList<Any>()
            val storage = HiddenSubjectsStorage(ctx)
            array.forEach { n ->
                val id = (n as? Number)?.toLong() ?: return@forEach
                storage.add(id)
            }
            Toast.makeText(ctx, R.string.import_complete, Toast.LENGTH_SHORT).show()
            // Notify listeners after bulk import
            parentFragmentManager.setFragmentResult("hidden_subjects_changed", bundleOf())
            loadData()
        } catch (_: Throwable) {
            Toast.makeText(ctx, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }
}
