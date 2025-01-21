package com.qwe7002.telegram_sms.static_class

import java.util.concurrent.atomic.AtomicInteger

object SnowFlake {
    private val sequence = AtomicInteger(0)
    private const val maxSequence = 9999
    @JvmStatic
    fun generate(): Int {
        val currentSequence = sequence.incrementAndGet()
        if (currentSequence > maxSequence) {
            sequence.set(0)
            return sequence.incrementAndGet()
        }
        return currentSequence
    }
}

