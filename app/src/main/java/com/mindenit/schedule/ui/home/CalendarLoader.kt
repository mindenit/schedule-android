package com.mindenit.schedule.ui.home

import android.util.Log
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentHomeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Оптимізований лоадер календаря: показуємо оверлей, поки контент готується і верстається
 */
class CalendarLoader(
    private val binding: FragmentHomeBinding,
    private val scope: CoroutineScope
) {

    private var isCalendarLoading = false
    private var loadingStartTime = 0L
    private var hideJob: Job? = null

    private val fadeInDuration = 90L
    private val fadeOutDuration = 120L
    private val minVisibleDuration = 150L
    private val maxLoadingTime = 800L

    private val logTag = "CalendarLoader"

    fun showLoadingIfNeeded(calendarState: CalendarState, targetMode: CalendarState.ViewMode? = null): Boolean {
        // Показуємо лоадер тільки при першому завантаженні
        if (!calendarState.isFirstTimeLoad && calendarState.hasEverBeenInitialized) {
            Log.d(logTag, "Skipping loader - already initialized")
            return false
        }

        if (isCalendarLoading && binding.loadingState.isVisible) return true

        hideJob?.cancel(); hideJob = null
        isCalendarLoading = true
        loadingStartTime = System.currentTimeMillis()

        if (targetMode != null) prepareSkeleton(targetMode)

        scope.launch(Dispatchers.Main.immediate) {
            binding.emptyState.isGone = true
            binding.loadingState.alpha = 0f
            binding.loadingState.isVisible = true
            binding.loadingIndicator.show()
            binding.loadingText.setText(R.string.loading_calendar_preparing)
            binding.loadingState.animate().alpha(1f).setDuration(fadeInDuration).start()
        }

        scope.launch {
            delay(maxLoadingTime)
            if (isCalendarLoading) {
                Log.d(logTag, "Loading fallback triggered")
                hideLoading(calendarState) {}
            }
        }
        return true
    }

    // Робить потрібні вью видимими з alpha=0, інші ховає
    fun prepareSkeleton(mode: CalendarState.ViewMode) {
        scope.launch(Dispatchers.Main.immediate) {
            when (mode) {
                CalendarState.ViewMode.MONTH -> {
                    binding.weekPager.isGone = true
                    binding.dayPager.isGone = true
                    binding.weekdayHeader.isVisible = true
                    binding.calendarView.isVisible = true
                    binding.calendarView.alpha = 0f
                    // Old pager stays hidden
                    binding.calendarPager.isGone = true
                }
                CalendarState.ViewMode.WEEK -> {
                    binding.weekdayHeader.isGone = true
                    binding.calendarView.isGone = true
                    binding.calendarPager.isGone = true
                    binding.dayPager.isGone = true
                    binding.weekPager.isVisible = true
                    binding.weekPager.alpha = 0f
                }
                CalendarState.ViewMode.DAY -> {
                    binding.weekdayHeader.isGone = true
                    binding.calendarView.isGone = true
                    binding.calendarPager.isGone = true
                    binding.weekPager.isGone = true
                    binding.dayPager.isVisible = true
                    binding.dayPager.alpha = 0f
                }
            }
        }
    }

    // Плавне відкриття календаря під лоадером
    fun revealCalendar(mode: CalendarState.ViewMode, onRevealed: () -> Unit = {}) {
        scope.launch(Dispatchers.Main.immediate) {
            when (mode) {
                CalendarState.ViewMode.MONTH -> {
                    binding.weekdayHeader.animate().alpha(1f).setDuration(fadeInDuration).start()
                    binding.calendarView.animate().alpha(1f).setDuration(fadeInDuration).withEndAction(onRevealed).start()
                }
                CalendarState.ViewMode.WEEK -> {
                    binding.weekPager.animate().alpha(1f).setDuration(fadeInDuration).withEndAction(onRevealed).start()
                }
                CalendarState.ViewMode.DAY -> {
                    binding.dayPager.animate().alpha(1f).setDuration(fadeInDuration).withEndAction(onRevealed).start()
                }
            }
        }
    }

    /**
     * Приховує лоадер з красивою анімацією
     */
    fun hideLoading(calendarState: CalendarState, onComplete: () -> Unit) {
        if (!isCalendarLoading) {
            scope.launch(Dispatchers.Main.immediate) { onComplete() }
            return
        }
        val elapsed = System.currentTimeMillis() - loadingStartTime
        val waitMore = (minVisibleDuration - elapsed).coerceAtLeast(0L)

        hideJob?.cancel()
        hideJob = scope.launch(Dispatchers.Main) {
            if (waitMore > 0) delay(waitMore)
            isCalendarLoading = false
            binding.loadingState.animate()
                .alpha(0f)
                .setDuration(fadeOutDuration)
                .withEndAction {
                    binding.loadingIndicator.hide()
                    binding.loadingState.isGone = true
                    calendarState.markAsInitialized()
                    onComplete()
                }
                .start()
        }
    }

    // Сумісність: миттєвий показ, коли вже все готово
    fun showCalendarInstantly(mode: CalendarState.ViewMode) {
        scope.launch(Dispatchers.Main.immediate) {
            when (mode) {
                CalendarState.ViewMode.MONTH -> {
                    binding.weekdayHeader.isVisible = true
                    binding.calendarView.isVisible = true
                    binding.weekdayHeader.alpha = 1f
                    binding.calendarView.alpha = 1f
                    binding.calendarPager.isGone = true
                }
                CalendarState.ViewMode.WEEK -> {
                    binding.weekPager.isVisible = true
                    binding.weekPager.alpha = 1f
                }
                CalendarState.ViewMode.DAY -> {
                    binding.dayPager.isVisible = true
                    binding.dayPager.alpha = 1f
                }
            }
        }
    }

    fun hideOtherPagers(currentMode: CalendarState.ViewMode) {
        scope.launch(Dispatchers.Main.immediate) {
            when (currentMode) {
                CalendarState.ViewMode.MONTH -> { binding.weekPager.isGone = true; binding.dayPager.isGone = true; binding.calendarPager.isGone = true }
                CalendarState.ViewMode.WEEK -> { binding.weekdayHeader.isGone = true; binding.calendarView.isGone = true; binding.calendarPager.isGone = true; binding.dayPager.isGone = true }
                CalendarState.ViewMode.DAY -> { binding.weekdayHeader.isGone = true; binding.calendarView.isGone = true; binding.calendarPager.isGone = true; binding.weekPager.isGone = true }
            }
        }
    }

    val isLoading: Boolean get() = isCalendarLoading

    fun cleanup() { hideJob?.cancel(); hideJob = null; isCalendarLoading = false }
}
