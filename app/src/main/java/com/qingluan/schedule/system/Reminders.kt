package com.qingluan.schedule.system

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qingluan.schedule.*
import com.qingluan.schedule.data.*
import com.qingluan.schedule.model.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SystemCoordinator {
    private val lock = Mutex()
    const val CHANNEL = "courses"

    private fun ledger(ctx: Context) =
        ctx.getSharedPreferences("alarm_ledger", Context.MODE_PRIVATE)

    private fun token(ctx: Context, key: String, date: String? = null): PendingIntent {
        val intent =
            Intent(ctx, ReminderReceiver::class.java)
                .setData(Uri.parse("qingluan://reminder/${Uri.encode(key)}"))
        if (date != null) intent.putExtra("key", key).putExtra("date", date)
        return PendingIntent.getBroadcast(
            ctx,
            701,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun delivered(ctx: Context): Set<String> {
        val today = LocalDate.now()
        val prefs = ledger(ctx)
        return listOf(today, today.minusDays(1))
            .flatMap { prefs.getStringSet("delivered-$it", emptySet()).orEmpty() }
            .toSet()
    }

    fun markDelivered(ctx: Context, key: String): Boolean =
        synchronized(this) {
            if (key in delivered(ctx)) false
            else {
                val store = ledger(ctx)
                val bucket = "delivered-${LocalDate.now()}"
                store
                    .edit()
                    .putStringSet(bucket, store.getStringSet(bucket, emptySet()).orEmpty() + key)
                    .commit()
                true
            }
        }

    private fun setAlarm(alarm: AlarmManager, time: Long, token: PendingIntent) {
        try {
            if (alarm.canScheduleExactAlarms())
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, token)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, token)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, token)
        }
    }

    suspend fun refresh(ctx: Context, s: Snapshot, p: AppSettings) = lock.withLock {
        val notifications = ctx.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "课程开始前的本地提醒"
            }
        )
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val store = ledger(ctx)
        store.getStringSet("armed", emptySet()).orEmpty().forEach { alarm.cancel(token(ctx, it)) }
        val now = LocalDateTime.now()
        val upcoming = ScheduleEngine.upcoming(s, s.activeTermId, now, 7)
        val valid = upcoming.map { it.key }.toSet()
        val delivered = delivered(ctx)
        val armed = mutableSetOf<String>()
        val enabled =
            p.reminders &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        if (enabled) {
            // Independent alarms avoid losing B when an inexact alarm A arrives late.
            ReminderPlanner.plan(s, p, now, delivered).take(200).forEach { plan ->
                val o = plan.occurrence
                setAlarm(
                    alarm,
                    plan.trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    token(ctx, o.key, o.date.toString()),
                )
                armed += o.key
            }
        }
        val editor = store.edit().putStringSet("armed", armed)
        val oldest = "delivered-${LocalDate.now().minusDays(1)}"
        store.all.keys
            .filter { it.startsWith("delivered-") && it < oldest }
            .forEach { editor.remove(it) }
        editor.apply()
        notifications.activeNotifications
            .filter { it.tag != null && (it.tag !in valid || !p.reminders) }
            .forEach { notifications.cancel(it.tag, it.id) }
        val midnight = LocalDate.now().plusDays(1).atStartOfDay().plusSeconds(3)
        val boundary =
            upcoming
                .flatMap { o ->
                    listOf(
                        o.date.atStartOfDay().plusMinutes(o.startMinute.toLong()),
                        o.date.atStartOfDay().plusMinutes(o.endMinute.toLong()),
                    )
                }
                .filter { it > now.plusSeconds(1) }
                .minOrNull()
        val next = minOf(midnight, boundary?.plusSeconds(2) ?: midnight)
        val daily =
            PendingIntent.getBroadcast(
                ctx,
                702,
                Intent(ctx, RefreshReceiver::class.java).setAction("com.qingluan.schedule.REFRESH"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        if (enabled)
            setAlarm(alarm, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), daily)
        else alarm.cancel(daily)
    }
}

class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ScheduleApplication
        app.applicationScope.launch {
            try {
                app.repository.awaitReady()
                SystemCoordinator.refresh(
                    context,
                    checkNotNull(app.repository.state.value),
                    app.repository.currentSettings(),
                )
            } catch (_: Exception) {
                /* A read failure must not erase user data. */
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as ScheduleApplication
        app.applicationScope.launch {
            try {
                app.repository.awaitReady()
                val s = checkNotNull(app.repository.state.value)
                val p = app.repository.currentSettings()
                val date = intent.getStringExtra("date")?.let(LocalDate::parse)
                val key = intent.getStringExtra("key")
                if (
                    p.reminders &&
                        date != null &&
                        key != null &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val now = LocalDateTime.now()
                    val o =
                        ScheduleEngine.onDate(s, s.activeTermId, date).firstOrNull {
                            it.key == key &&
                                date.atStartOfDay().plusMinutes(it.endMinute.toLong()) > now
                        }
                    if (o != null && SystemCoordinator.markDelivered(context, key)) {
                        val open =
                            PendingIntent.getActivity(
                                context,
                                0,
                                Intent(context, MainActivity::class.java).putExtra("tab", 0),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        val n =
                            NotificationCompat.Builder(context, SystemCoordinator.CHANNEL)
                                .setSmallIcon(R.drawable.ic_notification)
                                .setContentTitle(o.course.name)
                                .setContentText(
                                    "${clock(o.startMinute)}–${clock(o.endMinute)} · ${o.location.ifBlank{"未设置地点"}}"
                                )
                                .setStyle(
                                    NotificationCompat.BigTextStyle()
                                        .bigText(
                                            "${clock(o.startMinute)}–${clock(o.endMinute)}\n${o.location.ifBlank{"未设置地点"}} ${o.teacher}"
                                        )
                                )
                                .setContentIntent(open)
                                .setAutoCancel(true)
                                .setTimeoutAfter(2 * 60 * 60 * 1000L)
                                .build()
                        NotificationManagerCompat.from(context).notify(key, 0, n)
                    }
                }
                SystemCoordinator.refresh(context, s, p)
            } catch (_: Exception) {
                /* Denied permissions never crash the application. */
            } finally {
                pending.finish()
            }
        }
    }
}
