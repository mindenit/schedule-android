package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import androidx.preference.PreferenceManager
import com.mindenit.schedule.data.SchedulesStorage

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Month pager adapter/state
    private var monthPagerAdapter: MonthPagerAdapter? = null
    private var pagerInitialised = false
    private var pagerBaseYearMonth: YearMonth? = null

    // Week pager adapter/state
    private var weekPagerAdapter: WeekPagerAdapter? = null
    private var weekPagerInitialised = false
    private var pagerBaseStartOfWeek: LocalDate? = null

    // Day pager adapter/state
    private var dayPagerAdapter: DayPagerAdapter? = null
    private var dayPagerInitialised = false
    private var pagerBaseDate: LocalDate? = null

    private var selectedYearMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()

    private enum class ViewMode { MONTH, WEEK, DAY }
    private var viewMode: ViewMode = ViewMode.MONTH

    // Simple in-fragment back stack to remember previous view and selection
    private data class ViewState(val mode: ViewMode, val ym: YearMonth, val date: LocalDate)
    private val backStack = ArrayDeque<ViewState>()

    private var hasActiveSchedule: Boolean = false

    // Cache frequently used objects to reduce allocations
    private var cachedMenuProvider: MenuProvider? = null
    private var cachedPageChangeCallbacks = mutableMapOf<ViewMode, androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback>()

    // Optimize repeated calculations
    private var lastScheduleCheckTime = 0L
    private val scheduleCheckInterval = 500L // Check every 500ms max

    // Cache PreferenceManager to avoid repeated lookups
    private var cachedPreferences: android.content.SharedPreferences? = null

    // Enhanced lazy loading state management
    private var isCalendarLoading = false
    private var loadingStartTime = 0L
    private val minimumLoadingTime = 200L // Reduced minimum loading time
    private val maxLoadingTime = 1500L // Maximum loading time before timeout

    // Loading states with progress tracking
    private enum class LoadingState { IDLE, LOADING, LOADED, ERROR, TIMEOUT }
    private var currentLoadingState = LoadingState.IDLE

    // Progress tracking for smoother UX
    private var loadingProgress = 0f
    private val loadingSteps = arrayOf(
        R.string.loading_calendar_preparing,
        R.string.loading_calendar_events,
        R.string.loading_calendar_ready
    )
    private var currentLoadingStep = 0

    // Animation handlers for smooth transitions
    private val fadeInDuration = 150L
    private val fadeOutDuration = 100L

    // Preloading optimization
    private var isPreloadingEnabled = true
    private val preloadedViews = mutableSetOf<ViewMode>()

    // Add state keys for saving/restoring fragment state
    companion object {
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SELECTED_YEAR = "selected_year"
        private const val KEY_SELECTED_MONTH = "selected_month"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val KEY_HAS_ACTIVE_SCHEDULE = "has_active_schedule"
    }

    private val logTag = "HomeFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore state if available
        savedInstanceState?.let { bundle ->
            viewMode = ViewMode.valueOf(bundle.getString(KEY_VIEW_MODE, ViewMode.MONTH.name))
            val year = bundle.getInt(KEY_SELECTED_YEAR, LocalDate.now().year)
            val month = bundle.getInt(KEY_SELECTED_MONTH, LocalDate.now().monthValue)
            selectedYearMonth = YearMonth.of(year, month)
            val dateString = bundle.getString(KEY_SELECTED_DATE)
            if (dateString != null) {
                selectedDate = LocalDate.parse(dateString)
            }
            hasActiveSchedule = bundle.getBoolean(KEY_HAS_ACTIVE_SCHEDULE, false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_VIEW_MODE, viewMode.name)
        outState.putInt(KEY_SELECTED_YEAR, selectedYearMonth.year)
        outState.putInt(KEY_SELECTED_MONTH, selectedYearMonth.monthValue)
        outState.putString(KEY_SELECTED_DATE, selectedDate.toString())
        outState.putBoolean(KEY_HAS_ACTIVE_SCHEDULE, hasActiveSchedule)
    }

    // Navigate between modes and optionally push current state
    private fun goToMode(newMode: ViewMode, push: Boolean = true) {
        if (newMode == viewMode) {
            setTitleForMode()
            return
        }

        // Show loading when switching modes for better UX
        if (hasActiveSchedule) {
            when (newMode) {
                ViewMode.MONTH -> if (!pagerInitialised) showLoadingState()
                ViewMode.WEEK -> if (!weekPagerInitialised) showLoadingState()
                ViewMode.DAY -> if (!dayPagerInitialised) showLoadingState()
            }
        }

        if (push) backStack.addLast(ViewState(viewMode, selectedYearMonth, selectedDate))
        viewMode = newMode
        renderCurrentMode()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(logTag, "onViewCreated: start")
        super.onViewCreated(view, savedInstanceState)

        // Handle system Back with smart stack
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

        // Initial render and title
        renderCurrentMode()
        Log.d(logTag, "onViewCreated: render done for mode=$viewMode")
        setTitleForMode()

        // Menu in toolbar (calendar view switcher)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }
            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_calendar_view)?.isVisible = hasActiveSchedule
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_calendar_view -> { showViewModePopup(); true }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupMonthPagerIfNeeded() {
        if (pagerInitialised) return
        Log.d(logTag, "setupMonthPagerIfNeeded: init with base=$selectedYearMonth")
        val pager = binding.calendarPager
        pagerBaseYearMonth = selectedYearMonth
        monthPagerAdapter = MonthPagerAdapter(pagerBaseYearMonth!!) { clickedDate ->
            selectedDate = clickedDate
            goToMode(ViewMode.DAY)
        }
        pager.adapter = monthPagerAdapter
        pager.offscreenPageLimit = 2 // pre-render neighbors for smooth drag
        pager.setCurrentItem(MonthPagerAdapter.START_INDEX, false)

        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Update selected month and toolbar title relative to pager base
                val base = pagerBaseYearMonth ?: selectedYearMonth
                val diff = position - MonthPagerAdapter.START_INDEX
                selectedYearMonth = base.plusMonths(diff.toLong())
                setTitleForMode()
            }
        })
        pagerInitialised = true
    }

    private fun setupWeekPagerIfNeeded() {
        if (weekPagerInitialised) return
        Log.d(logTag, "setupWeekPagerIfNeeded: init with startOfWeek=${'$'}{selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))}")
        val pager = binding.weekPager
        val startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        pagerBaseStartOfWeek = startOfWeek
        weekPagerAdapter = WeekPagerAdapter(startOfWeek)
        pager.adapter = weekPagerAdapter
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(WeekPagerAdapter.START_INDEX, false)
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseStartOfWeek ?: selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val diff = position - WeekPagerAdapter.START_INDEX
                val start = base.plusWeeks(diff.toLong())
                selectedDate = start // snap selection to start of displayed week
                setTitleForMode()
            }
        })
        weekPagerInitialised = true
    }

    private fun setupDayPagerIfNeeded() {
        if (dayPagerInitialised) return
        Log.d(logTag, "setupDayPagerIfNeeded: init with baseDate=$selectedDate")
        val pager = binding.dayPager
        pagerBaseDate = selectedDate
        dayPagerAdapter = DayPagerAdapter(pagerBaseDate!!)
        pager.adapter = dayPagerAdapter
        pager.offscreenPageLimit = 2
        pager.setCurrentItem(DayPagerAdapter.START_INDEX, false)
        pager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val base = pagerBaseDate ?: selectedDate
                val diff = position - DayPagerAdapter.START_INDEX
                selectedDate = base.plusDays(diff.toLong())
                setTitleForMode()
            }
        })
        dayPagerInitialised = true
    }

    override fun onResume() {
        super.onResume()
        Log.d(logTag, "onResume: binding=${_binding != null}")

        // Only proceed if view is created and binding is available
        if (_binding == null) {
            return
        }

        // Re-evaluate active schedule on return to this screen
        val previousActiveState = hasActiveSchedule
        updateActiveState()

        // Only force refresh if active state changed or adapters are missing
        val needsRefresh = hasActiveSchedule != previousActiveState ||
                          (viewMode == ViewMode.MONTH && monthPagerAdapter == null) ||
                          (viewMode == ViewMode.WEEK && weekPagerAdapter == null) ||
                          (viewMode == ViewMode.DAY && dayPagerAdapter == null)

        if (needsRefresh) {
            Log.d(logTag, "onResume: forcing refresh due to state change or missing adapter")
            forceRefreshCurrentMode()
        } else {
            Log.d(logTag, "onResume: skipping refresh, adapters are ready")
            setTitleForMode()
        }

        // Ask Activity to refresh menu visibility
        (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        Log.d(logTag, "onResume: refreshed mode=$viewMode hasActiveSchedule=$hasActiveSchedule")
    }

    // Optimized force refresh to avoid unnecessary re-creation
    private fun forceRefreshCurrentMode() {
        when (viewMode) {
            ViewMode.MONTH -> {
                if (monthPagerAdapter == null || !pagerInitialised) {
                    pagerInitialised = false
                    setupMonthPagerIfNeeded()
                } else {
                    // Just ensure correct position without notifyDataSetChanged
                    val base = pagerBaseYearMonth ?: selectedYearMonth
                    val diff = getOptimizedMonthIndex(selectedYearMonth) - getOptimizedMonthIndex(base)
                    val target = MonthPagerAdapter.START_INDEX + diff
                    if (binding.calendarPager.currentItem != target) {
                        binding.calendarPager.setCurrentItem(target, false)
                      }
                }
            }
            ViewMode.WEEK -> {
                if (weekPagerAdapter == null || !weekPagerInitialised) {
                    weekPagerInitialised = false
                    setupWeekPagerIfNeeded()
                } else {
                    // Just ensure correct position without notifyDataSetChanged
                    val base = pagerBaseStartOfWeek ?: selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val targetStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weeksDiff = ChronoUnit.WEEKS.between(base, targetStart).toInt()
                    val target = WeekPagerAdapter.START_INDEX + weeksDiff
                    if (binding.weekPager.currentItem != target) {
                        binding.weekPager.setCurrentItem(target, false)
                    }
                }
            }
            ViewMode.DAY -> {
                if (dayPagerAdapter == null || !dayPagerInitialised) {
                    dayPagerInitialised = false
                    setupDayPagerIfNeeded()
                } else {
                    // Just ensure correct position without notifyDataSetChanged
                    val base = pagerBaseDate ?: selectedDate
                    val daysDiff = ChronoUnit.DAYS.between(base, selectedDate).toInt()
                    val target = DayPagerAdapter.START_INDEX + daysDiff
                    if (binding.dayPager.currentItem != target) {
                        binding.dayPager.setCurrentItem(target, false)
                    }
                }
            }
        }
        renderCurrentMode()
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
        Log.d(logTag, "renderCurrentMode: mode=$viewMode hasActiveSchedule=$hasActiveSchedule")

        // Always hide empty state during render to avoid overlaying calendar UI
        binding.emptyState.isGone = true

        // If no active schedule, show empty state instead of loading
        if (!hasActiveSchedule) {
            binding.loadingState.isGone = true
            binding.emptyState.isVisible = true
            return
        }

        when (viewMode) {
            ViewMode.MONTH -> {
                // Use lazy loading for month view
                setupMonthPagerWithLoading()

                // Position will be set after loading completes
                if (pagerInitialised) {
                    val base = pagerBaseYearMonth ?: selectedYearMonth
                    val diff = getOptimizedMonthIndex(selectedYearMonth) - getOptimizedMonthIndex(base)
                    val target = MonthPagerAdapter.START_INDEX + diff
                    if (binding.calendarPager.currentItem != target) {
                        binding.calendarPager.setCurrentItem(target, false)
                    }
                }
            }
            ViewMode.WEEK -> {
                // Use lazy loading for week view
                setupWeekPagerWithLoading()

                if (weekPagerInitialised) {
                    val base = pagerBaseStartOfWeek ?: selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val targetStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val weeksDiff = ChronoUnit.WEEKS.between(base, targetStart).toInt()
                    val target = WeekPagerAdapter.START_INDEX + weeksDiff
                    if (binding.weekPager.currentItem != target) {
                        binding.weekPager.setCurrentItem(target, false)
                    }
                }
            }
            ViewMode.DAY -> {
                // Use lazy loading for day view
                setupDayPagerWithLoading()

                if (dayPagerInitialised) {
                    val base = pagerBaseDate ?: selectedDate
                    val daysDiff = ChronoUnit.DAYS.between(base, selectedDate).toInt()
                    val target = DayPagerAdapter.START_INDEX + daysDiff
                    if (binding.dayPager.currentItem != target) {
                        binding.dayPager.setCurrentItem(target, false)
                    }
                }
            }
        }
        setTitleForMode()
    }

    private fun updateActiveState() {
        // Throttle schedule checks to avoid excessive I/O operations
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScheduleCheckTime < scheduleCheckInterval) {
            return // Skip check if called too frequently
        }
        lastScheduleCheckTime = currentTime

        val storage = SchedulesStorage(requireContext())
        hasActiveSchedule = storage.getActive() != null || storage.getAll().isNotEmpty()
        // Do not toggle UI visibility here; renderCurrentMode controls it
    }

    // Cache expensive findViewById operations
    private var cachedNavBar: BottomNavigationView? = null
    private fun getNavigationBar(): BottomNavigationView? {
        return cachedNavBar ?: requireActivity().findViewById<BottomNavigationView>(R.id.nav_view).also {
            cachedNavBar = it
        }
    }

    // Optimize preference access with caching
    private fun getPreferences(): android.content.SharedPreferences {
        return cachedPreferences ?: PreferenceManager.getDefaultSharedPreferences(requireContext()).also {
            cachedPreferences = it
        }
    }

    // Optimize pager positioning with cached calculations
    private var cachedMonthIndex: Int = -1
    private var cachedMonthYearMonth: YearMonth? = null

    private fun getOptimizedMonthIndex(ym: YearMonth): Int {
        return if (cachedMonthYearMonth == ym && cachedMonthIndex != -1) {
            cachedMonthIndex
        } else {
            val index = ym.year * 12 + ym.monthValue
            cachedMonthIndex = index
            cachedMonthYearMonth = ym
            index
        }
    }
    // Enhanced lazy loading management methods with animations
    private fun showLoadingState() {
        if (isCalendarLoading) return // Already loading

        isCalendarLoading = true
        loadingStartTime = System.currentTimeMillis()
        currentLoadingState = LoadingState.LOADING
        currentLoadingStep = 0
        loadingProgress = 0f

        Log.d(logTag, "showLoadingState: starting optimized calendar load")

        // Start with fade out animation for smoother transition
        animateViewsOut {
            // Hide all calendar views
            binding.weekdayHeader.isGone = true
            binding.calendarPager.isGone = true
            binding.weekPager.isGone = true
            binding.dayPager.isGone = true
            binding.emptyState.isGone = true

            // Show loading with fade in animation
            binding.loadingState.alpha = 0f
            binding.loadingState.isVisible = true
            binding.loadingState.animate()
                .alpha(1f)
                .setDuration(fadeInDuration)
                .start()

            // Start enhanced loading animation
            startEnhancedLoadingAnimation()
        }
    }

    private fun hideLoadingState(onComplete: (() -> Unit)? = null) {
        if (!isCalendarLoading) {
            onComplete?.invoke()
            return
        }

        val loadingDuration = System.currentTimeMillis() - loadingStartTime
        val remainingTime = minimumLoadingTime - loadingDuration

        if (remainingTime > 0) {
            // Ensure minimum loading time for smooth UX
            binding.root.postDelayed({
                completeLoadingHide(onComplete)
            }, remainingTime)
        } else {
            completeLoadingHide(onComplete)
        }
    }

    private fun completeLoadingHide(onComplete: (() -> Unit)?) {
        Log.d(logTag, "completeLoadingHide: hiding optimized loading state")

        isCalendarLoading = false
        currentLoadingState = LoadingState.LOADED

        // Smooth fade out of loading state
        binding.loadingState.animate()
            .alpha(0f)
            .setDuration(fadeOutDuration)
            .withEndAction {
                binding.loadingState.isGone = true
                binding.loadingIndicator.hide()

                // Execute completion callback with calendar reveal animation
                onComplete?.invoke()
            }
            .start()
    }

    private fun animateViewsOut(onComplete: () -> Unit) {
        val activeViews = listOfNotNull(
            binding.weekdayHeader.takeIf { it.isVisible },
            binding.calendarPager.takeIf { it.isVisible },
            binding.weekPager.takeIf { it.isVisible },
            binding.dayPager.takeIf { it.isVisible }
        )

        if (activeViews.isEmpty()) {
            onComplete()
            return
        }

        var animationsCompleted = 0
        activeViews.forEach { view ->
            view.animate()
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .withEndAction {
                    animationsCompleted++
                    if (animationsCompleted == activeViews.size) {
                        onComplete()
                    }
                }
                .start()
        }
    }

    private fun startEnhancedLoadingAnimation() {
        binding.loadingIndicator.show()
        updateLoadingStep(0)

        // Simulate progressive loading steps
        binding.root.postDelayed({ updateLoadingStep(1) }, 100)
        binding.root.postDelayed({ updateLoadingStep(2) }, 200)
    }

    private fun updateLoadingStep(step: Int) {
        if (step >= loadingSteps.size || !isCalendarLoading) return

        currentLoadingStep = step
        loadingProgress = (step + 1) / loadingSteps.size.toFloat()

        // Update loading text with smooth transition
        binding.loadingText.animate()
            .alpha(0f)
            .setDuration(50)
            .withEndAction {
                binding.loadingText.setText(loadingSteps[step])
                binding.loadingText.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    // Enhanced setup methods with optimized loading and preloading
    private fun setupMonthPagerWithLoading() {
        if (pagerInitialised) {
            // If already initialized, show calendar immediately with smooth transition
            showCalendarWithAnimation(ViewMode.MONTH)
            return
        }

        // Check if we can preload this view
        if (preloadedViews.contains(ViewMode.MONTH)) {
            Log.d(logTag, "Using preloaded month view")
            setupMonthPagerIfNeeded()
            showCalendarWithAnimation(ViewMode.MONTH)
            return
        }

        showLoadingState()

        // Optimize loading timing based on view complexity
        val loadingDelay = if (hasActiveSchedule) 150L else 50L

        binding.root.postDelayed({
            if (isCalendarLoading) { // Check if still loading
                setupMonthPagerIfNeeded()
                hideLoadingState {
                    showCalendarWithAnimation(ViewMode.MONTH)
                    Log.d(logTag, "Month calendar loaded and displayed with animation")

                    // Start preloading other views in background
                    startBackgroundPreloading()
                }
            }
        }, loadingDelay)
    }

    private fun setupWeekPagerWithLoading() {
        if (weekPagerInitialised) {
            showCalendarWithAnimation(ViewMode.WEEK)
            return
        }

        if (preloadedViews.contains(ViewMode.WEEK)) {
            Log.d(logTag, "Using preloaded week view")
            setupWeekPagerIfNeeded()
            showCalendarWithAnimation(ViewMode.WEEK)
            return
        }

        showLoadingState()

        binding.root.postDelayed({
            if (isCalendarLoading) {
                setupWeekPagerIfNeeded()
                hideLoadingState {
                    showCalendarWithAnimation(ViewMode.WEEK)
                    Log.d(logTag, "Week calendar loaded and displayed with animation")
                }
            }
        }, 100)
    }

    private fun setupDayPagerWithLoading() {
        if (dayPagerInitialised) {
            showCalendarWithAnimation(ViewMode.DAY)
            return
        }

        if (preloadedViews.contains(ViewMode.DAY)) {
            Log.d(logTag, "Using preloaded day view")
            setupDayPagerIfNeeded()
            showCalendarWithAnimation(ViewMode.DAY)
            return
        }

        showLoadingState()

        binding.root.postDelayed({
            if (isCalendarLoading) {
                setupDayPagerIfNeeded()
                hideLoadingState {
                    showCalendarWithAnimation(ViewMode.DAY)
                    Log.d(logTag, "Day calendar loaded and displayed with animation")
                }
            }
        }, 80)
    }

    private fun showCalendarWithAnimation(mode: ViewMode) {
        when (mode) {
            ViewMode.MONTH -> {
                binding.weekdayHeader.alpha = 0f
                binding.calendarPager.alpha = 0f
                binding.weekdayHeader.isVisible = true
                binding.calendarPager.isVisible = true

                binding.weekdayHeader.animate().alpha(1f).setDuration(fadeInDuration).start()
                binding.calendarPager.animate().alpha(1f).setDuration(fadeInDuration).start()
            }
            ViewMode.WEEK -> {
                binding.weekPager.alpha = 0f
                binding.weekPager.isVisible = true
                binding.weekPager.animate().alpha(1f).setDuration(fadeInDuration).start()
            }
            ViewMode.DAY -> {
                binding.dayPager.alpha = 0f
                binding.dayPager.isVisible = true
                binding.dayPager.animate().alpha(1f).setDuration(fadeInDuration).start()
            }
        }
    }

    // Background preloading for better performance
    private fun startBackgroundPreloading() {
        if (!isPreloadingEnabled) return

        binding.root.postDelayed({
            if (!isCalendarLoading && hasActiveSchedule) {
                Log.d(logTag, "Starting background preloading")

                // Preload week view if not current mode
                if (viewMode != ViewMode.WEEK && !weekPagerInitialised && !preloadedViews.contains(ViewMode.WEEK)) {
                    setupWeekPagerIfNeeded()
                    preloadedViews.add(ViewMode.WEEK)
                    Log.d(logTag, "Preloaded week view")
                }

                // Preload day view if not current mode
                if (viewMode != ViewMode.DAY && !dayPagerInitialised && !preloadedViews.contains(ViewMode.DAY)) {
                    binding.root.postDelayed({
                        setupDayPagerIfNeeded()
                        preloadedViews.add(ViewMode.DAY)
                        Log.d(logTag, "Preloaded day view")
                    }, 200)
                }
            }
        }, 500) // Start preloading after main view is settled
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up adapters and reset flags when view is destroyed
        monthPagerAdapter = null
        weekPagerAdapter = null
        dayPagerAdapter = null
        pagerInitialised = false
        weekPagerInitialised = false
        dayPagerInitialised = false
        _binding = null
    }
}
