package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.preference.PreferenceManager

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MonthAdapter
    private var rowsCount: Int = 6
    private var selectedYearMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()

    private enum class ViewMode { MONTH, WEEK, DAY }
    private var viewMode: ViewMode = ViewMode.MONTH

    private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("uk"))
    private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d LLLL yyyy", Locale.forLanguageTag("uk"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Recycler
        adapter = MonthAdapter(emptyList()) { clickedDate ->
            selectedDate = clickedDate
            viewMode = ViewMode.DAY
            renderCurrentMode()
        }
        binding.calendarGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarGrid.setHasFixedSize(true)
        binding.calendarGrid.adapter = adapter

        // Apply default view mode from settings before first render
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        when (sp.getString("pref_default_view", "month")) {
            "week" -> viewMode = ViewMode.WEEK
            "day" -> viewMode = ViewMode.DAY
            else -> viewMode = ViewMode.MONTH
        }

        // Initial render and title
        renderCurrentMode()

        // Menu in toolbar (calendar view switcher)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_calendar_view -> {
                        showViewModePopup()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onResume() {
        super.onResume()
        setTitleForMode()
    }

    private fun setTitleForMode() {
        val ab = (activity as? AppCompatActivity)?.supportActionBar ?: return
        when (viewMode) {
            ViewMode.MONTH -> ab.title = selectedYearMonth.atDay(1).format(monthFormatter)
            ViewMode.WEEK -> ab.title = ""
            ViewMode.DAY -> ab.title = selectedDate.format(dayFormatter)
        }
    }

    private fun showViewModePopup() {
        val anchor = requireActivity().findViewById<View>(R.id.toolbar) ?: binding.root
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        popup.menuInflater.inflate(R.menu.popup_calendar_view, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.view_month -> { viewMode = ViewMode.MONTH; renderCurrentMode(); true }
                R.id.view_week -> { viewMode = ViewMode.WEEK; renderCurrentMode(); true }
                R.id.view_day -> { viewMode = ViewMode.DAY; renderCurrentMode(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun renderCurrentMode() {
        when (viewMode) {
            ViewMode.MONTH -> {
                // Show grid, hide week and day views
                binding.calendarGrid.isVisible = true
                binding.weekScroll.isGone = true
                binding.dayScroll.isGone = true

                val days = generateMonthGrid(selectedYearMonth)
                applyData(days, spanCount = 7, rows = rowsCount)
            }
            ViewMode.WEEK -> {
                // Hide grid and day view, show week view
                binding.calendarGrid.isGone = true
                binding.dayScroll.isGone = true
                binding.weekScroll.isVisible = true

                val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                // No fake events: pass empty list
                binding.weekView.setWeek(startOfWeek, emptyList())

                // Auto-scroll to selected day horizontally and to current time vertically
                binding.weekScroll.post {
                    val targetX = binding.weekView.getScrollXForDate(selectedDate)
                    binding.weekHscroll.scrollTo(targetX, 0)
                    val nowY = binding.weekView.getScrollYForTime(LocalTime.now())
                    binding.weekScroll.scrollTo(0, nowY)
                }
            }
            ViewMode.DAY -> {
                // Hide grid and week view, show day view
                binding.calendarGrid.isGone = true
                binding.weekScroll.isGone = true
                binding.dayScroll.isVisible = true

                // No fake events: pass empty list
                binding.dayView.setDay(selectedDate, emptyList())

                // Auto-scroll to current time vertically
                binding.dayScroll.post {
                    val nowY = binding.dayView.getScrollYForTime(LocalTime.now())
                    binding.dayScroll.scrollTo(0, nowY)
                }
            }
        }
        setTitleForMode()
    }

    private fun applyData(days: List<CalendarDay>, spanCount: Int, rows: Int) {
        rowsCount = rows
        (binding.calendarGrid.layoutManager as? GridLayoutManager)?.spanCount = spanCount
        adapter.submit(days)
        binding.calendarGrid.post {
            val totalH = binding.calendarGrid.height
            if (totalH > 0 && rowsCount > 0) {
                val rowH = totalH / rowsCount
                adapter.setItemHeight(rowH)
            }
        }
    }

    // Remove fake count usage; always 0 events in month cells unless real data is provided
    private fun generateMonthGrid(yearMonth: YearMonth): List<CalendarDay> {
        val firstOfMonth = yearMonth.atDay(1)
        val lastOfMonth = yearMonth.atEndOfMonth()
        val start = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = mutableListOf<CalendarDay>()
        var d = start
        while (!d.isAfter(end)) {
            val inCurrent = d.month == yearMonth.month
            val eventCount = 0
            days += CalendarDay(d, inCurrentMonth = inCurrent, eventCount = eventCount)
            d = d.plusDays(1)
        }
        rowsCount = days.size / 7
        return days
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}