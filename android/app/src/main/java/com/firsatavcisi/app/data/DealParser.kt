package com.firsatavcisi.app.data

import java.security.MessageDigest
import java.util.Locale

/**
 * 1.2.0 fiyat/indirim ayristirici.
 *
 * Onceki surumdeki hatalar:
 *  - "₺28,00" bicimi hic yakalanmiyordu (fiyat sembolu sayidan once).
 *  - "3.000 TL uzeri kargo bedava", "1 TL kargo" gibi metinler urun fiyati saniliyordu.
 *  - Eski fiyat ile guncel fiyat karisabiliyordu.
 */
object DealParser {

    data class Rules(
        val minDiscount: Double = 70.0,
        val cheapPrice: Double = 100.0,
        val cheapDiscount: Double = 90.0,
        val maxPrice: Double = 0.0,
        val cheapWithoutDiscount: Boolean = true
    )

    private val urlRegex = Regex(
        """https?://[^\s<>\[\]()]+|(?:www\.)?onu\.al/[^\s<>\[\]()]+""",
        RegexOption.IGNORE_CASE
    )

    private val number = """([0-9]{1,3}(?:[.\s][0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)"""

    /** Sayidan sonra gelen TL / ₺ / TRY */
    private val suffixPriceRegex = Regex("""(?<![\d.,])$number\s*(?:₺|TL\.?|TRY)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    /** Sayidan once gelen ₺ / TL */
    private val prefixPriceRegex = Regex("""(?:₺|TL)\s*$number(?![\d])""", RegexOption.IGNORE_CASE)

    /** "28 bin TL" / "28k TL" */
    private val binPriceRegex = Regex("""(?<![\d])$number\s*(?:bin|k)\s*(?:₺|TL|TRY)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    private val discountRegex = Regex(
        """(?:%\s*([0-9]{1,3}(?:[.,][0-9]+)?)|([0-9]{1,3}(?:[.,][0-9]+)?)\s*%|y[üu]zde\s*([0-9]{1,3}(?:[.,][0-9]+)?))""",
        RegexOption.IGNORE_CASE
    )

    /** Fiyat olmayan tutarlar: kargo, taksit, puan, alt limit vb. */
    private val ignoredContext = listOf(
        "kargo", "teslimat", "gönderi", "gonderi", "kapıda", "kapida",
        "üzeri", "uzeri", "ve üzeri", "ve uzeri", "alt limit", "altlimit",
        "taksit", "puan", "kupon", "çek", "cek", "hediye çeki", "hediye ceki",
        "fark", "iade", "indirim kuponu"
    )

    /** Bu ifadelerden sonra gelen tutar eski (liste) fiyattir. */
    private val oldPriceContext = listOf(
        "yerine", "önce", "once", "eski", "normal", "piyasa", "liste", "yerıne"
    )

    data class PriceHit(val value: Double, val start: Int, val end: Int)

    fun parse(
        message: String,
        title: String,
        conversationTitle: String,
        rules: Rules = Rules(),
        source: String = "text"
    ): Deal {
        val text = normalize(message)
        val urls = urlRegex.findAll(text).map { cleanUrl(it.value) }.filter { it.isNotBlank() }.toList()

        val hits = priceHits(text)
        val currentCandidates = hits.filterNot { isOldPriceContext(text, it.start) }.map { it.value }
        val oldFromHits = hits.filter { isOldPriceContext(text, it.start) }.map { it.value }

        val currentPrice = currentCandidates.minOrNull() ?: hits.minOfOrNull { it.value }
        val oldPrice = (oldFromHits + hits.map { it.value })
            .filter { it != currentPrice }
            .maxOrNull()
            ?.takeIf { currentPrice != null && it >= currentPrice * 1.05 }

        val explicitDiscount = discountRegex.find(text)?.let { match ->
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            raw?.replace(',', '.')?.toDoubleOrNull()
        }?.coerceIn(0.0, 100.0)

        val calculatedDiscount = if (oldPrice != null && currentPrice != null && oldPrice > 0.0) {
            ((oldPrice - currentPrice) / oldPrice * 100.0).coerceIn(0.0, 100.0)
        } else null

        val discount = explicitDiscount ?: calculatedDiscount

        val priority = when {
            currentPrice != null && rules.maxPrice > 0 && currentPrice > rules.maxPrice -> 0
            currentPrice != null && currentPrice <= rules.cheapPrice && discount != null && discount >= rules.cheapDiscount -> 2
            discount != null && discount >= rules.minDiscount -> 1
            currentPrice != null && currentPrice <= rules.cheapPrice && discount == null && rules.cheapWithoutDiscount -> 1
            else -> 0
        }

        val productName = buildProductName(text, title, conversationTitle)
        val stableKey = (urls.firstOrNull() ?: "${productName.lowercase(Locale.ROOT)}|${currentPrice ?: "?"}|${discount ?: "?"}") +
            "|" + (System.currentTimeMillis() / 1_800_000L) // ayni firsat 30 dk icinde tekrar alarm calmasin
        val id = sha256(urls.firstOrNull() ?: stableKey).take(32)

        return Deal(
            id = id,
            productName = productName,
            currentPrice = currentPrice,
            oldPrice = oldPrice,
            discountPercent = discount,
            url = urls.firstOrNull(),
            rawMessage = message.take(2000),
            groupName = conversationTitle.ifBlank { title },
            createdAt = System.currentTimeMillis(),
            priority = priority,
            source = source
        )
    }

    private fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace('\u2009', ' ')
        .replace("\r", " ")

    private fun priceHits(text: String): List<PriceHit> {
        val found = mutableListOf<PriceHit>()
        suffixPriceRegex.findAll(text).forEach { m ->
            normalizeTurkishNumber(m.groupValues[1])?.let { found += PriceHit(it, m.range.first, m.range.last) }
        }
        prefixPriceRegex.findAll(text).forEach { m ->
            normalizeTurkishNumber(m.groupValues[1])?.let { found += PriceHit(it, m.range.first, m.range.last) }
        }
        binPriceRegex.findAll(text).forEach { m ->
            normalizeTurkishNumber(m.groupValues[1])?.let { found += PriceHit(it * 1000.0, m.range.first, m.range.last) }
        }
        return found
            .filter { it.value >= 0.01 && it.value <= 100_000_000.0 }
            .filterNot { isIgnoredContext(text, it.start) }
            .distinctBy { it.start to it.value }
            .sortedBy { it.start }
    }

    private fun contextBefore(text: String, index: Int, length: Int = 40): String =
        text.substring((index - length).coerceAtLeast(0), index.coerceAtLeast(0)).lowercase(Locale("tr", "TR"))

    private fun isIgnoredContext(text: String, index: Int): Boolean {
        val before = contextBefore(text, index)
        return ignoredContext.any { before.contains(it) }
    }

    private fun isOldPriceContext(text: String, index: Int): Boolean {
        val before = contextBefore(text, index, 30)
        return oldPriceContext.any { before.contains(it) }
    }

    private fun normalizeTurkishNumber(value: String): Double? {
        val compact = value.replace(" ", "")
        val normalized = if (compact.contains(',')) {
            compact.replace(".", "").replace(',', '.')
        } else {
            // "32.000" binlik ayirici; "28.5" ise ondalik olabilir.
            val dots = compact.count { it == '.' }
            if (dots == 1 && compact.substringAfter('.').length <= 2 && compact.substringBefore('.').length > 2) {
                compact
            } else {
                compact.replace(".", "")
            }
        }
        return normalized.toDoubleOrNull()
    }

    private fun buildProductName(message: String, title: String, conversationTitle: String): String {
        val cleaned = urlRegex.replace(message, " ")
            .replace(suffixPriceRegex, " ")
            .replace(prefixPriceRegex, " ")
            .replace(binPriceRegex, " ")
            .replace(discountRegex, " ")
            .replace(Regex("(?i)\\b(indirim|fırsat|firsat|son stok|kaçırma|kacirma|onual|kampanya|sepette|kupon)\\b"), " ")
            .replace(Regex("[\\p{So}\\p{Sk}\\uFE0F]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '>', '|', '.', ',')
            .take(120)
        val candidate = cleaned.ifBlank { title.ifBlank { conversationTitle } }
        return candidate.ifBlank { "Yeni fırsat" }.take(100)
    }

    private fun cleanUrl(raw: String): String = raw.trim().trimEnd('.', ',', ';', ')', ']', '}', '>', '!', '?')
        .let { if (it.startsWith("www.", true)) "https://$it" else it }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
