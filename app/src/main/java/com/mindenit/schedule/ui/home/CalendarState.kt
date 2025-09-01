package com.mindenit.schedule.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mindenit.schedule.data.SchedulesStorage
import java.time.LocalDate
import java.time.YearMonth

/**
 * Управління станом календаря та кешування для оптимізації продуктивності
 */
class CalendarState(private val context: Context) {

    // ViewMode enum
    enum class ViewMode { MONTH, WEEK, DAY }

    // Current state
    var selectedYearMonth: YearMonth = YearMonth.now()
    var selectedDate: LocalDate = LocalDate.now()
    var viewMode: ViewMode = ViewMode.MONTH
    var hasActiveSchedule: Boolean = false

    // Cache variables
    private var cachedPreferences: SharedPreferences? = null
    private var cachedNavBar: BottomNavigationView? = null
    private var lastScheduleCheckTime = 0L
    private val scheduleCheckInterval = 300L // Зменшено з 500L для швидшої реакції

    // Initialization tracking
    var hasEverBeenInitialized = false
        private set
    var isFirstTimeLoad = true
        private set

    // Calendar caching
    private var lastActiveScheduleHash: Int = 0
    private var lastViewMode: ViewMode? = null

    fun markAsInitialized() {
        hasEverBeenInitialized = true
        isFirstTimeLoad = false
    }

    fun getPreferences(): SharedPreferences {
        return cachedPreferences ?: PreferenceManager.getDefaultSharedPreferences(context).also {
            cachedPreferences = it
        }
    }

    fun getCachedNavigationBar(activity: androidx.appcompat.app.AppCompatActivity): BottomNavigationView? {
        return cachedNavBar ?: activity.findViewById<BottomNavigationView>(com.mindenit.schedule.R.id.nav_view)?.also {
            cachedNavBar = it
        }
    }

    /**
     * Перевіряє чи змінився активний розклад
     * Повертає true тільки якщо дійсно потрібен ререндер
     */
    fun shouldRerenderCalendar(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScheduleCheckTime < scheduleCheckInterval) {
            return false // Skip check if called too frequently
        }
        lastScheduleCheckTime = currentTime

        val storage = SchedulesStorage(context)
        val previousState = hasActiveSchedule
        val previousHash = lastActiveScheduleHash

        hasActiveSchedule = storage.getActive() != null || storage.getAll().isNotEmpty()
        lastActiveScheduleHash = generateScheduleHash(storage)

        // Ререндер потрібен тільки якщо:
        // 1. Змінився стан наявності розкладу
        // 2. Змінився хеш розкладів (додали/видалили/змінили розклад)
        // 3. Це перший запуск
        return previousState != hasActiveSchedule ||
               previousHash != lastActiveScheduleHash ||
               !hasEverBeenInitialized
    }

    /**
     * Перевіряє чи потрібно оновити UI при зміні режиму
     */
    fun shouldUpdateModeUI(newMode: ViewMode): Boolean {
        val needsUpdate = lastViewMode != newMode
        lastViewMode = newMode
        return needsUpdate
    }

    private fun generateScheduleHash(storage: SchedulesStorage): Int {
        return try {
            val schedules = storage.getAll()
            val activeSchedule = storage.getActive()

            var hash = schedules.size
            hash = hash * 31 + (activeSchedule?.hashCode() ?: 0)
            schedules.forEach { schedule ->
                hash = hash * 31 + schedule.hashCode()
            }
            hash
        } catch (e: Exception) {
            0
        }
    }

    fun clearCache() {
        cachedNavBar = null
        cachedPreferences = null
        lastViewMode = null
        hasEverBeenInitialized = false
        isFirstTimeLoad = true
    }

    // Navigation state management
    data class ViewState(val mode: ViewMode, val ym: YearMonth, val date: LocalDate)
    val backStack = ArrayDeque<ViewState>()

    fun pushState(mode: ViewMode, ym: YearMonth, date: LocalDate) {
        backStack.addLast(ViewState(mode, ym, date))
    }

    fun popState(): ViewState? {
        return if (backStack.isNotEmpty()) backStack.removeLast() else null
    }
}
