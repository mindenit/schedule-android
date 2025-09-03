package com.mindenit.schedule.ui.home

import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.ImageViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.content.res.ColorStateList as CSList
import android.app.Dialog
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mindenit.schedule.R
import com.mindenit.schedule.data.SubjectLinksStorage
import com.mindenit.schedule.databinding.BottomsheetEventDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mindenit.schedule.data.HiddenSubjectsStorage
import androidx.core.os.bundleOf

class EventDetailsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomsheetEventDetailsBinding? = null
    private val binding get() = _binding!!

    private var subjectId: Long? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val behavior = dialog.behavior
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        // Ensure navbar is tinted to surface so it doesn't look transparent behind the sheet
        dialog.window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val navColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, 0)
            w.navigationBarColor = navColor
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetEventDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val placeholder = getString(R.string.placeholder_empty)

        fun TextView.setOrPlaceholder(value: String?) {
            text = if (value.isNullOrBlank()) placeholder else value
        }

        binding.title.setOrPlaceholder(args.getString(ARG_TITLE))

        val subtitleText = args.getString(ARG_SUBTITLE)
        if (subtitleText.isNullOrBlank()) {
            binding.subtitle.visibility = View.GONE
        } else {
            binding.subtitle.visibility = View.VISIBLE
            binding.subtitle.text = subtitleText
        }

        // Date and time without icons
        binding.dateText.setOrPlaceholder(args.getString(ARG_DATE))
        binding.timeText.setOrPlaceholder(args.getString(ARG_TIME))

        // Type chip with themed color
        val typeText = args.getString(ARG_TYPE)
        val typeColors = EventColorResolver.colorsForType(requireView(), typeText ?: "")
        binding.typeChip.text = if (typeText.isNullOrBlank()) placeholder else typeText
        binding.typeChip.setTextColor(typeColors.foreground)
        binding.typeChip.chipBackgroundColor = CSList.valueOf(typeColors.background)
        binding.typeChip.isClickable = false
        binding.typeChip.isCheckable = false

        // Header card stroke tinted by type color
        val strokePx = (requireContext().resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1)
        binding.headerCard.strokeColor = typeColors.background
        binding.headerCard.strokeWidth = strokePx

        // Auditorium text without icon
        val audText = args.getString(ARG_AUD)
        binding.auditoriumText.setOrPlaceholder(audText)

        // Chips for teachers (hide card if empty)
        val teacherNames = args.getString(ARG_TEACHERS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.teachersChips.removeAllViews()
        if (teacherNames.isEmpty()) {
            binding.teachersCard.visibility = View.GONE
        } else {
            binding.teachersCard.visibility = View.VISIBLE
            teacherNames.forEach { binding.teachersChips.addView(makeChip(it)) }
        }

        // Chips for groups (hide card if empty)
        val groupNames = args.getString(ARG_GROUPS)
            ?.split(", ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        binding.groupsChips.removeAllViews()
        if (groupNames.isEmpty()) {
            binding.groupsCard.visibility = View.GONE
        } else {
            binding.groupsCard.visibility = View.VISIBLE
            groupNames.forEach { binding.groupsChips.addView(makeChip(it)) }
        }

        // Subject links
        subjectId = if (args.containsKey(ARG_SUBJECT_ID)) args.getLong(ARG_SUBJECT_ID) else null
        if (subjectId == null) {
            binding.linksCard.visibility = View.GONE
        } else {
            binding.linksCard.visibility = View.VISIBLE
            binding.addLinkButton.setOnClickListener { showLinkDialog(null) }
            renderLinks()
        }

        // Hide/unhide subject button
        val sid = subjectId
        val btn = binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_toggle_hide_subject)
        if (sid == null) {
            btn.visibility = View.GONE
        } else {
            val storage = HiddenSubjectsStorage(requireContext())
            fun updateBtn() {
                val hidden = storage.isHidden(sid)
                btn.text = getString(if (hidden) R.string.unhide_subject else R.string.hide_subject)
            }
            updateBtn()
            btn.setOnClickListener {
                val hidden = storage.isHidden(sid)
                if (hidden) {
                    storage.remove(sid)
                } else {
                    storage.add(sid)
                    // Inform user how to restore the subject later
                    Toast.makeText(requireContext(), getString(R.string.subject_hidden_hint), Toast.LENGTH_LONG).show()
                }
                // notify parent to refresh calendar
                parentFragmentManager.setFragmentResult("hidden_subjects_changed", bundleOf())
                updateBtn()
                dismiss()
            }
            btn.visibility = View.VISIBLE
        }
    }

    private fun renderLinks() {
        val sid = subjectId ?: return
        val storage = SubjectLinksStorage(requireContext())
        val links = storage.getLinks(sid)
            .sortedWith(compareByDescending<com.mindenit.schedule.data.SubjectLinksStorage.SubjectLink> { it.isVideoConference() }
                .thenByDescending { it.isPrimaryVideo }
                .thenBy { it.title.ifBlank { it.url } })

        binding.linksContainer.removeAllViews()
        binding.linksEmpty.visibility = if (links.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())
        links.forEach { link ->
            val item = inflater.inflate(R.layout.item_subject_link, binding.linksContainer, false) as ViewGroup
            val icon = item.findViewById<ImageView>(R.id.icon)
            val title = item.findViewById<TextView>(R.id.title)
            val urlView = item.findViewById<TextView>(R.id.url)
            val badgeVideo = item.findViewById<TextView>(R.id.badge_video)
            val badgePrimary = item.findViewById<TextView>(R.id.badge_primary)
            val menu = item.findViewById<ImageButton>(R.id.menu)

            val isMeet = link.url.contains("meet.google", ignoreCase = true)
            val isZoom = link.url.contains("zoom", ignoreCase = true)

            // Icon: use brand icons without tint; globe for others
            if (isMeet) {
                icon.setImageResource(R.drawable.ic_meet_brand_24)
                ImageViewCompat.setImageTintList(icon, null)
            } else if (isZoom) {
                icon.setImageResource(R.drawable.ic_zoom_brand_24)
                ImageViewCompat.setImageTintList(icon, null)
            } else {
                icon.setImageResource(R.drawable.ic_web_24)
                ImageViewCompat.setImageTintList(icon, null)
            }

            // Auto-title for meet/zoom if title empty
            val autoTitle = when {
                link.title.isNotBlank() -> link.title
                isMeet -> getString(R.string.brand_google_meet)
                isZoom -> getString(R.string.brand_zoom)
                else -> Uri.parse(link.url).host ?: link.url
            }
            title.text = autoTitle
            urlView.text = link.url

            badgeVideo.visibility = if (link.isVideoConference()) View.VISIBLE else View.GONE
            badgePrimary.visibility = if (link.isPrimaryVideo) View.VISIBLE else View.GONE

            // Open link on click
            item.setOnClickListener {
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(link.url)))
                } catch (_: Throwable) {
                    Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                }
            }

            // Popup menu
            menu.setOnClickListener { v ->
                val pm = PopupMenu(v.context, v)
                pm.menu.add(0, 1, 0, R.string.edit_link)
                pm.menu.add(0, 2, 1, R.string.delete_link)
                if (link.isVideoConference() && !link.isPrimaryVideo) {
                    pm.menu.add(0, 3, 2, R.string.set_primary)
                }
                pm.setOnMenuItemClickListener { mi ->
                    when (mi.itemId) {
                        1 -> { showLinkDialog(link); true }
                        2 -> { confirmDelete(link); true }
                        3 -> { setPrimary(link); true }
                        else -> false
                    }
                }
                pm.show()
            }

            binding.linksContainer.addView(item)
        }
    }

    private fun showLinkDialog(existing: SubjectLinksStorage.SubjectLink?) {
        val ctx = requireContext()
        val content = LayoutInflater.from(ctx).inflate(R.layout.popup_link_edit, null)
        val tilTitle = content.findViewById<TextInputLayout>(R.id.til_title)
        val etTitle = content.findViewById<TextInputEditText>(R.id.et_title)
        val tilUrl = content.findViewById<TextInputLayout>(R.id.til_url)
        val etUrl = content.findViewById<TextInputEditText>(R.id.et_url)
        val btnCancel = content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnSave = content.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)

        etTitle.setText(existing?.title ?: "")
        etUrl.setText(existing?.url ?: "")

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(content)
            .create()

        // Avoid navbar see-through on large screens
        dialog.setOnShowListener {
            dialog.window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, true)
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                val navColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, 0)
                w.navigationBarColor = navColor
            }
        }

        // Apply system bottom inset as padding to the content (safety for devices with gesture nav)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            var url = etUrl.text?.toString()?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            if (!isValidUrl(url)) {
                tilUrl.error = getString(R.string.invalid_url)
                return@setOnClickListener
            } else {
                tilUrl.error = null
            }
            val sid = subjectId ?: return@setOnClickListener
            val storage = SubjectLinksStorage(ctx)
            val newLink = SubjectLinksStorage.SubjectLink(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                subjectId = sid,
                title = title,
                url = url,
                isPrimaryVideo = existing?.isPrimaryVideo == true
            )
            storage.upsert(newLink)
            dialog.dismiss()
            renderLinks()
        }

        dialog.show()
    }

    private fun confirmDelete(link: SubjectLinksStorage.SubjectLink) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage(R.string.delete_link_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                val sid = subjectId ?: return@setPositiveButton
                SubjectLinksStorage(requireContext()).delete(sid, link.id)
                renderLinks()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setPrimary(link: SubjectLinksStorage.SubjectLink) {
        val sid = subjectId ?: return
        SubjectLinksStorage(requireContext()).setPrimary(sid, link.id)
        renderLinks()
    }

    private fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val u = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        return Patterns.WEB_URL.matcher(u).matches()
    }

    private fun makeChip(text: String): Chip = Chip(requireContext()).apply {
        this.text = text
        isClickable = false
        isCheckable = false
        isCloseIconVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_TYPE = "type"
        private const val ARG_AUD = "aud"
        private const val ARG_TEACHERS = "teachers"
        private const val ARG_GROUPS = "groups"
        private const val ARG_DATE = "date"
        private const val ARG_TIME = "time"
        private const val ARG_TEACHER_IDS = "teacher_ids"
        private const val ARG_GROUP_IDS = "group_ids"
        private const val ARG_SUBJECT_ID = "subject_id"

        fun from(event: com.mindenit.schedule.data.Event): EventDetailsBottomSheet {
            // Use Ukrainian locale for date/time formatting (full weekday and month names)
            val uk = Locale.forLanguageTag("uk")
            val dfDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", uk)
            val dfTime = DateTimeFormatter.ofPattern("HH:mm", uk)
            val sameDay = event.start.toLocalDate() == event.end.toLocalDate()
            val date = if (sameDay) {
                event.start.format(dfDate)
            } else {
                "${event.start.format(dfDate)} — ${event.end.format(dfDate)}"
            }
            val time = if (sameDay) {
                "${event.start.format(dfTime)}–${event.end.format(dfTime)}"
            } else {
                "${event.start.format(dfTime)} — ${event.end.format(dfTime)}"
            }
            val teachers = event.teachers.joinToString { it.shortName }
            val groups = event.groups.joinToString { it.name }
            return EventDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, event.subject.title)
                    putString(ARG_SUBTITLE, event.subject.brief)
                    putString(ARG_TYPE, event.type)
                    putString(ARG_AUD, event.auditorium.name)
                    putString(ARG_TEACHERS, teachers)
                    putString(ARG_GROUPS, groups)
                    putString(ARG_DATE, date)
                    putString(ARG_TIME, time)
                    putLong(ARG_SUBJECT_ID, event.subject.id)
                }
            }
        }
    }
}
