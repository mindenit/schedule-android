package com.mindenit.schedule.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.mindenit.schedule.data.ApiClient
import com.mindenit.schedule.data.Auditorium
import com.mindenit.schedule.data.Group
import com.mindenit.schedule.data.Teacher
import com.mindenit.schedule.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.textfield.TextInputEditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ListPageFragment : Fragment() {

    private lateinit var type: String
    private lateinit var adapter: SimpleAdapter

    private var progress: ProgressBar? = null
    private var listView: RecyclerView? = null

    private val gson = Gson()
    private var allItems: List<Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = requireArguments().getString(ARG_TYPE) ?: "group"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_list_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress = view.findViewById(R.id.progress)
        listView = view.findViewById(R.id.list)

        adapter = SimpleAdapter { item ->
            // Post result on the Activity's FragmentManager so DashboardFragment (a sibling) can receive it
            requireParentFragment().parentFragmentManager.setFragmentResult(
                AddScheduleBottomSheet.REQUEST_KEY,
                bundleOf(
                    AddScheduleBottomSheet.RESULT_TYPE to type,
                    AddScheduleBottomSheet.RESULT_ID to item.id,
                    AddScheduleBottomSheet.RESULT_NAME to item.title,
                    AddScheduleBottomSheet.RESULT_PAYLOAD to item.payload
                )
            )
            (parentFragment as? BottomSheetDialogFragment)?.dismiss()
        }
        listView?.layoutManager = LinearLayoutManager(requireContext())
        listView?.adapter = adapter

        // Single inline search field (Material 3)
        view.findViewById<TextInputEditText>(R.id.searchEdit)?.addTextChangedListener { text ->
            onQueryChanged(text?.toString())
        }

        load()
    }

    private fun onQueryChanged(q: String?) {
        val query = q?.trim().orEmpty()
        // Поиск работает с первого символа, не чувствителен к регистру
        val list = if (query.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.matches(query.lowercase()) }
        }
        adapter.submitList(list)
        showEmptyStateIfNeeded(list, query)
    }

    private fun showEmptyStateIfNeeded(list: List<Item>, query: String) {
        val emptyState = view?.findViewById<View>(R.id.empty_state)
        val shouldShowEmpty = list.isEmpty() && query.isNotEmpty() && allItems.isNotEmpty()
        emptyState?.visibility = if (shouldShowEmpty) View.VISIBLE else View.GONE
        listView?.visibility = if (shouldShowEmpty) View.GONE else View.VISIBLE
    }

    private fun load() {
        progress?.isGone = false
        listView?.isGone = true
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val items = when (type) {
                    "teacher" -> ApiClient.service.getTeachers().data.map { it.toItem(gson) }
                    "auditorium" -> ApiClient.service.getAuditoriums().data.map { it.toItem(gson) }
                    else -> ApiClient.service.getGroups().data.map { it.toItem(gson) }
                }
                launch(Dispatchers.Main) {
                    allItems = items
                    adapter.submitList(items)
                    progress?.isGone = true
                    listView?.isGone = false
                }
            } catch (_: Throwable) {
                launch(Dispatchers.Main) {
                    progress?.isGone = true
                    Toast.makeText(requireContext(), R.string.error_loading, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val ARG_TYPE = "type"
        fun newInstance(type: String) = ListPageFragment().apply {
            arguments = bundleOf(ARG_TYPE to type)
        }
    }
}

private data class Item(val id: Long, val title: String, val payload: String) {
    fun matches(query: String): Boolean = title.lowercase().contains(query)
}

private fun Group.toItem(gson: Gson) = Item(id = id, title = name, payload = gson.toJson(this))
private fun Teacher.toItem(gson: Gson) = Item(id = id, title = shortName.ifBlank { fullName }, payload = gson.toJson(this))
private fun Auditorium.toItem(gson: Gson) = Item(id = id, title = name, payload = gson.toJson(this))

private class SimpleAdapter(private val onClick: (Item) -> Unit) :
    ListAdapter<Item, SimpleVH>(object : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_title, parent, false)
        return SimpleVH(v, onClick)
    }
    override fun onBindViewHolder(holder: SimpleVH, position: Int) {
        holder.bind(getItem(position))
    }
}

private class SimpleVH(v: View, private val onClick: (Item) -> Unit) : RecyclerView.ViewHolder(v) {
    private val tv = v.findViewById<android.widget.TextView>(R.id.titleText)
    private var current: Item? = null
    init { v.setOnClickListener { current?.let(onClick) } }
    fun bind(item: Item) { current = item; tv.text = item.title }
}
