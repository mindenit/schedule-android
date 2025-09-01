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

        // Ініці��лізуємо компоненти після створення binding та наявності viewLifecycleOwner
        calendarLoader = CalendarLoader(binding, viewLifecycleOwner.lifecycleScope)
        calendarManager = CalendarManager(binding, calendarState, calendarLoader)
        calendarNavigator = CalendarNavigator(this, calendarState, calendarManager, calendarLoader)

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
        } else {
            Log.d(logTag, "onViewCreated: showing empty state")
            showEmptyState()
        }

        calendarNavigator.updateTitle()
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

    private fun setupToolbarMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_calendar_view)?.isVisible = calendarState.hasActiveSchedule
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
        Log.d(logTag, "onResume: start")

        if (_binding == null) return

        // КЛЮЧОВА ОПТИМІЗАЦІЯ: перевіряємо чи дійсно потрібен ререндер
        if (calendarState.shouldRerenderCalendar()) {
            Log.d(logTag, "onResume: schedule changed, force rerendering")
            renderCurrentMode()
        } else if (calendarState.hasEverBeenInitialized) {
            // Швидке відновлення без ререндеру
            Log.d(logTag, "onResume: fast restore - no schedule changes")
            val restored = calendarNavigator.fastRestore()
            if (!restored && calendarState.hasActiveSchedule) {
                renderCurrentMode() // Fallback якщо швидке відновлення не вдалося
            }
        }

        // Оновлюємо меню
        (activity as? AppCompatActivity)?.invalidateOptionsMenu()
        Log.d(logTag, "onResume: completed")
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

        // Рендеримо календар
        calendarNavigator.renderMode { clickedDate ->
            calendarState.selectedDate = clickedDate
            calendarNavigator.goToMode(CalendarState.ViewMode.DAY)
        }
    }

    private fun showEmptyState() {
        calendarLoader.hideOtherPagers(CalendarState.ViewMode.MONTH)
        binding.loadingState.isGone = true
        binding.emptyState.isVisible = true
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
        calendarManager.cleanup()
        calendarState.clearCache()
        _binding = null
    }
}
