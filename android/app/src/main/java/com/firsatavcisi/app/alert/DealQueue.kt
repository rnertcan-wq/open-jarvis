package com.firsatavcisi.app.alert

import com.firsatavcisi.app.data.Deal

/**
 * 1.2.0: ayni anda birden fazla firsat gelirse hepsi siraya girer.
 * Onceki surumde yeni firsat oncekini eziyordu ve sadece sonuncusu duyuluyordu.
 */
object DealQueue {
    private const val MAX = 10
    private val items = ArrayDeque<Deal>()

    @Synchronized
    fun add(deal: Deal) {
        if (items.any { it.id == deal.id }) return
        if (items.size >= MAX) items.removeFirst()
        items.addLast(deal)
    }

    @Synchronized
    fun next(): Deal? = items.removeFirstOrNull()

    @Synchronized
    fun peek(): Deal? = items.firstOrNull()

    @Synchronized
    fun size(): Int = items.size

    @Synchronized
    fun clear() = items.clear()
}
