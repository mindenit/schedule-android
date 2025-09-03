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
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mindenit.schedule.ui.notifications.NotificationsScheduler
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mindenit.schedule.data.EventRepository
import java.time.YearMonth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Offload scheduling to background thread to avoid blocking UI
        lifecycleScope.launch(Dispatchers.Default) {
            NotificationsScheduler.scheduleForUpcoming(this@MainActivity)
        }
    }

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
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Notifications: request permission on 13+ and schedule workers after app start
        handleNotificationsSetup()

        // On each app launch: clear old cached schedules and prefetch current month
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                EventRepository.clearAll(this@MainActivity)
                EventRepository.ensureMonthCached(this@MainActivity, YearMonth.now())
            } catch (_: Throwable) { }
        }
    }

    private fun handleNotificationsSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Offload scheduling to background thread
                lifecycleScope.launch(Dispatchers.Default) {
                    NotificationsScheduler.scheduleForUpcoming(this@MainActivity)
                }
            }
        } else {
            lifecycleScope.launch(Dispatchers.Default) {
                NotificationsScheduler.scheduleForUpcoming(this@MainActivity)
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
