package com.mindenit.schedule

import android.os.Bundle
import android.os.Build
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.mindenit.schedule.databinding.ActivityMainBinding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.mindenit.schedule.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val repo by lazy { Repository() }
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before view inflation
        applySavedTheme()

        // Enable dynamic (Monet) colors on Android 13+ only; older keep fallback palette
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)

        // Opt into edge-to-edge so we can handle insets ourselves
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set MaterialToolbar as ActionBar for Material 3 NoActionBar theme
        setSupportActionBar(binding.toolbar)

        // Apply status bar inset to toolbar to avoid overlap under system bar
        val toolbarBaseTop = binding.toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, toolbarBaseTop + status.top, v.paddingRight, v.paddingBottom)
            insets
        }

        val navView: BottomNavigationView = binding.navView

        // Preserve XML padding and add system bottom inset (gesture nav) to avoid clipping
        val baseBottom = navView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(navView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottom + bars.bottom)
            insets
        }

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Kick off test requests on startup to verify networking and log responses
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val h = repo.health()
                Log.d(tag, "Health OK: message=${h.message}, uptime=${h.uptime}")
            } catch (t: Throwable) {
                Log.e(tag, "Health request failed: ${t.message}", t)
            }

            try {
                val groups = repo.fetchGroups()
                Log.d(tag, "Fetched groups: ${groups.size}")
            } catch (t: Throwable) {
                Log.e(tag, "Groups request failed: ${t.message}", t)
            }

            try {
                val teachers = repo.fetchTeachers()
                Log.d(tag, "Fetched teachers: ${teachers.size}")
            } catch (t: Throwable) {
                Log.e(tag, "Teachers request failed: ${t.message}", t)
            }

            try {
                val auds = repo.fetchAuditoriums()
                Log.d(tag, "Fetched auditoriums: ${auds.size}")
            } catch (t: Throwable) {
                Log.e(tag, "Auditoriums request failed: ${t.message}", t)
            }
        }
    }

    private fun applySavedTheme() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val mode = sp.getString("pref_theme", "system")
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
