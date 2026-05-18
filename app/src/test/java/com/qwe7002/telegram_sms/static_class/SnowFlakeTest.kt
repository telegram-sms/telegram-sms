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
        val field = SnowFlake::class.java.getDeclaredField("sequence")
        field.isAccessible = true
        (field.get(SnowFlake) as AtomicInteger).set(0)
    }

    @Test
    fun generate_startsFromOne() {
        assertEquals(1, SnowFlake.generate())
    }

    @Test
    fun generate_isMonotonicallyIncreasing() {
        var previous = SnowFlake.generate()
        repeat(100) {
            val next = SnowFlake.generate()
            assertTrue("$next should be > $previous", next > previous)
            previous = next
        }
    }

    @Test
    fun generate_wrapsAroundAtMax() {
        val field = SnowFlake::class.java.getDeclaredField("sequence")
        field.isAccessible = true
        (field.get(SnowFlake) as AtomicInteger).set(9999)

        // 9999 is at the limit; next call should hit > MAX, reset, and return 1.
        assertEquals(1, SnowFlake.generate())
    }

    // Verifies AtomicInteger.incrementAndGet() is safe under contention.
    // NOTE: stays below MAX (9999) intentionally — wrap-around has a known race
    // (read-modify-write across the if-block isn't atomic) that would make this
    // test flaky. Covering that race needs a redesign of generate() itself.
    @Test(timeout = 5_000)
    fun generate_producesUniqueValues_underConcurrentAccess_belowWrapAround() {
        val threadCount = 16
        val callsPerThread = 500
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

    @Test
    fun generate_returnsDifferentValuesOnConsecutiveCalls() {
        assertNotEquals(SnowFlake.generate(), SnowFlake.generate())
    }
}
