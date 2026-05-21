package com.qwe7002.telegram_sms.static_class

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SnowFlakeTest {

    // SnowFlake.sequence is a private singleton field; reset it before each test
    // so order-independent expectations work.
    @Before
    fun resetSequence() {
        setSequence(0)
    }

    @Test
    fun generate_startsFromOne() {
        assertEquals(1, SnowFlake.generate())
    }

    @Test
    fun generate_isMonotonicallyIncreasing_withinAWindow() {
        var previous = SnowFlake.generate()
        repeat(100) {
            val next = SnowFlake.generate()
            assertTrue("$next should be > $previous", next > previous)
            previous = next
        }
    }

    @Test
    fun generate_stayedWithinRange() {
        repeat(500) {
            val id = SnowFlake.generate()
            assertTrue("id $id out of [1, $MAX]", id in 1..MAX)
        }
    }

    @Test
    fun generate_wrapsAroundAtMax() {
        setSequence(MAX)
        assertEquals(1, SnowFlake.generate())
        assertEquals(2, SnowFlake.generate())
    }

    @Test
    fun generate_wrapsAround_evenIfStateOverMax() {
        // Defensive: even if state somehow exceeded MAX, the next call should wrap
        // rather than climb forever.
        setSequence(20_000)
        assertEquals(1, SnowFlake.generate())
    }

    @Test
    fun generate_returnsDifferentValuesOnConsecutiveCalls() {
        assertNotEquals(SnowFlake.generate(), SnowFlake.generate())
    }

    @Test(timeout = 5_000)
    fun generate_producesUniqueValues_underConcurrentAccess_belowWrapAround() {
        val threadCount = 16
        val callsPerThread = 500 // 8000 < MAX, no wrap → every id must be unique
        val seen = ConcurrentHashMap.newKeySet<Int>()
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(callsPerThread) {
                        seen.add(SnowFlake.generate())
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(4, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals(threadCount * callsPerThread, seen.size)
    }

    // Regression: the prior incrementAndGet → if → set → incrementAndGet sequence
    // wasn't atomic across wrap-around. Two threads crossing MAX simultaneously
    // could both reset to 0 and both return 1. With updateAndGet, CAS retries the
    // lambda until commit, so any single id appears at most once per wrap-window.
    @Test(timeout = 10_000)
    fun generate_isUniqueAcrossWrapAround_underContention() {
        setSequence(MAX - 50) // park near the boundary so most calls cross it
        val threadCount = 32
        val callsPerThread = 200
        val total = threadCount * callsPerThread
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = Array(threadCount) { IntArray(callsPerThread) }
        repeat(threadCount) { t ->
            executor.submit {
                try {
                    for (i in 0 until callsPerThread) {
                        results[t][i] = SnowFlake.generate()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(8, TimeUnit.SECONDS)
        executor.shutdown()

        val all = results.flatMap { it.toList() }
        all.forEach { id -> assertTrue("id $id out of [1, $MAX]", id in 1..MAX) }

        // total = 6400 calls; MAX = 9999 → at most ceil(total / MAX) = 1 full wrap,
        // so no id should ever appear more than that many times.
        val maxAllowedOccurrences = (total + MAX - 1) / MAX
        val maxObserved = all.groupingBy { it }.eachCount().values.max()
        assertTrue(
            "an id appeared $maxObserved times — expected <= $maxAllowedOccurrences (race regression)",
            maxObserved <= maxAllowedOccurrences
        )
    }

    private fun setSequence(value: Int) {
        val field = SnowFlake::class.java.getDeclaredField("sequence")
        field.isAccessible = true
        (field.get(SnowFlake) as AtomicInteger).set(value)
    }

    private companion object {
        const val MAX = 9999
    }
}
