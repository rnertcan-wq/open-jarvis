package com.firsatavcisi.app.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.firsatavcisi.app.notifications.NotificationHelper
import java.util.Calendar

/** Daily local sleep window. Alerts are suppressed while the current local time is inside it. */
object SleepSchedule {
    private const val PREFS = "firsat_avcisi_sleep"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_START = "start"
    private const val KEY_END = "end"

    const val ACTION_SLEEP_START = "com.firsatavcisi.app.action.SLEEP_START"
    const val ACTION_SLEEP_END = "com.firsatavcisi.app.action.SLEEP_END"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun startTime(context: Context): String = prefs(context).getString(KEY_START, "23:00") ?: "23:00"
    fun endTime(context: Context): String = prefs(context).getString(KEY_END, "07:00") ?: "07:00"

    /** Returns false for an invalid equal start/end pair. */
    fun configure(context: Context, enabled: Boolean, start: String, end: String): Boolean {
        if (!isValidTime(start) || !isValidTime(end) || (enabled && start == end)) return false
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_START, start)
            .putString(KEY_END, end)
            .apply()
        schedule(context)
        if (isSleeping(context)) silenceActiveAlarm(context)
        return true
    }

    fun isSleeping(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!enabled(context)) return false
        val start = toMinutes(startTime(context)) ?: return false
        val end = toMinutes(endTime(context)) ?: return false
        if (start == end) return false
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (start < end) minute >= start && minute < end else minute >= start || minute < end
    }

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val startPending = pendingIntent(context, ACTION_SLEEP_START, 7301)
        val endPending = pendingIntent(context, ACTION_SLEEP_END, 7302)
        manager.cancel(startPending)
        manager.cancel(endPending)
        if (!enabled(context)) return

        scheduleBoundary(context, manager, startTime(context), startPending)
        scheduleBoundary(context, manager, endTime(context), endPending)
    }

    private fun scheduleBoundary(context: Context, manager: AlarmManager, value: String, pending: PendingIntent) {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        val now = System.currentTimeMillis()
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
        } else {
            // Inexact fallback still works when Android's special exact-alarm access is not granted.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pending)
        }
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, SleepScheduleReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun toMinutes(value: String): Int? {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun isValidTime(value: String): Boolean = toMinutes(value) != null && value.matches(Regex("\\d{2}:\\d{2}"))

    fun silenceActiveAlarm(context: Context) {
        DealQueue.clear()
        context.stopService(Intent(context, AlertService::class.java))
        NotificationManagerCompat.from(context).cancel(NotificationHelper.ALARM_NOTIFICATION_ID)
        NotificationManagerCompat.from(context).cancel(7100)
        // 1.2.0: uyku modu baslarken tam ekran alarm ekrani da kapanir.
        NotificationHelper.broadcastAlarmStopped(context)
    }
}
