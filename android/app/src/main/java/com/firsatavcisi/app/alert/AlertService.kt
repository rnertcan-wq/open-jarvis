package com.firsatavcisi.app.alert

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.firsatavcisi.app.data.Deal
import com.firsatavcisi.app.data.DealStore
import com.firsatavcisi.app.data.Settings
import com.firsatavcisi.app.notifications.NotificationHelper
import java.util.Locale

/**
 * 1.2.0:
 *  - Sira: gelen firsatlar kuyruga alinir, biri kapatilinca sonraki calar.
 *  - Ses garantisi: STREAM_ALARM seviyesi kontrol edilir ve gecici olarak yukseltilir.
 *  - Otomatik susma: varsayilan 90 saniye sonra kendiliginden durur.
 */
class AlertService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private var currentDeal: Deal? = null
    private var previousVolume = -1
    private var volumeChanged = false
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopCurrentAlarm(next = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createChannels(this)

        if (intent?.action == ACTION_DISMISS) {
            stopCurrentAlarm(next = true)
            return START_NOT_STICKY
        }

        val incoming = intent?.takeIf { it.extras != null }?.let { readDeal(it) }
        if (incoming != null) {
            DealQueue.add(incoming)
        }

        if (currentDeal != null) {
            // Zaten caliyor: yeni firsat sirada bekler, ekran/on bildirim guncellenir.
            showForeground(currentDeal!!)
            return START_NOT_STICKY
        }

        if (SleepSchedule.isSleeping(this)) {
            DealQueue.clear()
            stopSelf()
            return START_NOT_STICKY
        }

        playNext()
        return START_NOT_STICKY
    }

    private fun readDeal(intent: Intent): Deal = Deal(
        id = intent.getStringExtra(EXTRA_ID).orEmpty().ifBlank { "alert-${System.currentTimeMillis()}" },
        productName = intent.getStringExtra(EXTRA_PRODUCT) ?: "Yeni firsat",
        currentPrice = intent.getStringExtra(EXTRA_PRICE)?.toDoubleOrNull(),
        oldPrice = null,
        discountPercent = intent.getStringExtra(EXTRA_DISCOUNT)?.toDoubleOrNull(),
        url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() },
        rawMessage = "",
        groupName = intent.getStringExtra(EXTRA_GROUP).orEmpty(),
        createdAt = System.currentTimeMillis(),
        priority = intent.getIntExtra(EXTRA_PRIORITY, 1),
        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { "text" }
    )

    private fun playNext() {
        val deal = DealQueue.next() ?: run {
            finishService()
            return
        }
        currentDeal = deal

        showForeground(deal)
        NotificationHelper.showAlarm(this, deal)

        boostVolume()
        startRinging()
        if (Settings.vibration(this)) startVibration()
        speakDeal(deal)

        val seconds = Settings.alarmSeconds(this)
        handler.removeCallbacks(stopRunnable)
        if (seconds > 0) handler.postDelayed(stopRunnable, seconds * 1000L)
    }

    private fun showForeground(deal: Deal) {
        val queued = DealQueue.size()
        val notification = NotificationHelper.serviceNotification(this, deal, queued)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopCurrentAlarm(next: Boolean) {
        handler.removeCallbacks(stopRunnable)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { toneGenerator?.stopTone(); toneGenerator?.release() }
        toneGenerator = null
        runCatching { vibrator?.cancel() }
        runCatching { tts?.stop() }
        NotificationHelper.cancelAlarm(this)
        currentDeal = null

        if (next && DealQueue.size() > 0 && !SleepSchedule.isSleeping(this)) {
            playNext()
        } else {
            finishService()
        }
    }

    private fun finishService() {
        currentDeal = null
        NotificationHelper.cancelAlarm(this)
        restoreVolume()
        NotificationHelper.broadcastAlarmStopped(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun boostVolume() {
        if (!Settings.forceVolume(this)) return
        runCatching {
            val am = getSystemService(AudioManager::class.java) ?: return
            if (previousVolume < 0) previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = (max * 0.85f).toInt().coerceAtLeast(1)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < target) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
                volumeChanged = true
            }
        }
    }

    private fun restoreVolume() {
        if (!volumeChanged || previousVolume < 0) return
        runCatching {
            getSystemService(AudioManager::class.java)
                ?.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
        }
        volumeChanged = false
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone = RingtoneManager.getRingtone(this, uri).apply {
                this.audioAttributes = alarmAttributes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.onFailure {
            runCatching {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100).apply {
                    startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 60_000)
                }
            }
        }
    }

    private fun startVibration() {
        runCatching {
            vibrator = getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 700, 250, 700, 250, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun speakDeal(deal: Deal) {
        if (!DealStore.voiceEnabled(this)) return
        if (!Settings.ttsAlways(this) && !isBluetoothAudioConnected()) return
        val text = "Firsatta yeni urun. ${deal.voiceSummary()}. Acmak icin ekrandaki dugmeyi kullan."
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.language = Locale("tr", "TR")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "firsat-alert")
            }
        }
    }

    private fun isBluetoothAudioConnected(): Boolean {
        val audioManager = getSystemService(AudioManager::class.java) ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    override fun onCreate() {
        super.onCreate()
        attach(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        runCatching { ringtone?.stop() }
        runCatching { toneGenerator?.stopTone(); toneGenerator?.release() }
        runCatching { vibrator?.cancel() }
        runCatching { tts?.stop(); tts?.shutdown() }
        restoreVolume()
        detach(this)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 7100
        private const val EXTRA_ID = "id"
        private const val EXTRA_PRODUCT = "product"
        private const val EXTRA_PRICE = "price"
        private const val EXTRA_DISCOUNT = "discount"
        private const val EXTRA_PRIORITY = "priority"
        private const val EXTRA_URL = "url"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_SOURCE = "source"

        fun start(context: Context, deal: Deal) {
            if (SleepSchedule.isSleeping(context)) return
            val intent = Intent(context, AlertService::class.java).apply {
                putExtra(EXTRA_ID, deal.id)
                putExtra(EXTRA_PRODUCT, deal.productName)
                putExtra(EXTRA_PRICE, deal.currentPrice?.toString().orEmpty())
                putExtra(EXTRA_DISCOUNT, deal.discountPercent?.toString().orEmpty())
                putExtra(EXTRA_PRIORITY, deal.priority)
                putExtra(EXTRA_URL, deal.url.orEmpty())
                putExtra(EXTRA_GROUP, deal.groupName)
                putExtra(EXTRA_SOURCE, deal.source)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Kullanicinin "kapat" hareketi: calan alarmi durdurur, sirada varsa sonrakine gecer. */
        fun dismiss(context: Context) {
            val service = running
            if (service != null) {
                service.stopCurrentAlarm(next = true)
            } else {
                DealQueue.clear()
                NotificationHelper.cancelAlarm(context)
                NotificationHelper.broadcastAlarmStopped(context)
            }
        }

        private val running: AlertService?
            get() = instance

        @Volatile
        private var instance: AlertService? = null

        internal fun attach(service: AlertService) {
            instance = service
        }

        internal fun detach(service: AlertService) {
            if (instance === service) instance = null
        }

        fun stop(context: Context) {
            DealQueue.clear()
            context.stopService(Intent(context, AlertService::class.java))
            NotificationHelper.cancelAlarm(context)
            NotificationHelper.broadcastAlarmStopped(context)
        }

        const val ACTION_DISMISS = "com.firsatavcisi.app.action.DISMISS"
    }
}
