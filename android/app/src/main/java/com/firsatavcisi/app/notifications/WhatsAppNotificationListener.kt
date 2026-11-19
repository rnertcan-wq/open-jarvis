package com.firsatavcisi.app.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.firsatavcisi.app.alert.AlertService
import com.firsatavcisi.app.alert.SleepSchedule
import com.firsatavcisi.app.data.Deal
import com.firsatavcisi.app.data.DealParser
import com.firsatavcisi.app.data.DealStore
import com.firsatavcisi.app.data.Settings
import com.firsatavcisi.app.ocr.DealOcr

/**
 * 1.2.0 degisiklikleri:
 *  - EXTRA_MESSAGES destegi: WhatsApp ayni sohbete birden fazla mesaj dusurdugunde
 *    "3 yeni mesaj" ozeti nedeniyle metin kaybolmuyor, tek tek mesajlar okunuyor.
 *  - Coklu grup: virgulle ayrilmis liste, bos ise tum sohbetler.
 *  - Kalp atisi: son gorulen olay zamani kaydedilir, ana ekranda gosterilir.
 *  - Gorsel firsatlar: metinde fiyat yoksa bildirimdeki gorsel cevrimdisi OCR ile okunur.
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP_PACKAGE && sbn.packageName != WHATSAPP_BUSINESS_PACKAGE) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()

        val message = collectText(extras)
        if (message.isBlank()) return

        val groups = Settings.groups(this)
        val identity = buildList {
            add(conversationTitle)
            add(title)
            add(sbn.tag.orEmpty())
            add(sbn.groupKey.orEmpty())
            add(sbn.notification.shortcutId.orEmpty())
        }
        if (groups.isNotEmpty() && groups.none { group -> identity.any { it.contains(group, ignoreCase = true) } }) return

        DealStore.noteEvent(this, message)

        val rules = Settings.rules(this)
        val groupLabel = conversationTitle.ifBlank { title }.ifBlank { sbn.tag.orEmpty() }
        val deal = DealParser.parse(message, title, groupLabel, rules, "text")
        val hasContent = deal.currentPrice != null || deal.discountPercent != null
        val stored = hasContent && DealStore.addDeal(this, deal)

        if (stored && deal.priority > 0) {
            alert(deal)
        } else if (stored && !SleepSchedule.isSleeping(this)) {
            NotificationHelper.showNormal(this, deal)
        }

        // Metinde fiyat yoksa gorseli dene (indirim gorselleri cogunlukla fiyat icerir).
        if (deal.currentPrice == null && Settings.ocrEnabled(this)) {
            val bitmap = pictureOf(extras) ?: return
            DealOcr.recognize(bitmap) { text ->
                if (text.isBlank()) return@recognize
                val ocrDeal = DealParser.parse(text, title, groupLabel, rules, "ocr")
                if (ocrDeal.currentPrice == null) return@recognize
                if (!DealStore.addDeal(this, ocrDeal)) return@recognize
                if (ocrDeal.priority > 0) alert(ocrDeal) else if (!SleepSchedule.isSleeping(this)) NotificationHelper.showNormal(this, ocrDeal)
            }
        }
    }

    private fun alert(deal: Deal) {
        if (SleepSchedule.isSleeping(this)) return
        runCatching { AlertService.start(this, deal) }
            .onFailure { NotificationHelper.showAlarm(this, deal) }
    }

    /** MessagingStyle mesajlari + klasik alanlar birlestirilir. */
    private fun collectText(extras: Bundle): String {
        val parts = mutableListOf<String>()

        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null) {
            for (item in messages) {
                val bundle = item as? Bundle ?: continue
                val text = bundle.getCharSequence("text")?.toString().orEmpty()
                if (text.isNotBlank()) parts += text
            }
        }

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { parts += it }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            ?.let { parts += it }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { parts += it }

        return parts.distinct().joinToString("\n").take(6000)
    }

    private fun pictureOf(extras: Bundle): Bitmap? {
        val picture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_PICTURE)
        }
        if (picture != null) return picture
        return null
    }

    companion object {
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }
}
