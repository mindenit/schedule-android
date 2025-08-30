package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import androidx.preference.PreferenceManager
import com.mindenit.schedule.data.SchedulesStorage

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MonthAdapter
    private var rowsCount: Int = 6
    private var selectedYearMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()

    private enum class ViewMode { MONTH, WEEK, DAY }
    private var viewMode: ViewMode = ViewMode.MONTH

    // Animation flags
    private var isMonthAnimating = false
    private var isDayAnimating = false
    private var isWeekAnimating = false

    // Simple in-fragment back stack to remember previous view and selection
    private data class ViewState(val mode: ViewMode, val ym: YearMonth, val date: LocalDate)
    private val backStack = ArrayDeque<ViewState>()

    private var hasActiveSchedule: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Navigate between modes and optionally push current state
    private fun goToMode(newMode: ViewMode, push: Boolean = true) {
        if (newMode == viewMode) {
            setTitleForMode()
            return
        }
        if (push) backStack.addLast(ViewState(viewMode, selectedYearMonth, selectedDate))
        viewMode = newMode
        renderCurrentMode()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup Recycler
        adapter = MonthAdapter(emptyList()) { clickedDate ->
            selectedDate = clickedDate
            // From month grid to specific day
            goToMode(ViewMode.DAY)
        }
        binding.calendarGrid.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarGrid.setHasFixedSize(true)
        binding.calendarGrid.adapter = adapter

        // Attach swipe helpers instead of custom detectors
        SwipeGestureHelper(
            view = binding.calendarGrid,
            condition = { viewMode == ViewMode.MONTH && !isMonthAnimating },
            onSwipeLeft = { switchMonthAnimated(next = true) },
            onSwipeRight = { switchMonthAnimated(next = false) }
        )
        SwipeGestureHelper(
            view = binding.dayView,
            condition = { viewMode == ViewMode.DAY && !isDayAnimating },
            onSwipeLeft = { switchDayAnimated(next = true) },
            onSwipeRight = { switchDayAnimated(next = false) }
        )
        SwipeGestureHelper(
            view = binding.weekView,
            condition = { viewMode == ViewMode.WEEK && !isWeekAnimating },
            onSwipeLeft = { switchWeekAnimated(next = true) },
            onSwipeRight = { switchWeekAnimated(next = false) }
        )

        // Handle system Back with smart stack: pop previous view if present; else ensure Month; else default
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backStack.isNotEmpty()) {
                    val prev = backStack.removeLast()
                    selectedYearMonth = prev.ym
                    selectedDate = prev.date
                    goToMode(prev.mode, push = false)
                    return
                }
                if (viewMode != ViewMode.MONTH) {
                    goToMode(ViewMode.MONTH, push = false)
                } else {
                    // Let system handle
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Apply default view mode from settings before first render
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        when (sp.getString("pref_default_view", "month")) {
            "week" -> viewMode = ViewMode.WEEK
            "day" -> viewMode = ViewMode.DAY
            else -> viewMode = ViewMode.MONTH
        }

        // Determine active schedule and update UI before first render
        updateActiveState()

        // Wire empty action button to navigate to schedules screen via BottomNavigationView
        binding.emptyAction.setOnClickListener {
            val navBar = requireActivity().findViewById<BottomNavigationView>(R.id.nav_view)
            navBar?.selectedItemId = R.id.navigation_dashboard
        }

        // Initial render and title (only if active schedule exists)
        if (hasActiveSchedule) renderCurrentMode() else setTitleForMode()

        // Menu in toolbar (calendar view switcher)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }
            override fun onPrepareMenu(menu: Menu) {
                // Hide calendar view switcher when no active schedule
                menu.findItem(R.id.action_calendar_view)?.isVisible = hasActiveSchedule
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

    private fun switchMonthAnimated(next: Boolean) {
        if (isMonthAnimating) return
        val grid = binding.calendarGrid
        val width = grid.width.takeIf { it > 0 } ?: run {
            grid.post { switchMonthAnimated(next) }
            return
        }
        isMonthAnimating = true
        val dir = if (next) -1 else 1
        val outInterpolator = AccelerateInterpolator()
        val inInterpolator = AnimationUtils.loadInterpolator(requireContext(), android.R.interpolator.fast_out_slow_in)
        val outDuration = 100L
        val inDuration = 160L

        grid.animate().cancel()
        grid.animate()
            .translationX((dir * width).toFloat())
            .alpha(0f)
            .setDuration(outDuration)
            .setInterpolator(outInterpolator)
            .withEndAction {
                // Update data to new month
                selectedYearMonth = if (next) selectedYearMonth.plusMonths(1) else selectedYearMonth.minusMonths(1)
                val days = generateMonthGrid(selectedYearMonth)
                applyData(days, spanCount = 7, rows = rowsCount)
                setTitleForMode()

                // Prepare starting position off-screen opposite side
                grid.translationX = (-dir * width).toFloat()
                grid.alpha = 0f

                // Animate new month in
                grid.animate().cancel()
                grid.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(inDuration)
                    .setInterpolator(inInterpolator)
                    .withEndAction {
                        isMonthAnimating = false
                    }
                    .start()
            }
            .start()
    }

    private fun switchDayAnimated(next: Boolean) {
        if (isDayAnimating) return
        val dayView = binding.dayView
        val width = dayView.width.takeIf { it > 0 } ?: run {
            dayView.post { switchDayAnimated(next) }
            return
        }
        isDayAnimating = true
        val dir = if (next) -1 else 1
        val outInterpolator = AccelerateInterpolator()
        val inInterpolator = AnimationUtils.loadInterpolator(requireContext(), android.R.interpolator.fast_out_slow_in)
        val outDuration = 90L
        val inDuration = 150L

        dayView.animate().cancel()
        dayView.animate()
            .translationX((dir * width).toFloat())
            .alpha(0f)
            .setDuration(outDuration)
            .setInterpolator(outInterpolator)
            .withEndAction {
                // Update date and data
                selectedDate = if (next) selectedDate.plusDays(1) else selectedDate.minusDays(1)
                binding.dayView.setDay(selectedDate, emptyList())
                setTitleForMode()

                // Prepare incoming position
                dayView.translationX = (-dir * width).toFloat()
                dayView.alpha = 0f

                // Animate in and scroll to current time
                dayView.animate().cancel()
                dayView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(inDuration)
                    .setInterpolator(inInterpolator)
                    .withEndAction {
                        isDayAnimating = false
                        binding.dayScroll.post {
                            val nowY = binding.dayView.getScrollYForTime(LocalTime.now())
                            binding.dayScroll.scrollTo(0, nowY)
                        }
                    }
                    .start()
            }
            .start()
    }

    private fun switchWeekAnimated(next: Boolean) {
        if (isWeekAnimating) return
        val weekView = binding.weekView
        val width = weekView.width.takeIf { it > 0 } ?: run {
            weekView.post { switchWeekAnimated(next) }
            return
        }
        isWeekAnimating = true
        val dir = if (next) -1 else 1
        val outInterpolator = AccelerateInterpolator()
        val inInterpolator = AnimationUtils.loadInterpolator(requireContext(), android.R.interpolator.fast_out_slow_in)
        val outDuration = 100L
        val inDuration = 160L

        weekView.animate().cancel()
        weekView.animate()
            .translationX((dir * width).toFloat())
            .alpha(0f)
            .setDuration(outDuration)
            .setInterpolator(outInterpolator)
            .withEndAction {
                // Update selectedDate by a week and refresh week view
                selectedDate = if (next) selectedDate.plusDays(7) else selectedDate.minusDays(7)
                val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                weekView.setWeek(startOfWeek, emptyList())
                setTitleForMode() // remains empty for week

                // Prepare incoming position
                weekView.translationX = (-dir * width).toFloat()
                weekView.alpha = 0f

                weekView.animate().cancel()
                weekView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(inDuration)
                    .setInterpolator(inInterpolator)
                    .withEndAction {
                        isWeekAnimating = false
                        // Keep vertical scroll around current time after switch
                        binding.weekScroll.post {
                            val nowY = binding.weekView.getScrollYForTime(LocalTime.now())
                            binding.weekScroll.scrollTo(0, nowY)
                        }
                    }
                    .start()
            }
            .start()
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate active schedule on return to this screen
        updateActiveState()
        if (hasActiveSchedule) renderCurrentMode() else setTitleForMode()
        // Ask Activity to refresh menu visibility
        (activity as? AppCompatActivity)?.invalidateOptionsMenu()
    }

    private fun setTitleForMode() {
        val ab = (activity as? AppCompatActivity)?.supportActionBar ?: return
        if (!hasActiveSchedule) {
            ab.title = getString(R.string.title_home)
            return
        }
        when (viewMode) {
            ViewMode.MONTH -> ab.title = DateTitleFormatter.formatMonthTitle(selectedYearMonth)
            ViewMode.WEEK -> ab.title = ""
            ViewMode.DAY -> ab.title = DateTitleFormatter.formatDayTitle(selectedDate)
        }
    }

    private fun showViewModePopup() {
        val anchor = requireActivity().findViewById<View>(R.id.toolbar) ?: binding.root
        val popup = PopupMenu(requireContext(), anchor, Gravity.END)
        popup.menuInflater.inflate(R.menu.popup_calendar_view, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.view_month -> { goToMode(ViewMode.MONTH); true }
                R.id.view_week -> { goToMode(ViewMode.WEEK); true }
                R.id.view_day -> { goToMode(ViewMode.DAY); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun renderCurrentMode() {
        if (!hasActiveSchedule) {
            // Hide all calendar views and show empty state container
            binding.calendarGrid.isGone = true
            binding.weekScroll.isGone = true
            binding.dayScroll.isGone = true
            binding.emptyState.isVisible = true
            setTitleForMode()
            return
        }
        when (viewMode) {
            ViewMode.MONTH -> {
                // Show grid, hide week and day views
                binding.calendarGrid.isVisible = true
                binding.weekScroll.isGone = true
                binding.dayScroll.isGone = true
                binding.emptyState.isGone = true

                val days = generateMonthGrid(selectedYearMonth)
                applyData(days, spanCount = 7, rows = rowsCount)
            }
            ViewMode.WEEK -> {
                // Hide grid and day view, show week view
                binding.calendarGrid.isGone = true
                binding.dayScroll.isGone = true
                binding.weekScroll.isVisible = true
                binding.emptyState.isGone = true

                val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                // No fake events: pass empty list
                binding.weekView.setWeek(startOfWeek, emptyList())

                // Only vertical auto-scroll to current time (no horizontal scroll needed)
                binding.weekScroll.post {
                    val nowY = binding.weekView.getScrollYForTime(LocalTime.now())
                    binding.weekScroll.scrollTo(0, nowY)
                }
            }
            ViewMode.DAY -> {
                // Hide grid and week view, show day view
                binding.calendarGrid.isGone = true
                binding.weekScroll.isGone = true
                binding.dayScroll.isVisible = true
                binding.emptyState.isGone = true

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

    private fun updateActiveState() {
        hasActiveSchedule = SchedulesStorage(requireContext()).getActive() != null
        // Update empty state content and visibility
        binding.emptyText.text = getString(R.string.calendar_empty_state)
        binding.emptyState.isVisible = !hasActiveSchedule
        if (!hasActiveSchedule) {
            binding.calendarGrid.isGone = true
            binding.weekScroll.isGone = true
            binding.dayScroll.isGone = true
        }
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