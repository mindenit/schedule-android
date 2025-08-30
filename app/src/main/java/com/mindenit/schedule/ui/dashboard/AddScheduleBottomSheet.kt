package com.mindenit.schedule.ui.dashboard

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.mindenit.schedule.R

class AddScheduleBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { _: DialogInterface ->
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottomsheet_add_schedule, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)
        val tabs = view.findViewById<TabLayout>(R.id.tabs)
        val pagerAdapter = PagesAdapter(this)
        viewPager.adapter = pagerAdapter
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_groups)
                1 -> getString(R.string.tab_teachers)
                else -> getString(R.string.tab_auditoriums)
            }
        }.attach()

        // Removed result listener; child fragment will dismiss the sheet directly
    }

    companion object {
        const val REQUEST_KEY = "add_schedule_request"
        const val RESULT_TYPE = "type"
        const val RESULT_ID = "id"
        const val RESULT_NAME = "name"
        const val RESULT_PAYLOAD = "payload"
    }
}

private class PagesAdapter(parent: Fragment) : FragmentStateAdapter(parent) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment {
        val type = when (position) {
            0 -> "group"
            1 -> "teacher"
            else -> "auditorium"
        }
        return ListPageFragment.newInstance(type)
    }
}
