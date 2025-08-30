package com.mindenit.schedule.ui.home

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent

class SwipeGestureHelper(
    private val view: View,
    private val condition: () -> Boolean,
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    touchSlopPx: Int? = null,
    private val velocityThresholdPx: Int? = null,
) {
    private var handled = false
    private val detector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            handled = false
            return condition()
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Do not consume taps; let the view handle click
            return false
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!condition() || e1 == null || handled) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > touchSlop) {
                handled = true
                (view.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                if (dx < 0) onSwipeLeft() else onSwipeRight()
                return true
            }
            return false
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (!condition() || handled) return false
            val threshold = velocityThresholdPx ?: 0
            if (kotlin.math.abs(velocityX) > threshold) {
                handled = true
                (view.parent as? ViewParent)?.requestDisallowInterceptTouchEvent(true)
                if (velocityX < 0) onSwipeLeft() else onSwipeRight()
                return true
            }
            return false
        }
    })

    private val touchSlop: Int = touchSlopPx ?: ViewConfiguration.get(view.context).scaledTouchSlop

    init {
        view.setOnTouchListener { v, event ->
            val result = detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                handled = false
                if (!result && !v.isPressed) v.performClick()
            }
            result
        }
    }
}

