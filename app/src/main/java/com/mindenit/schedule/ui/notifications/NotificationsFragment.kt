package com.mindenit.schedule.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mindenit.schedule.R
import com.mindenit.schedule.databinding.FragmentNotificationsBinding
import androidx.preference.PreferenceManager

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.themeRow.setOnClickListener { showThemeDialog() }
        binding.defaultViewRow.setOnClickListener { showDefaultViewDialog() }
        syncDefaultViewSummary()
    }

    private fun showThemeDialog() {
        val ctx = requireContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = sp.getString("pref_theme", "system") ?: "system"
        val entries = resources.getStringArray(R.array.pref_theme_entries)
        val values = resources.getStringArray(R.array.pref_theme_values)
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.pref_theme_title)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                val v = values[which]
                applyTheme(v)
                sp.edit().putString("pref_theme", v).apply()
                dialog.dismiss()
                requireActivity().recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDefaultViewDialog() {
        val ctx = requireContext()
        val sp = PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = sp.getString("pref_default_view", "month") ?: "month"
        val entries = resources.getStringArray(R.array.pref_default_view_entries)
        val values = resources.getStringArray(R.array.pref_default_view_values)
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_default_view_title)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                val v = values[which]
                sp.edit().putString("pref_default_view", v).apply()
                dialog.dismiss()
                syncDefaultViewSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun syncDefaultViewSummary() {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val current = sp.getString("pref_default_view", "month") ?: "month"
        val entries = resources.getStringArray(R.array.pref_default_view_entries)
        val values = resources.getStringArray(R.array.pref_default_view_values)
        val idx = values.indexOf(current).coerceAtLeast(0)
        binding.defaultViewSummary.text = entries.getOrNull(idx) ?: getString(R.string.settings_default_view_summary)
    }

    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}