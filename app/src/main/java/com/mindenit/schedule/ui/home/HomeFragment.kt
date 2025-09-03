package com.mindenit.schedule.ui.home

import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import java.time.LocalDate
import androidx.lifecycle.lifecycleScope
import com.mindenit.schedule.data.EventRepository
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import androidx.navigation.NavController
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import com.mindenit.schedule.data.SchedulesStorage
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.fragment.app.setFragmentResultListener
import com.mindenit.schedule.data.ScheduleType
import com.mindenit.schedule.data.FiltersStorage

/**
 * Оптимізований HomeFragment з декомпозицією та розумним кешуванням
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Декомпоновані компоненти
    private lateinit var calendarState: CalendarState
    private lateinit var calendarLoader: CalendarLoader
    private lateinit var calendarManager: CalendarManager
    private lateinit var calendarNavigator: CalendarNavigator

    // State keys for saving/restoring fragment state
    companion object {
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SELECTED_YEAR = "selected_year"
        private const val KEY_SELECTED_MONTH = "selected_month"
        private const val KEY_SELECTED_DATE = "selected_date"
        private const val KEY_HAS_ACTIVE_SCHEDULE = "has_active_schedule"
    }

    private val logTag = "HomeFragment"
    private var navDestListener: NavController.OnDestinationChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ініціалізуємо компоненти
        calendarState = CalendarState(requireContext())

        // Відновлюємо стан якщо доступний
        savedInstanceState?.let { bundle ->
            calendarState.viewMode = CalendarState.ViewMode.valueOf(
                bundle.getString(KEY_VIEW_MODE, CalendarState.ViewMode.MONTH.name)
            )
            val year = bundle.getInt(KEY_SELECTED_YEAR, LocalDate.now().year)
            val month = bundle.getInt(KEY_SELECTED_MONTH, LocalDate.now().monthValue)
            calendarState.selectedYearMonth = java.time.YearMonth.of(year, month)
            val dateString = bundle.getString(KEY_SELECTED_DATE)
            if (dateString != null) {
                calendarState.selectedDate = LocalDate.parse(dateString)
            }
            calendarState.hasActiveSchedule = bundle.getBoolean(KEY_HAS_ACTIVE_SCHEDULE, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        // Ініціалізацію CalendarLoader/Manager переносимо в onViewCreated, де доступний viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(logTag, "onViewCreated: start")
        super.onViewCreated(view, savedInstanceState)

        // Listen for hidden subjects changes to refresh calendar
        requireActivity().supportFragmentManager.setFragmentResultListener("hidden_subjects_changed", viewLifecycleOwner) { _, _ ->
            calendarManager.refreshVisible()
            updateFabVisibility()
        }
        // Listen on Activity FragmentManager (EventDetailsBottomSheet posts to activity FM)
        setFragmentResultListener("hidden_subjects_changed") { _, _ ->
            calendarManager.refreshVisible()
            updateFabVisibility()
        }

        // Listen for active schedule changes to refresh calendar
        requireActivity().supportFragmentManager.setFragmentResultListener("active_schedule_changed", viewLifecycleOwner) { _, _ ->
            Log.d(logTag, "Active schedule changed, refreshing calendar")
            // Clear all caches when active schedule changes
            EventRepository.clearCacheForScheduleChange(requireContext())
            // Force re-render when active schedule changes
            calendarState.clearCache()
            renderCurrentMode()
            updateFabVisibility()
            (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        }

        // Listen for filters changes to refresh calendar (childFragmentManager, where dialog is shown)
        childFragmentManager.setFragmentResultListener("filters_changed", viewLifecycleOwner) { _, _ ->
            calendarManager.refreshVisible()
            updateFabVisibility()
            (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        }
        // Keep parent manager listener for other sources if any
        setFragmentResultListener("filters_changed") { _, _ ->
            calendarManager.refreshVisible()
            updateFabVisibility()
            (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        }

        // Prefetch events cache for current month (once per day) before rendering
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(logTag, "Starting prefetch for current month: ${calendarState.selectedYearMonth}")
                EventRepository.ensureMonthCached(requireContext(), calendarState.selectedYearMonth)
                Log.d(logTag, "Prefetch completed for current month")
            } catch (e: Throwable) {
                Log.e(logTag, "Failed to prefetch current month", e)
                /* keep old cache */
            }
        }

        // Ініціалізуємо компоненти після створення binding та наявності viewLifecycleOwner
        calendarLoader = CalendarLoader(binding, viewLifecycleOwner.lifecycleScope)
        calendarManager = CalendarManager(binding, calendarState, calendarLoader, viewLifecycleOwner.lifecycleScope)
        calendarNavigator = CalendarNavigator(this, calendarState, calendarManager, calendarLoader)
        // Update header when user swipes pages and also update FAB visibility
        calendarManager.onHeaderUpdate = {
            calendarNavigator.updateTitle()
            updateFabVisibility()
        }
        // Also re-apply title after NavigationUI sets destination label
        navDestListener = NavController.OnDestinationChangedListener { _, dest, _ ->
            if (dest.id == R.id.navigation_home) {
                calendarNavigator.updateTitle()
                updateFabVisibility()
            }
        }
        findNavController().addOnDestinationChangedListener(navDestListener!!)

        // FAB: jump to today/current period
        binding.fabToday.setOnClickListener { calendarManager.goToToday() }

        setupBackNavigation()
        applyDefaultViewMode()
        setupEmptyStateAction()
        setupToolbarMenu()

        // Перевіряємо чи потрібен ререндер календаря
        if (calendarState.shouldRerenderCalendar()) {
            Log.d(logTag, "onViewCreated: schedule changed, rendering calendar")
            renderCurrentMode()
        } else if (calendarState.hasActiveSchedule) {
            Log.d(logTag, "onViewCreated: quick restore without rerender")
            calendarNavigator.fastRestore()
            updateFabVisibility()
        } else {
            Log.d(logTag, "onViewCreated: showing empty state")
            showEmptyState()
        }

        calendarNavigator.updateTitle()
        updateFabVisibility()
        Log.d(logTag, "onViewCreated: completed")
    }

    private fun setupBackNavigation() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val handled = calendarNavigator.handleBackPressed { clickedDate ->
                        calendarState.selectedDate = clickedDate
                        calendarNavigator.goToMode(CalendarState.ViewMode.DAY)
                    }

                    if (!handled) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun applyDefaultViewMode() {
        val sp = calendarState.getPreferences()
        calendarState.viewMode = when (sp.getString("pref_default_view", "month")) {
            "week" -> CalendarState.ViewMode.WEEK
            "day" -> CalendarState.ViewMode.DAY
            else -> CalendarState.ViewMode.MONTH
        }
    }

    private fun setupEmptyStateAction() {
        binding.emptyAction.setOnClickListener {
            val navBar = calendarState.getCachedNavigationBar(requireActivity() as AppCompatActivity)
            navBar?.selectedItemId = R.id.navigation_dashboard
        }
    }

    private fun areGroupFiltersActive(): Boolean {
        val active = SchedulesStorage(requireContext()).getActive() ?: return false
        if (active.first != ScheduleType.GROUP) return false
        val f = FiltersStorage(requireContext()).get(active.first, active.second)
        return f.lessonTypes.isNotEmpty() || f.teachers.isNotEmpty() || f.auditoriums.isNotEmpty() || f.subjects.isNotEmpty()
    }

    private fun setupToolbarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_calendar_view)?.isVisible = calendarState.hasActiveSchedule
                menu.findItem(R.id.action_refresh_schedule)?.isVisible = calendarState.hasActiveSchedule
                val active = SchedulesStorage(requireContext()).getActive()
                val filtersItem = menu.findItem(R.id.action_filters)
                val filtersVisible = calendarState.hasActiveSchedule && active?.first == ScheduleType.GROUP
                filtersItem?.isVisible = filtersVisible
                if (filtersVisible) {
                    val badged = areGroupFiltersActive()
                    filtersItem?.setIcon(if (badged) R.drawable.ic_filter_badged_24 else R.drawable.ic_filter_24)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_calendar_view -> {
                        showViewModePopup()
                        true
                    }
                    R.id.action_refresh_schedule -> {
                        refreshSchedule()
                        true
                    }
                    R.id.action_filters -> {
                        FiltersDialogFragment().show(childFragmentManager, "filters_dialog")
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshSchedule() {
        val ctx = requireContext()
        val storage = SchedulesStorage(ctx)
        val active = storage.getActive()
        if (active == null) {
            Toast.makeText(ctx, R.string.no_active_schedule, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(ctx, R.string.refreshing_schedule, Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Do not clear cached events; only clear the 'stamp' to force re-fetch with diff check
                val base = YearMonth.now()
                for (offset in -11..12) {
                    val ym = base.plusMonths(offset.toLong())
                    try {
                        EventRepository.clearStampForMonth(ctx, ym)
                        EventRepository.ensureMonthCached(ctx, ym)
                    } catch (_: Throwable) { }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Refresh UI
                    calendarManager.refreshVisible()
                    calendarNavigator.updateTitle()
                    Toast.makeText(ctx, R.string.refresh_complete, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(logTag, "onResume: start")

        if (_binding == null) return

        // КЛЮЧОВА ОПТИМІЗАЦІЯ: перевіряємо чи дійсно потрібен ре��енд��р
        if (calendarState.shouldRerenderCalendar()) {
            Log.d(logTag, "onResume: schedule changed, force rerendering")
            renderCurrentMode()
        } else if (calendarState.hasEverBeenInitialized) {
            // Швидке відно��лення без ререндеру
            Log.d(logTag, "onResume: fast restore - no schedule changes")
            val restored = calendarNavigator.fastRestore()
            if (!restored && calendarState.hasActiveSchedule) {
                renderCurrentMode() // Fallback якщо швидке відновлення не вдалося
            }
        }

        // Оновлюємо меню і FAB
        (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        updateFabVisibility()
        Log.d(logTag, "onResume: completed")
    }

    // --- Helpers to control FAB visibility ---
    private fun isOnCurrentPeriod(): Boolean {
        return when (calendarState.viewMode) {
            CalendarState.ViewMode.MONTH -> calendarState.selectedYearMonth == YearMonth.now()
            CalendarState.ViewMode.WEEK -> {
                val selWeek = calendarState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val curWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                selWeek == curWeek
            }
            CalendarState.ViewMode.DAY -> calendarState.selectedDate == LocalDate.now()
        }
    }

    private fun updateFabVisibility() {
        val shouldShow = calendarState.hasActiveSchedule && !isOnCurrentPeriod()
        binding.fabToday.isVisible = shouldShow
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_VIEW_MODE, calendarState.viewMode.name)
        outState.putInt(KEY_SELECTED_YEAR, calendarState.selectedYearMonth.year)
        outState.putInt(KEY_SELECTED_MONTH, calendarState.selectedYearMonth.monthValue)
        outState.putString(KEY_SELECTED_DATE, calendarState.selectedDate.toString())
        outState.putBoolean(KEY_HAS_ACTIVE_SCHEDULE, calendarState.hasActiveSchedule)
    }

    private fun renderCurrentMode() {
        Log.d(logTag, "renderCurrentMode: mode=${calendarState.viewMode} hasActive=${calendarState.hasActiveSchedule}")

        if (!calendarState.hasActiveSchedule) {
            showEmptyState()
            return
        }

        // Ховаємо empty state
        binding.emptyState.isGone = true
        // Do not force-show FAB here; update visibility based on current period below

        // Рендеримо календар
        calendarNavigator.renderMode { clickedDate ->
            calendarState.selectedDate = clickedDate
            calendarNavigator.goToMode(CalendarState.ViewMode.DAY)
        }
        // Update FAB according to whether we're on the current period
        updateFabVisibility()
    }

    private fun showEmptyState() {
        // Ensure all calendar views and weekday header are hidden
        binding.weekdayHeader.isGone = true
        binding.calendarView.isGone = true
        binding.calendarPager.isGone = true
        binding.weekPager.isGone = true
        binding.dayPager.isGone = true
        // Show empty state only
        binding.loadingState.isGone = true
        binding.emptyState.isVisible = true
        binding.fabToday.isVisible = false
        // Invalidate the options menu so actions get hidden
        (activity as? AppCompatActivity)?.invalidateOptionsMenu()
    }

    private fun showViewModePopup() {
        val anchor = requireActivity().findViewById<View>(R.id.toolbar) ?: binding.root
        calendarNavigator.showViewModePopup(anchor) { clickedDate ->
            calendarState.selectedDate = clickedDate
            calendarNavigator.goToMode(CalendarState.ViewMode.DAY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Очищуємо ресурси
        navDestListener?.let { findNavController().removeOnDestinationChangedListener(it) }
        navDestListener = null
        calendarManager.cleanup()
        calendarState.clearCache()
        _binding = null
    }
}
