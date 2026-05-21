package com.qwe7002.telegram_sms.static_class

import java.util.concurrent.atomic.AtomicInteger

object SnowFlake {
    private val sequence = AtomicInteger(0)
    private const val MAX = 9999
    @JvmStatic
    fun generate(): Int {
        // Manual CAS loop — equivalent to AtomicInteger.updateAndGet, which is
        // API 24+ (project minSdk is 23 and core library desugaring isn't enabled).
        // The prior incrementAndGet + if + set could race across wrap-around,
        // letting two threads both reset to 0 and return 1.
        while (true) {
            val current = sequence.get()
            val next = if (current >= MAX) 1 else current + 1
            if (sequence.compareAndSet(current, next)) return next
        }
    }
}

