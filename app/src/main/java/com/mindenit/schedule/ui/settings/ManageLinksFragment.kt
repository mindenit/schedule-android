package com.mindenit.schedule.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mindenit.schedule.R
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.SubjectLinksStorage
import com.mindenit.schedule.data.SubjectLinksStorage.SubjectLink
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import com.google.gson.Gson

class ManageLinksFragment : Fragment() {
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var importBtn: View
    private lateinit var exportBtn: View

    private val gson = Gson()
    private var items: MutableList<SubjectLink> = mutableListOf()
    private var subjectNames: Map<Long, String> = emptyMap()

    private val pickImportFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importFromUri(uri)
    }
    private val pickExportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) exportToUri(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_manage_links, container, false)
        list = root.findViewById<RecyclerView>(R.id.links_list)
        empty = root.findViewById<View>(R.id.links_empty)
        importBtn = root.findViewById<View>(R.id.btn_import)
        exportBtn = root.findViewById<View>(R.id.btn_export)

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = LinksAdapter()

        importBtn.setOnClickListener { pickImportFile.launch(arrayOf("application/json")) }
        exportBtn.setOnClickListener { showExportSelectionDialog() }

        loadData()
        return root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = getString(R.string.links_manage_screen_title)
    }

    private fun loadData() {
        val ctx = requireContext()
        val storage = SubjectLinksStorage(ctx)
        val map = storage.getAll()
        subjectNames = EventRepository.getSubjectNamesFromCache(ctx)
        items = map.values.flatten().sortedWith(compareBy({ it.subjectId }, { it.title.ifBlank { it.url } })).toMutableList()
        (list.adapter as LinksAdapter).notifyDataSetChanged()
        val isEmpty = items.isEmpty()
        empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        list.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private inner class LinksAdapter : RecyclerView.Adapter<LinkVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkVH {
            val v = layoutInflater.inflate(R.layout.item_manage_link, parent, false)
            return LinkVH(v)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: LinkVH, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class LinkVH(v: View) : RecyclerView.ViewHolder(v) {
        private val title: TextView = v.findViewById(R.id.link_title)
        private val url: TextView = v.findViewById(R.id.link_url)
        private val subject: TextView = v.findViewById(R.id.link_subject)
        private val menu: ImageButton = v.findViewById(R.id.menu)
        fun bind(link: SubjectLink) {
            val isMeet = link.url.contains("meet.google", true)
            val isZoom = link.url.contains("zoom", true)
            val autoTitle = when {
                link.title.isNotBlank() -> link.title
                isMeet -> getString(R.string.brand_google_meet)
                isZoom -> getString(R.string.brand_zoom)
                else -> link.url
            }
            title.text = autoTitle
            url.text = link.url
            val subjName = subjectNames[link.subjectId] ?: "ID: ${link.subjectId}"
            subject.text = getString(R.string.subject_label, subjName)

            menu.setOnClickListener { v ->
                val pm = PopupMenu(v.context, v)
                pm.menu.add(0, 1, 0, R.string.edit_link)
                pm.menu.add(0, 2, 1, R.string.delete_link)
                pm.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> { showEditDialog(link); true }
                        2 -> { deleteLink(link); true }
                        else -> false
                    }
                }
                pm.show()
            }
        }
    }

    private fun showEditDialog(existing: SubjectLink) {
        val content = layoutInflater.inflate(R.layout.popup_link_edit, null)
        val etTitle = content.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_title)
        val tilUrl = content.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_url)
        val etUrl = content.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_url)
        val btnCancel = content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnSave = content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)
        etTitle.setText(existing.title)
        etUrl.setText(existing.url)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(content).create()
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val newTitle = etTitle.text?.toString()?.trim().orEmpty()
            var newUrl = etUrl.text?.toString()?.trim().orEmpty()
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) newUrl = "https://$newUrl"
            if (newUrl.isBlank()) {
                tilUrl.error = getString(R.string.invalid_url)
                return@setOnClickListener
            } else tilUrl.error = null
            val storage = SubjectLinksStorage(requireContext())
            storage.upsert(existing.copy(title = newTitle, url = newUrl))
            dialog.dismiss()
            loadData()
        }
        dialog.show()
    }

    private fun deleteLink(link: SubjectLink) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.delete_link_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                SubjectLinksStorage(requireContext()).delete(link.subjectId, link.id)
                loadData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showExportSelectionDialog() {
        val ctx = requireContext()
        val storage = SubjectLinksStorage(ctx)
        val all = storage.getAll()
        if (all.isEmpty()) {
            Toast.makeText(ctx, R.string.no_links, Toast.LENGTH_SHORT).show()
            return
        }
        val root = ScrollView(ctx)
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 0) }
        root.addView(container)
        // State map
        data class Node(var checked: Boolean)
        val selectedSubjects = mutableMapOf<Long, Node>()
        val selectedLinks = mutableMapOf<String, Node>()
        val subjNames = EventRepository.getSubjectNamesFromCache(ctx)
        // Build tree (subjects with checkbox + child links with checkboxes)
        all.forEach { (sid, links) ->
            val subjectCb = CheckBox(ctx)
            subjectCb.text = subjNames[sid] ?: "ID: $sid"
            container.addView(subjectCb)
            selectedSubjects[sid] = Node(false)
            val child = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 0, 0, 8) }
            container.addView(child)
            links.forEach { l ->
                val cb = CheckBox(ctx)
                cb.text = (l.title.ifBlank { l.url })
                child.addView(cb)
                selectedLinks[l.id] = Node(false)
                cb.setOnCheckedChangeListener { _, isChecked ->
                    selectedLinks[l.id]?.checked = isChecked
                }
            }
            subjectCb.setOnCheckedChangeListener { _, isChecked ->
                selectedSubjects[sid]?.checked = isChecked
                // toggle all children
                for (i in 0 until child.childCount) {
                    val c = child.getChildAt(i) as? CheckBox
                    c?.isChecked = isChecked
                }
            }
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.select_export_items)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.export) { d, _ ->
                // Compute selection
                val selected = mutableMapOf<Long, List<SubjectLink>>()
                all.forEach { (sid, links) ->
                    val subChecked = selectedSubjects[sid]?.checked == true
                    val l = if (subChecked) links else links.filter { selectedLinks[it.id]?.checked == true }
                    if (l.isNotEmpty()) selected[sid] = l
                }
                if (selected.isEmpty()) {
                    Toast.makeText(ctx, R.string.no_links, Toast.LENGTH_SHORT).show()
                } else {
                    // trigger create document
                    pickExportFile.launch("links_export.json")
                    // temporarily stash selection
                    pendingExport = selected
                }
                d.dismiss()
            }
            .show()
    }

    private var pendingExport: Map<Long, List<SubjectLink>>? = null

    private fun exportToUri(uri: Uri) {
        val ctx = requireContext()
        val sel = pendingExport ?: return
        data class SubjectBlock(val subjectId: Long, val links: List<SubjectLink>, val subjectTitle: String?)
        val blocks = sel.map { (sid, links) -> SubjectBlock(sid, links, EventRepository.getSubjectNamesFromCache(ctx)[sid]) }
        val json = gson.toJson(mapOf("subjects" to blocks))
        try {
            requireActivity().contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { it.write(json) }
            }
            Toast.makeText(ctx, R.string.export_complete, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(ctx, R.string.export_error, Toast.LENGTH_SHORT).show()
        } finally {
            pendingExport = null
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
            val subjects = root["subjects"] as? List<*> ?: emptyList<Any>()
            val storage = SubjectLinksStorage(ctx)
            subjects.forEach { sb ->
                val obj = sb as? Map<*, *> ?: return@forEach
                val sid = (obj["subjectId"] as? Number)?.toLong() ?: return@forEach
                val links = obj["links"] as? List<*> ?: emptyList<Any>()
                links.forEach { l ->
                    val m = l as? Map<*, *> ?: return@forEach
                    val id = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString()
                    val title = (m["title"] as? String).orEmpty()
                    val url = (m["url"] as? String).orEmpty()
                    val primary = (m["isPrimaryVideo"] as? Boolean) ?: false
                    if (url.isNotBlank()) {
                        storage.upsert(SubjectLink(id = id, subjectId = sid, title = title, url = url, isPrimaryVideo = primary))
                    }
                }
            }
            Toast.makeText(ctx, R.string.import_complete, Toast.LENGTH_SHORT).show()
            loadData()
        } catch (_: Throwable) {
            Toast.makeText(ctx, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }
}
