package com.mindenit.schedule.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mindenit.schedule.MainActivity
import com.mindenit.schedule.R
import com.mindenit.schedule.data.Event
import com.mindenit.schedule.data.SubjectLinksStorage
import java.time.format.DateTimeFormatter

object NotificationHelper {
    const val CHANNEL_ID = "events_now"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.channel_events_now_name)
            val desc = context.getString(R.string.channel_events_now_desc)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    data class JoinLink(val url: String, val brandIcon: Int)

    private fun findPrimaryJoinLink(context: Context, event: Event): JoinLink? {
        val storage = SubjectLinksStorage(context)
        val links = storage.getLinks(event.subject.id)
        val primary = links.firstOrNull { it.isPrimaryVideo }
            ?: links.firstOrNull { it.isVideoConference() }
        primary ?: return null
        val icon = when {
            primary.url.lowercase().contains("meet.google") -> R.drawable.ic_meet_brand_24
            primary.url.lowercase().contains("zoom") -> R.drawable.ic_zoom_brand_24
            else -> R.drawable.ic_zoom_24 // fallback generic
        }
        return JoinLink(primary.url, icon)
    }

    fun notifyForEvent(context: Context, event: Event, wave: Int, notificationId: Int? = null) {
        ensureChannel(context)

        // Content intent: open app
        val intent = Intent(context, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            context,
            (event.id.toInt() xor 0xACED) + wave,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val join = findPrimaryJoinLink(context, event)
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val title = context.getString(R.string.notif_soon_title_format, event.subject.title)
        val text = context.getString(R.string.notif_now_text_format, event.start.format(formatter), event.end.format(formatter))

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (join != null) {
            val intentJoin = Intent(Intent.ACTION_VIEW, Uri.parse(join.url))
            val pendingJoin = PendingIntent.getActivity(
                context,
                (event.id.toInt() xor 0xBEEF) + wave,
                intentJoin,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val action = NotificationCompat.Action.Builder(
                join.brandIcon,
                context.getString(R.string.action_join),
                pendingJoin
            ).build()
            builder.addAction(action)
        }

        val id = notificationId ?: ((event.id.hashCode() and 0x7FFFFFFF) + wave * 100000)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
