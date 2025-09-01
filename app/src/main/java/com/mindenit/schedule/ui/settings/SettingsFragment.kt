package com.mindenit.schedule.ui.settings

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

class SettingsFragment : Fragment() {
    private var healthContainer: View? = null
    private var healthBeacon: View? = null
    private var healthStatus: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        healthContainer = root.findViewById(R.id.healthContainer)
        healthBeacon = root.findViewById(R.id.healthBeacon)
        healthStatus = root.findViewById(R.id.healthStatus)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkHealth()
        healthContainer?.setOnClickListener { checkHealth() }
    }

    private fun checkHealth() {
        healthStatus?.text = "Checking…"
        setBeacon(false, false)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val ok = try {
                val resp = ApiClient.api.health()
                resp.code() in 200..299
            } catch (_: Throwable) { false }
            withContext(Dispatchers.Main) {
                healthStatus?.text = if (ok) "API: OK" else "API: DOWN"
                setBeacon(true, ok)
            }
        }
    }

    private fun setBeacon(visible: Boolean, ok: Boolean) {
        healthBeacon?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        healthBeacon?.setBackgroundResource(if (ok) R.drawable.beacon_green else R.drawable.beacon_red)
    }
}
