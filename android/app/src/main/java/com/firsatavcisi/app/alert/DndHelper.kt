package com.firsatavcisi.app.alert

import android.app.NotificationManager
import android.content.Context

/**
 * 1.2.1: Rahatsiz Etme (DND) destegi.
 *
 * DND acikken Android varsayilan olarak alarm sesini ve titresimi kisitlar.
 * Kullanici "Rahatsiz Etme erisimi" verdiyse alarm suresince filtre gecici olarak
 * ALL'a alinir ve alarm bitince onceki haline dondurulur.
 */
object DndHelper {

    fun isAccessGranted(context: Context): Boolean =
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)

    /** Telefon su anda Rahatsiz Etme modunda mi (tam sessiz dahil). */
    fun isActive(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrDefault(false)

    /** Gecici olarak DND'yi kaldirir ve geri donus icin onceki filtreyi dondurur. */
    fun suspend(context: Context): Int {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return -1
        if (!manager.isNotificationPolicyAccessGranted) return -1
        val previous = manager.currentInterruptionFilter
        if (previous == NotificationManager.INTERRUPTION_FILTER_ALL) return -1
        runCatching { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        return previous
    }

    fun restore(context: Context, previousFilter: Int) {
        if (previousFilter < 0) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.isNotificationPolicyAccessGranted) {
                manager.setInterruptionFilter(previousFilter)
            }
        }
    }
}
