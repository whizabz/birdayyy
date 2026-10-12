package app.birdayyy.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.birdayyy.MainActivity
import app.birdayyy.R
import app.birdayyy.data.ContactsRepository
import app.birdayyy.data.ICloudClient
import app.birdayyy.data.ListMode
import app.birdayyy.data.ReminderStyle
import app.birdayyy.data.SettingsStore
import app.birdayyy.data.UpcomingEvent
import app.birdayyy.data.buildPersons
import app.birdayyy.data.subtitle
import app.birdayyy.data.upcoming
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Channels {
    const val NORMAL = "bd_normal"
    const val PRIORITY = "bd_priority"
    const val SILENT = "bd_silent"

    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(NORMAL, "Daily digest", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Birthdays and anniversaries at your reminder time" })
        nm.createNotificationChannel(NotificationChannel(PRIORITY, "Priority reminders", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Pops up on screen for people set to Notify on priority" })
        nm.createNotificationChannel(NotificationChannel(SILENT, "Silent reminders", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "No sound or vibration"; setSound(null, null); enableVibration(false) })
    }
}

object ReminderScheduler {
    const val ACTION_DIGEST = "app.birdayyy.DIGEST"
    const val ACTION_SNOOZE = "app.birdayyy.SNOOZE"
    private const val DAILY = 1
    private const val LATER = 2

    private fun pending(ctx: Context, code: Int, action: String, extraAt: Long = 0L): PendingIntent =
        PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, ReminderReceiver::class.java).setAction(action).putExtra("at", extraAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun canScheduleExact(ctx: Context) =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun scheduleDaily(ctx: Context) {
        val s = SettingsStore(ctx).value
        var next = LocalDateTime.now().withHour(s.hour).withMinute(s.minute).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        set(ctx, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending(ctx, DAILY, ACTION_DIGEST))
    }

    /** Re-shows today's digest at the given epoch millis. */
    fun remindAt(ctx: Context, at: Long) = set(ctx, at, pending(ctx, LATER, ACTION_SNOOZE, at))

    fun snoozePending(ctx: Context, at: Long): PendingIntent = pending(ctx, 3, ACTION_SNOOZE + ".set", at)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_DIGEST -> { DigestNotifier.post(ctx); ReminderScheduler.scheduleDaily(ctx) }
                    ReminderScheduler.ACTION_SNOOZE -> DigestNotifier.post(ctx)
                    ReminderScheduler.ACTION_SNOOZE + ".set" -> {
                        ReminderScheduler.remindAt(ctx, intent.getLongExtra("at", System.currentTimeMillis() + 3_600_000))
                        NotificationManagerCompat.from(ctx).cancel(DigestNotifier.ALERTING_ID)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("Birdayyy", "digest failed", e)
            } finally { result.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Channels.create(ctx)
        ReminderScheduler.scheduleDaily(ctx)
    }
}

object DigestNotifier {
    const val ALERTING_ID = 1001
    const val SILENT_ID = 1002

    fun todaysEvents(ctx: Context): List<UpcomingEvent> {
        val store = SettingsStore(ctx)
        val s = store.value.copy(mode = ListMode.Combined)
        val repo = ContactsRepository(ctx)
        val persons = buildPersons(repo.loadRows(), ICloudClient(ctx).cached(), s)
        return upcoming(persons, LocalDate.now()).filter {
            it.daysUntil == 0 && s.kindEnabled(it.date.kind) && s.styleFor(it.person.id, it.date.kind) != ReminderStyle.Never
        }
    }

    fun post(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        Channels.create(ctx)
        val s = SettingsStore(ctx).value
        val events = todaysEvents(ctx)
        android.util.Log.i("Birdayyy", "digest events=${events.size}")
        val nm = NotificationManagerCompat.from(ctx)
        val alerting = events.filter { s.styleFor(it.person.id, it.date.kind) != ReminderStyle.Silent }
        val silent = events.filter { s.styleFor(it.person.id, it.date.kind) == ReminderStyle.Silent }
        if (alerting.isEmpty()) nm.cancel(ALERTING_ID) else nm.notify(ALERTING_ID, build(ctx, alerting, silent = false, priority = alerting.any { s.styleFor(it.person.id, it.date.kind) == ReminderStyle.Priority }))
        if (silent.isEmpty()) nm.cancel(SILENT_ID) else nm.notify(SILENT_ID, build(ctx, silent, silent = true, priority = false))
    }

    private fun open(ctx: Context, code: Int, personId: String?, dateKey: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (personId != null) i.putExtra(MainActivity.EXTRA_WISH_PERSON, personId).putExtra(MainActivity.EXTRA_WISH_DATE, dateKey)
        else i.putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun build(ctx: Context, events: List<UpcomingEvent>, silent: Boolean, priority: Boolean) =
        NotificationCompat.Builder(ctx, if (silent) Channels.SILENT else if (priority) Channels.PRIORITY else Channels.NORMAL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentTitle(if (events.size == 1) events[0].person.name else "${events.size} celebrations today")
            .setContentText(if (events.size == 1) events[0].subtitle() else events.joinToString { it.person.firstName })
            .setStyle(NotificationCompat.InboxStyle().also { st -> events.forEach { st.addLine("${it.person.name} · ${it.subtitle()}") } })
            .setContentIntent(open(ctx, 10, null, null))
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else if (priority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (!silent) {
                    events.take(2).forEachIndexed { i, e -> addAction(0, "Wish ${e.person.firstName}", open(ctx, 20 + i, e.person.id, e.date.key)) }
                    val noon = LocalDateTime.now().withHour(12).withMinute(0).withSecond(0).withNano(0)
                    val at = (if (noon.isAfter(LocalDateTime.now())) noon else LocalDateTime.now().plusHours(1))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    addAction(0, if (noon.isAfter(LocalDateTime.now())) "Remind me at noon" else "Remind me in 1 hour", ReminderScheduler.snoozePending(ctx, at))
                }
            }.build()
}
