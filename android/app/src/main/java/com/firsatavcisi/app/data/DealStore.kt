package com.firsatavcisi.app.data

import android.content.Context
import org.json.JSONArray

object DealStore {
    private const val PREFS = "firsat_avcisi_local"
    private const val KEY_GROUP = "group_name"
    private const val KEY_HISTORY = "history_json"
    private const val KEY_SEEN = "seen_json"
    private const val KEY_VOICE = "voice_enabled"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_LAST_EVENT_TEXT = "last_event_text"
    private const val KEY_EVENT_COUNT = "event_count"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun groupName(context: Context): String = prefs(context).getString(KEY_GROUP, "") ?: ""

    fun setGroupName(context: Context, value: String) {
        prefs(context).edit().putString(KEY_GROUP, value.trim()).apply()
    }

    fun voiceEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_VOICE, true)

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE, enabled).apply()
    }

    /** 1.2.0: sirali liste. Eski surumde StringSet sirasiz okunuyordu ve yanlis kayit siliniyordu. */
    private fun seenList(context: Context): MutableList<String> {
        val raw = prefs(context).getString(KEY_SEEN, null)
        val list = mutableListOf<String>()
        if (raw != null) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) list += array.getString(i)
            }
        }
        return list
    }

    private fun saveSeen(context: Context, list: List<String>) {
        val array = JSONArray()
        list.takeLast(500).forEach { array.put(it) }
        prefs(context).edit().putString(KEY_SEEN, array.toString()).apply()
    }

    @Synchronized
    fun addDeal(context: Context, deal: Deal): Boolean {
        val seen = seenList(context)
        if (deal.id in seen) return false
        seen += deal.id

        val old = JSONArray(prefs(context).getString(KEY_HISTORY, "[]") ?: "[]")
        val next = JSONArray()
        val start = (old.length() - 99).coerceAtLeast(0)
        for (i in start until old.length()) next.put(old.getJSONObject(i))
        next.put(deal.toJson())

        prefs(context).edit().putString(KEY_HISTORY, next.toString()).apply()
        saveSeen(context, seen)
        return true
    }

    fun history(context: Context): List<Deal> {
        val json = JSONArray(prefs(context).getString(KEY_HISTORY, "[]") ?: "[]")
        return buildList {
            for (i in json.length() - 1 downTo 0) {
                val deal = runCatching { Deal.fromJson(json.getJSONObject(i)) }.getOrNull()
                if (deal != null) add(deal)
            }
        }
    }

    fun lastDeal(context: Context): Deal? = history(context).firstOrNull()

    fun clearHistory(context: Context) {
        prefs(context).edit().remove(KEY_HISTORY).remove(KEY_SEEN).apply()
    }

    // ---- 1.2.0: dinleyicinin yasadigini gosteren kalp atisi ----

    fun noteEvent(context: Context, text: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
            .putString(KEY_LAST_EVENT_TEXT, text.take(120))
            .putInt(KEY_EVENT_COUNT, eventCount(context) + 1)
            .apply()
    }

    fun lastEventAt(context: Context): Long = prefs(context).getLong(KEY_LAST_EVENT_AT, 0L)

    fun lastEventText(context: Context): String = prefs(context).getString(KEY_LAST_EVENT_TEXT, "") ?: ""

    fun eventCount(context: Context): Int = prefs(context).getInt(KEY_EVENT_COUNT, 0)
}
