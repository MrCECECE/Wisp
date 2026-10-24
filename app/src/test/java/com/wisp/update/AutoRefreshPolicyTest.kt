package com.wisp.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshPolicyTest {
    private val now = 1_000_000_000_000L

    @Test
    fun `due when never refreshed`() {
        assertTrue(AutoRefreshPolicy.isDue(0L, now))
    }

    @Test
    fun `not due within interval`() {
        assertFalse(AutoRefreshPolicy.isDue(now - 5 * 60_000L, now))
    }

    @Test
    fun `due after interval`() {
        assertTrue(AutoRefreshPolicy.isDue(now - 16 * 60_000L, now))
    }

    @Test
    fun `due when clock moved backwards`() {
        assertTrue(AutoRefreshPolicy.isDue(now + 60_000L, now))
    }
}
