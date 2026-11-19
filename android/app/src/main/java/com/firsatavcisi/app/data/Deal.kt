package com.firsatavcisi.app.data

import org.json.JSONObject

/** A locally stored opportunity parsed from a WhatsApp notification. */
data class Deal(
    val id: String,
    val productName: String,
    val currentPrice: Double?,
    val oldPrice: Double?,
    val discountPercent: Double?,
    val url: String?,
    val rawMessage: String,
    val groupName: String,
    val createdAt: Long,
    /** 0 = normal, 1 = urgent, 2 = exceptional. */
    val priority: Int,
    /** "text" veya "ocr" */
    val source: String = "text"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("productName", productName)
        put("currentPrice", currentPrice ?: JSONObject.NULL)
        put("oldPrice", oldPrice ?: JSONObject.NULL)
        put("discountPercent", discountPercent ?: JSONObject.NULL)
        put("url", url ?: JSONObject.NULL)
        put("rawMessage", rawMessage)
        put("groupName", groupName)
        put("createdAt", createdAt)
        put("priority", priority)
        put("source", source)
    }

    /** Alarm calarken ekranda gosterilecek kisa ozet. */
    fun voiceSummary(): String = buildString {
        append(productName)
        currentPrice?.let { append(". Fiyat ${formatPrice(it)}") }
        discountPercent?.let { append(". Yuzde ${it.toInt()} indirim") }
    }

    companion object {
        fun formatPrice(value: Double): String =
            String.format(java.util.Locale("tr", "TR"), "%.2f TL", value).replace(",00 TL", " TL")

        fun fromJson(json: JSONObject): Deal = Deal(
            id = json.optString("id"),
            productName = json.optString("productName", "Yeni fırsat"),
            currentPrice = json.optDouble("currentPrice").takeUnless { json.isNull("currentPrice") || it.isNaN() },
            oldPrice = json.optDouble("oldPrice").takeUnless { json.isNull("oldPrice") || it.isNaN() },
            discountPercent = json.optDouble("discountPercent").takeUnless { json.isNull("discountPercent") || it.isNaN() },
            url = json.optString("url").takeIf { it.isNotBlank() && it != "null" },
            rawMessage = json.optString("rawMessage"),
            groupName = json.optString("groupName", ""),
            createdAt = json.optLong("createdAt"),
            priority = json.optInt("priority"),
            source = json.optString("source", "text")
        )
    }
}
