package com.mindenit.schedule.ui.settings

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.mindenit.schedule.R
import com.mindenit.schedule.data.LogStorage
import android.content.Context

class LogViewerFragment : Fragment() {
    private var textView: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_logs, container, false)
        textView = root.findViewById(R.id.logs_text)
        textView?.movementMethod = ScrollingMovementMethod()

        root.findViewById<MaterialButton>(R.id.btn_copy)?.setOnClickListener { copyLogs() }
        root.findViewById<MaterialButton>(R.id.btn_clear)?.setOnClickListener { clearLogs() }
        root.findViewById<MaterialButton>(R.id.btn_refresh)?.setOnClickListener { loadLogs() }

        return root
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun loadLogs() {
        val ctx = context ?: return
        val text = LogStorage(ctx).getText()
        textView?.text = if (text.isBlank()) getString(R.string.no_logs) else text
    }

    private fun copyLogs() {
        val ctx = context ?: return
        val text = textView?.text?.toString().orEmpty()
        if (text.isBlank()) return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("logs", text)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(ctx, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun clearLogs() {
        val ctx = context ?: return
        LogStorage(ctx).clear()
        loadLogs()
    }
}
