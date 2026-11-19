package com.firsatavcisi.app.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            SleepSchedule.ACTION_SLEEP_START,
            SleepSchedule.ACTION_SLEEP_END,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                if (SleepSchedule.isSleeping(context)) SleepSchedule.silenceActiveAlarm(context)
                SleepSchedule.schedule(context)
            }
        }
    }
}
