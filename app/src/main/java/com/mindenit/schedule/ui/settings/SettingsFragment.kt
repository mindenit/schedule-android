package com.mindenit.schedule.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mindenit.schedule.R
import com.mindenit.schedule.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.mindenit.schedule.data.EventRepository
import com.mindenit.schedule.data.SchedulesStorage
import com.mindenit.schedule.data.SubjectLinksStorage
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import com.mindenit.schedule.ui.notifications.NotificationsScheduler
import com.mindenit.schedule.data.HiddenSubjectsStorage

class SettingsFragment : Fragment() {
    private var healthContainer: View? = null
    private var healthBeacon: View? = null
    private var healthStatus: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        healthContainer = root.findViewById(R.id.healthContainer)
        healthBeacon = root.findViewById(R.id.healthBeacon)
        healthStatus = root.findViewById(R.id.healthStatus)

        // Manage links navigation
        root.findViewById<View>(R.id.manage_links_row)?.setOnClickListener {
            findNavController().navigate(R.id.navigation_manage_links)
        }
        // Manage hidden subjects navigation
        root.findViewById<View>(R.id.manage_hidden_subjects_row)?.setOnClickListener {
            findNavController().navigate(R.id.navigation_manage_hidden_subjects)
        }
        // Logs navigation
        root.findViewById<View>(R.id.logs_row)?.setOnClickListener {
            findNavController().navigate(R.id.navigation_logs)
        }

        // Theme selection
        root.findViewById<View>(R.id.theme_row)?.setOnClickListener { showThemeDialog() }
        // Default view selection
        root.findViewById<View>(R.id.default_view_row)?.setOnClickListener { showDefaultViewDialog() }
        // Notifications lead time
        root.findViewById<View>(R.id.notifications_row)?.setOnClickListener { showNotificationsDialog() }

        // Clear all data
        root.findViewById<View>(R.id.btn_clear_data)?.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_clear_all)
                .setMessage(R.string.settings_clear_all_desc)
                .setPositiveButton(R.string.confirm) { d, _ ->
                    clearAllAppData()
                    d.dismiss()
                    Toast.makeText(requireContext(), R.string.cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Support button
        root.findViewById<View>(R.id.btn_support)?.setOnClickListener {
            val url = "https://base.monobank.ua/93NFt4Rk2fM3vF"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        // Initialize summaries
        updateThemeSummary(root)
        updateDefaultViewSummary(root)
        updateNotificationsSummary(root)

        return root
    }

    private fun updateThemeSummary(root: View) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mode = sp.getString("pref_theme", "system")
        val summary = when (mode) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }
        root.findViewById<TextView>(R.id.theme_summary)?.text = summary
    }

    private fun updateDefaultViewSummary(root: View) {
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val v = sp.getString("pref_default_view", "month")
        val summary = when (v) {
            "week" -> getString(R.string.default_view_week)
            "day" -> getString(R.string.default_view_day)
            else -> getString(R.string.default_view_month)
        }
        root.findViewById<TextView>(R.id.default_view_summary)?.text = summary
    }

    private fun updateNotificationsSummary(root: View) {
        val minutes = getSelectedOffsets()
        val summary = if (minutes.isEmpty()) "—" else minutes.joinToString(", ")
        root.findViewById<TextView>(R.id.notifications_summary)?.text = getString(R.string.settings_notifications_summary, summary)
    }

    private fun showThemeDialog() {
        val ctx = requireContext()
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = sp.getString("pref_theme", "system")
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val values = arrayOf("system", "light", "dark")
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.pref_theme_title)
            .setSingleChoiceItems(options, checked) { dialog: DialogInterface, which: Int ->
                val value = values[which]
                sp.edit().putString("pref_theme", value).apply()
                applyTheme(value)
                updateThemeSummary(requireView())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme(value: String?) {
        when (value) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showDefaultViewDialog() {
        val ctx = requireContext()
        val sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val current = sp.getString("pref_default_view", "month")
        val options = arrayOf(
            getString(R.string.default_view_month),
            getString(R.string.default_view_week),
            getString(R.string.default_view_day)
        )
        val values = arrayOf("month", "week", "day")
        val checked = values.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_default_view_title)
            .setSingleChoiceItems(options, checked) { dialog: DialogInterface, which: Int ->
                val value = values[which]
                sp.edit().putString("pref_default_view", value).apply()
                updateDefaultViewSummary(requireView())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNotificationsDialog() {
        val ctx = requireContext()
        val options = (5..30 step 5).toList()
        val labels = options.map { "$it хв" }.toTypedArray()
        val selected = getSelectedOffsets().toMutableSet()
        val checked = options.map { selected.contains(it) }.toBooleanArray()

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_notifications_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val value = options[which]
                if (isChecked) selected.add(value) else selected.remove(value)
            }
            .setPositiveButton(R.string.confirm) { d, _ ->
                saveSelectedOffsets(selected)
                updateNotificationsSummary(requireView())
                // Reschedule notifications according to new preferences off the main thread
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                    NotificationsScheduler.scheduleForUpcoming(ctx)
                }
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getSelectedOffsets(): List<Int> {
        val sp = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val raw = sp.getString("pref_notif_offsets", null)
        val list = raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 5..30 && it % 5 == 0 }?.sorted()
        return if (list.isNullOrEmpty()) listOf(5) else list
    }

    private fun saveSelectedOffsets(values: Set<Int>) {
        val normalized = values.filter { it in 5..30 && it % 5 == 0 }.sorted().ifEmpty { listOf(5) }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .edit().putString("pref_notif_offsets", normalized.joinToString(",")).apply()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkHealth()
        healthContainer?.setOnClickListener { checkHealth() }
    }

    private fun checkHealth() {
        healthStatus?.text = getString(R.string.loading)
        setBeacon(false, false)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val resp = ApiClient.api.health()
                resp.code() in 200..299
            } catch (_: Throwable) { false }
            withContext(Dispatchers.Main) {
                healthStatus?.text = if (ok) getString(R.string.api_ok) else getString(R.string.api_down)
                setBeacon(true, ok)
            }
        }
    }

    private fun setBeacon(visible: Boolean, ok: Boolean) {
        healthBeacon?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        healthBeacon?.setBackgroundResource(if (ok) R.drawable.beacon_green else R.drawable.beacon_red)
    }

    private fun clearAllAppData() {
        val ctx = requireContext()
        // Clear schedules list and active selection
        val schedules = SchedulesStorage(ctx)
        schedules.clear()
        schedules.clearActive()
        // Clear cached events
        EventRepository.clearAll(ctx)
        // Clear subject links
        SubjectLinksStorage.clearAll(ctx)
        // Clear hidden subjects
        HiddenSubjectsStorage.clearAll(ctx)
        // Clear app preferences (theme, defaults, etc.)
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().clear().apply()
    }
}
