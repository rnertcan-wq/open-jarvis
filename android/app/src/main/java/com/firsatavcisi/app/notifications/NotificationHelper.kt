package com.firsatavcisi.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.firsatavcisi.app.MainActivity
import com.firsatavcisi.app.alert.AlarmActivity
import com.firsatavcisi.app.alert.DismissAlarmReceiver
import com.firsatavcisi.app.data.Deal

object NotificationHelper {
    const val CHANNEL_NORMAL = "firsat_normal"
    const val CHANNEL_URGENT = "firsat_urgent"
    const val CHANNEL_SERVICE = "firsat_service"
    // Yeni kanal kimligi bilincli: Android mevcut kanalin onemini yukseltmeye izin vermez.
    const val CHANNEL_FULLSCREEN_ALARM = "firsat_fullscreen_alarm_v2"

    const val ALARM_NOTIFICATION_ID = 7101
    const val ACTION_ALARM_STOPPED = "com.firsatavcisi.app.action.ALARM_STOPPED"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val normal = NotificationChannel(
            CHANNEL_NORMAL,
            "Firsat bildirimleri",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Yakalanan normal firsatlar" }

        val urgent = NotificationChannel(
            CHANNEL_URGENT,
            "Acil firsat alarmlari",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Yuksek indirimli firsatlarda acil uyari"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 250, 700, 250, 900)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "Aktif firsat alarmi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Kapatilana kadar calisan firsat alarmi"
            setSound(null, null)
            enableVibration(false)
        }

        val fullScreen = NotificationChannel(
            CHANNEL_FULLSCREEN_ALARM,
            "Tam ekran firsat alarmi",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Ekran kilitliyken firsat alarm ekranini acar"
            setSound(null, null) // Sesi on plan servisi calar.
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        manager.createNotificationChannel(normal)
        manager.createNotificationChannel(urgent)
        manager.createNotificationChannel(service)
        manager.createNotificationChannel(fullScreen)
    }

    fun showNormal(context: Context, deal: Deal) {
        createChannels(context)
        val openIntent = PendingIntent.getActivity(
            context,
            deal.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Yeni firsat: ${deal.productName}")
            .setContentText(dealSummaryLine(deal))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(deal)))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(deal.id.hashCode(), notification) }
    }

    /** Tam ekran, arama benzeri bildirim. Kaydirma hareketini activity saglar. */
    fun showAlarm(context: Context, deal: Deal) {
        createChannels(context)
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_ID, deal.id)
            putExtra(AlarmActivity.EXTRA_PRODUCT, deal.productName)
            putExtra(AlarmActivity.EXTRA_PRICE, deal.currentPrice?.let { Deal.formatPrice(it) }.orEmpty())
            putExtra(AlarmActivity.EXTRA_DISCOUNT, deal.discountPercent?.let(::formatPercent).orEmpty())
            putExtra(AlarmActivity.EXTRA_PRIORITY, deal.priority)
            putExtra(AlarmActivity.EXTRA_URL, deal.url.orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreen = PendingIntent.getActivity(
            context,
            ALARM_NOTIFICATION_ID,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = dismissPendingIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_FULLSCREEN_ALARM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (deal.priority == 2) "COK YUKSEK INDIRIM: ${deal.productName}" else "FIRSAT ALARMI: ${deal.productName}")
            .setContentText(dealSummaryLine(deal))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(deal)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Alarmi kapat", dismiss)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notification) }
    }

    fun cancelAlarm(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID) }
        runCatching { NotificationManagerCompat.from(context).cancel(7100) }
    }

    fun broadcastAlarmStopped(context: Context) {
        runCatching {
            context.sendBroadcast(Intent(ACTION_ALARM_STOPPED).setPackage(context.packageName))
        }
    }

    fun serviceNotification(context: Context, deal: Deal, queued: Int = 0) =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Firsat alarmi caliyor${if (queued > 0) " · $queued sirada" else ""}")
            .setContentText(dealSummaryLine(deal))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(deal)))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Alarmi kapat", dismissPendingIntent(context))
            .build()

    private fun dismissPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        7102,
        Intent(context, DismissAlarmReceiver::class.java).setAction(AlertServiceBridge.ACTION_DISMISS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun dealSummaryLine(deal: Deal): String = buildString {
        deal.currentPrice?.let { append(Deal.formatPrice(it)) } ?: append("Fiyat dogrulanamadi")
        deal.discountPercent?.let { append(" · %${formatPercent(it)} indirim") }
        if (deal.source == "ocr") append(" · gorselden okundu")
    }

    private fun summary(deal: Deal): String = buildString {
        append(deal.productName)
        deal.currentPrice?.let { append("\nFiyat: ${Deal.formatPrice(it)}") }
        deal.oldPrice?.let { append("\nOnceki fiyat: ${Deal.formatPrice(it)}") }
        deal.discountPercent?.let { append("\nIndirim: %${formatPercent(it)}") }
        deal.groupName.takeIf { it.isNotBlank() }?.let { append("\nGrup: $it") }
        deal.url?.let { append("\n$it") }
    }

    private fun formatPercent(value: Double): String =
        String.format(java.util.Locale("tr", "TR"), "%.1f", value).removeSuffix(",0")
}

/** Dongusel bagimliligi onlemek icin kucuk kopru. */
internal object AlertServiceBridge {
    const val ACTION_DISMISS = "com.firsatavcisi.app.action.DISMISS"
}
