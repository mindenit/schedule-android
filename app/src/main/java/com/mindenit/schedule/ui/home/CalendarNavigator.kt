package com.mindenit.schedule.ui.home

import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.mindenit.schedule.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Навігатор для управління переходами між режимами календаря
 */
class CalendarNavigator(
    private val fragment: Fragment,
    private val calendarState: CalendarState,
    private val calendarManager: CalendarManager,
    private val calendarLoader: CalendarLoader
) {

    private val logTag = "CalendarNavigator"

    /**
     * Перехід між режимами календаря з оптимізацією
     */
    fun goToMode(newMode: CalendarState.ViewMode, push: Boolean = true, onDateClick: (LocalDate) -> Unit = {}) {
        if (newMode == calendarState.viewMode) {
            updateTitle()
            return
        }

        Log.d(logTag, "goToMode: switching from ${calendarState.viewMode} to $newMode")

        // Додаємо до стеку тільки якщо потрібно
        if (push) {
            calendarState.pushState(calendarState.viewMode, calendarState.selectedYearMonth, calendarState.selectedDate)
        }

        calendarState.viewMode = newMode

        // Оптимізація: показуємо лоадер тільки якщо календар ще не ініціалізований
        val needsLoader = !calendarState.hasEverBeenInitialized &&
                         calendarState.hasActiveSchedule &&
                         !calendarManager.isPagerInitialized(newMode)

        if (needsLoader) {
            Log.d(logTag, "goToMode: showing loader for first-time initialization")
        }

        // Рендеримо новий режим
        renderMode(onDateClick)
    }

    /**
     * Показує popup меню для вибору режиму календаря
     */
    fun showViewModePopup(anchor: View, onDateClick: (LocalDate) -> Unit = {}) {
        val popup = PopupMenu(fragment.requireContext(), anchor, Gravity.END)
        popup.menuInflater.inflate(R.menu.popup_calendar_view, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.view_month -> { goToMode(CalendarState.ViewMode.MONTH, true, onDateClick); true }
                R.id.view_week -> { goToMode(CalendarState.ViewMode.WEEK, true, onDateClick); true }
                R.id.view_day -> { goToMode(CalendarState.ViewMode.DAY, true, onDateClick); true }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Обробка системної кнопки "Назад"
     */
    fun handleBackPressed(onDateClick: (LocalDate) -> Unit = {}): Boolean {
        // Спробуємо повернутися до попереднього стану
        val previousState = calendarState.popState()
        if (previousState != null) {
            calendarState.selectedYearMonth = previousState.ym
            calendarState.selectedDate = previousState.date
            goToMode(previousState.mode, push = false, onDateClick)
            return true
        }

        // Якщо не в місячному режимі - перемикаємо на нього
        if (calendarState.viewMode != CalendarState.ViewMode.MONTH) {
            goToMode(CalendarState.ViewMode.MONTH, push = false, onDateClick)
            return true
        }

        return false // Дозволяємо стандартну обробку
    }

    /**
     * Рендер поточного режиму календаря
     */
    fun renderMode(onDateClick: (LocalDate) -> Unit = {}) {
        Log.d(logTag, "renderMode: mode=${calendarState.viewMode} hasActiveSchedule=${calendarState.hasActiveSchedule}")

        // Якщо немає активного розкладу - показуємо empty state
        if (!calendarState.hasActiveSchedule) {
            calendarLoader.hideOtherPagers(CalendarState.ViewMode.MONTH) // Ховаємо всі
            // Empty state logic would be handled in the main fragment
            return
        }

        // Оптимізація: перевіряємо чи потрібно оновлювати UI
        if (!calendarState.shouldUpdateModeUI(calendarState.viewMode) && calendarState.hasEverBeenInitialized) {
            Log.d(logTag, "renderMode: skipping render - no UI changes needed")
            updateTitle()
            return
        }

        // Ховаємо ін��і pager-и та показуємо потрібний
        calendarLoader.hideOtherPagers(calendarState.viewMode)
        calendarManager.setupPagerWithLoading(calendarState.viewMode, onDateClick)
        calendarManager.positionPager(calendarState.viewMode)

        updateTitle()
    }

    /**
     * Оновлює заголовок відповідно до поточного режиму
     */
    fun updateTitle() {
        val activity = fragment.activity as? AppCompatActivity ?: return
        val actionBar = activity.supportActionBar ?: return

        if (!calendarState.hasActiveSchedule) {
            actionBar.title = fragment.getString(R.string.title_home)
            return
        }

        when (calendarState.viewMode) {
            CalendarState.ViewMode.MONTH -> {
                actionBar.title = DateTitleFormatter.formatMonthTitle(calendarState.selectedYearMonth)
            }
            CalendarState.ViewMode.WEEK -> {
                actionBar.title = DateTitleFormatter.formatMonthTitle(YearMonth.from(calendarState.selectedDate))
            }
            CalendarState.ViewMode.DAY -> {
                actionBar.title = DateTitleFormatter.formatDayTitle(calendarState.selectedDate)
            }
        }
    }

    /**
     * Швидке відновлення при onResume (без ререндеру)
     */
    fun fastRestore(): Boolean {
        // Якщо календар вже був ініціалізований - просто відновлюємо поточний режим
        if (calendarState.hasEverBeenInitialized && calendarManager.isPagerInitialized(calendarState.viewMode)) {
            Log.d(logTag, "fastRestore: quick restore without rerender")
            calendarLoader.showCalendarInstantly(calendarState.viewMode)
            updateTitle()
            return true
        }
        return false
    }
}
