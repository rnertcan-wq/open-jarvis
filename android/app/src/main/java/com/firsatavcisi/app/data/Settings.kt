package com.firsatavcisi.app.data

import android.content.Context

/**
 * 1.2.0: kullanici tarafindan ayarlanabilen tum esikler ve davranislar tek yerde tutulur.
 * Eski surumle uyumluluk icin grup adi DealStore'dan tasinir.
 */
object Settings {
    private const val PREFS = "firsat_avcisi_settings"

    private const val KEY_GROUPS = "groups"
    private const val KEY_MIN_DISCOUNT = "min_discount"
    private const val KEY_CHEAP_PRICE = "cheap_price"
    private const val KEY_CHEAP_DISCOUNT = "cheap_discount"
    private const val KEY_MAX_PRICE = "max_price"
    private const val KEY_CHEAP_WITHOUT_DISCOUNT = "cheap_without_discount"
    private const val KEY_ALARM_SECONDS = "alarm_seconds"
    private const val KEY_FORCE_VOLUME = "force_volume"
    private const val KEY_TTS_ALWAYS = "tts_always"
    private const val KEY_OCR = "ocr_enabled"
    private const val KEY_VIBRATION = "vibration"
    private const val KEY_ALARM_MODE = "alarm_mode"
    private const val KEY_DND_BYPASS = "dnd_bypass"

    /**
     * Alarm cikis modu.
     * Rahatsiz Etme (DND) kapisi ayri bir ayardir: [dndBypass].
     */
    object AlarmMode {
        const val SOUND_AND_VIBRATION = 0
        const val VIBRATION_ONLY = 1
        const val SOUND_ONLY = 2
        const val SILENT = 3

        fun label(value: Int): String = when (value) {
            VIBRATION_ONLY -> "Sadece titreşim"
            SOUND_ONLY -> "Sadece ses"
            SILENT -> "Sessiz (ekran + bildirim)"
            else -> "Ses + titreşim"
        }

        fun description(value: Int): String = when (value) {
            VIBRATION_ONLY -> "Ses çalmaz, telefon titrer ve tam ekran uyarı açılır."
            SOUND_ONLY -> "Alarm sesi çalar, titreşim olmaz."
            SILENT -> "Ne ses ne titreşim. Ekran ve bildirim görünür."
            else -> "Alarm sesi ve titreşim birlikte çalışır."
        }

        val all: List<Int> = listOf(SOUND_AND_VIBRATION, VIBRATION_ONLY, SOUND_ONLY, SILENT)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Virgul veya noktali virgul ile ayrilmis grup adlari.
     * Bos liste = tum WhatsApp sohbetleri izlenir.
     */
    fun groups(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_GROUPS, null)
        if (raw == null) {
            // Eski surumden tasima: tek grup adi DealStore icinde tutuluyordu.
            val legacy = DealStore.groupName(context).trim()
            return if (legacy.isBlank() || legacy.equals("Onual", true)) emptyList() else listOf(legacy)
        }
        return raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun setGroups(context: Context, value: String) {
        val cleaned = value.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        prefs(context).edit().putString(KEY_GROUPS, cleaned).apply()
    }

    /** %70 gibi. Bu oranin uzerindeki indirimler acil alarm sayilir. */
    fun minDiscount(context: Context): Double = prefs(context).getFloat(KEY_MIN_DISCOUNT, 70f).toDouble()

    /** 100 TL gibi. Bu tutarin altindaki fiyatlar "cok ucuz" sayilir. */
    fun cheapPrice(context: Context): Double = prefs(context).getFloat(KEY_CHEAP_PRICE, 100f).toDouble()

    /** %90 gibi. Ucuz fiyatla birlikte bu oran asilirsa en yuksek oncelik. */
    fun cheapDiscount(context: Context): Double = prefs(context).getFloat(KEY_CHEAP_DISCOUNT, 90f).toDouble()

    /** 0 = sinir yok. Bu tutarin uzerindeki fiyatlar alarm caldiramaz, sadece gecmise yazilir. */
    fun maxPrice(context: Context): Double = prefs(context).getFloat(KEY_MAX_PRICE, 0f).toDouble()

    /** Indirim orani yazmayan ama fiyati cok dusuk mesajlarda da alarm calinsin mi. */
    fun cheapWithoutDiscount(context: Context): Boolean = prefs(context).getBoolean(KEY_CHEAP_WITHOUT_DISCOUNT, true)

    /** Alarm en fazla kac saniye calar. 0 = kullanici kapatana kadar. */
    fun alarmSeconds(context: Context): Int = prefs(context).getInt(KEY_ALARM_SECONDS, 90)

    /** Alarm calarken ses seviyesini gecici olarak yukselt. */
    fun forceVolume(context: Context): Boolean = prefs(context).getBoolean(KEY_FORCE_VOLUME, true)

    /** Sesli anons sadece Bluetooth bagliyken mi calinsin. */
    fun ttsAlways(context: Context): Boolean = prefs(context).getBoolean(KEY_TTS_ALWAYS, false)

    /** Bildirimde gorsel varsa cevrimdisi metin tanima ile fiyat aranir. */
    fun ocrEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_OCR, true)

    fun vibration(context: Context): Boolean = prefs(context).getBoolean(KEY_VIBRATION, true)

    /** 0 = ses + titresim, 1 = sadece titresim, 2 = sadece ses, 3 = sessiz. */
    fun alarmMode(context: Context): Int = prefs(context).getInt(KEY_ALARM_MODE, AlarmMode.SOUND_AND_VIBRATION)

    fun setAlarmMode(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_ALARM_MODE, value.coerceIn(0, 3)).apply()
    }

    fun alarmWantsSound(context: Context): Boolean {
        val mode = alarmMode(context)
        return mode == AlarmMode.SOUND_AND_VIBRATION || mode == AlarmMode.SOUND_ONLY
    }

    fun alarmWantsVibration(context: Context): Boolean {
        val mode = alarmMode(context)
        return vibration(context) &&
            (mode == AlarmMode.SOUND_AND_VIBRATION || mode == AlarmMode.VIBRATION_ONLY)
    }

    /** Rahatsiz Etme (DND) acikken alarm yine de calssin mi. Izin gerektirir. */
    fun dndBypass(context: Context): Boolean = prefs(context).getBoolean(KEY_DND_BYPASS, false)

    fun setDndBypass(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DND_BYPASS, value).apply()
    }

    fun rules(context: Context) = DealParser.Rules(
        minDiscount = minDiscount(context),
        cheapPrice = cheapPrice(context),
        cheapDiscount = cheapDiscount(context),
        maxPrice = maxPrice(context),
        cheapWithoutDiscount = cheapWithoutDiscount(context)
    )

    fun setNumber(context: Context, key: String, value: Float) {
        prefs(context).edit().putFloat(key, value).apply()
    }

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun setInt(context: Context, key: String, value: Int) {
        prefs(context).edit().putInt(key, value).apply()
    }

    const val K_MIN_DISCOUNT = KEY_MIN_DISCOUNT
    const val K_CHEAP_PRICE = KEY_CHEAP_PRICE
    const val K_CHEAP_DISCOUNT = KEY_CHEAP_DISCOUNT
    const val K_MAX_PRICE = KEY_MAX_PRICE
    const val K_ALARM_SECONDS = KEY_ALARM_SECONDS
    const val K_FORCE_VOLUME = KEY_FORCE_VOLUME
    const val K_TTS_ALWAYS = KEY_TTS_ALWAYS
    const val K_OCR = KEY_OCR
    const val K_VIBRATION = KEY_VIBRATION
    const val K_ALARM_MODE = KEY_ALARM_MODE
    const val K_DND_BYPASS = KEY_DND_BYPASS
    const val K_CHEAP_WITHOUT_DISCOUNT = KEY_CHEAP_WITHOUT_DISCOUNT
}
