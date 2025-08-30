package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.view.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewParent
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
import androidx.recyclerview.widget.GridLayoutManager
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
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

    // Gesture handling for month switching
    private var isMonthAnimating = false
    private lateinit var monthGestureDetector: GestureDetector
    private var monthSwipeHandled = false

    // Day view gesture state
    private var isDayAnimating = false
    private lateinit var dayGestureDetector: GestureDetector
    private var daySwipeHandled = false

    // Week view gesture state
    private var isWeekAnimating = false
    private lateinit var weekGestureDetector: GestureDetector
    private var weekSwipeHandled = false

    private val localeUk = Locale.forLanguageTag("uk")

    // Helpers: Ukrainian title-case of first letter
    private fun ukTitleCase(s: String): String = s.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(localeUk) else ch.toString()
    }

    private fun formatMonthTitle(ym: YearMonth): String {
        val monthName = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, localeUk)
        return "${ukTitleCase(monthName)} ${ym.year}"
    }

    private fun formatDayTitle(date: LocalDate): String {
        val dow = ukTitleCase(date.dayOfWeek.getDisplayName(TextStyle.FULL, localeUk))
        // Use MMMM to get genitive month when used with day number
        val datePart = date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", localeUk))
        // Keep month in genitive lowercase; only day-of-week capitalized per UA style
        return "$dow, $datePart"
    }

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

        // Gestures for month switching (Month View only)
        setupMonthSwipeGestures()
        // Gestures for day switching (Day View only)
        setupDaySwipeGestures()
        // Gestures for week switching (Week View only)
        setupWeekSwipeGestures()

        // Handle system Back: in Day view return to Month view
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewMode == ViewMode.DAY) {
                    viewMode = ViewMode.MONTH
                    renderCurrentMode()
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

    private fun setupMonthSwipeGestures() {
        val touchSlopPx = dpToPx(24f)
        val velocityThreshold = dpToPx(200f)
        monthGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // Start tracking a new gesture only in Month mode and when not animating
                monthSwipeHandled = false
                return viewMode == ViewMode.MONTH && !isMonthAnimating
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                return false
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (viewMode != ViewMode.MONTH || isMonthAnimating || e1 == null || monthSwipeHandled) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > touchSlopPx) {
                    // Lock this gesture to avoid double triggering and disallow parent intercept
                    monthSwipeHandled = true
                    (binding.calendarGrid.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (dx < 0) switchMonthAnimated(next = true) else switchMonthAnimated(next = false)
                    return true
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (viewMode != ViewMode.MONTH || isMonthAnimating || monthSwipeHandled) return false
                if (kotlin.math.abs(velocityX) > velocityThreshold) {
                    monthSwipeHandled = true
                    (binding.calendarGrid.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (velocityX < 0) switchMonthAnimated(next = true) else switchMonthAnimated(next = false)
                    return true
                }
                return false
            }
        })

        binding.calendarGrid.setOnTouchListener { _, event ->
            if (viewMode != ViewMode.MONTH) return@setOnTouchListener false
            val handled = monthGestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                monthSwipeHandled = false
            }
            handled
        }
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

    private fun setupDaySwipeGestures() {
        val touchSlopPx = dpToPx(24f)
        val velocityThreshold = dpToPx(200f)
        dayGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                daySwipeHandled = false
                return viewMode == ViewMode.DAY && !isDayAnimating
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean = false
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (viewMode != ViewMode.DAY || isDayAnimating || e1 == null || daySwipeHandled) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > touchSlopPx) {
                    daySwipeHandled = true
                    (binding.dayView.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (dx < 0) switchDayAnimated(next = true) else switchDayAnimated(next = false)
                    return true
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (viewMode != ViewMode.DAY || isDayAnimating || daySwipeHandled) return false
                if (kotlin.math.abs(velocityX) > velocityThreshold) {
                    daySwipeHandled = true
                    (binding.dayView.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (velocityX < 0) switchDayAnimated(next = true) else switchDayAnimated(next = false)
                    return true
                }
                return false
            }
        })

        binding.dayView.setOnTouchListener { _, event ->
            if (viewMode != ViewMode.DAY) return@setOnTouchListener false
            val handled = dayGestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                daySwipeHandled = false
            }
            handled
        }
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

    private fun setupWeekSwipeGestures() {
        val touchSlopPx = dpToPx(24f)
        val velocityThreshold = dpToPx(200f)
        weekGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                weekSwipeHandled = false
                return viewMode == ViewMode.WEEK && !isWeekAnimating
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean = false
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (viewMode != ViewMode.WEEK || isWeekAnimating || e1 == null || weekSwipeHandled) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > touchSlopPx) {
                    weekSwipeHandled = true
                    (binding.weekView.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (dx < 0) switchWeekAnimated(next = true) else switchWeekAnimated(next = false)
                    return true
                }
                return false
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (viewMode != ViewMode.WEEK || isWeekAnimating || weekSwipeHandled) return false
                if (kotlin.math.abs(velocityX) > velocityThreshold) {
                    weekSwipeHandled = true
                    (binding.weekView.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                    if (velocityX < 0) switchWeekAnimated(next = true) else switchWeekAnimated(next = false)
                    return true
                }
                return false
            }
        })

        binding.weekView.setOnTouchListener { _, event ->
            if (viewMode != ViewMode.WEEK) return@setOnTouchListener false
            val handled = weekGestureDetector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                weekSwipeHandled = false
            }
            handled
        }
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

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        setTitleForMode()
    }

    private fun setTitleForMode() {
        val ab = (activity as? AppCompatActivity)?.supportActionBar ?: return
        when (viewMode) {
            ViewMode.MONTH -> ab.title = formatMonthTitle(selectedYearMonth)
            ViewMode.WEEK -> ab.title = ""
            ViewMode.DAY -> ab.title = formatDayTitle(selectedDate)
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