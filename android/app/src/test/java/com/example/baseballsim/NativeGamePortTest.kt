package com.example.baseballsim

import com.example.baseballsim.core.GameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NativeGamePortTest {
    @Test
    fun readingSnapshotsDoesNotChangeTheNativePitchOrRandomSequence() {
        val observed = NativeGamePortFactory.create("42")
        val control = NativeGamePortFactory.create("42")
        val before = observed.snapshot()
        repeat(100) { assertEquals(before, observed.snapshot()) }
        assertEquals(control.advanceOnePitch(), observed.advanceOnePitch())
    }

    @Test
    fun nativeInitialStateHasNineInningsAndTheFirstBatter() {
        val initial = NativeGamePortFactory.create("18446744073709551615").snapshot()
        assertEquals("18446744073709551615", initial.seed)
        assertEquals(GameStatus.IN_PROGRESS, initial.status)
        assertEquals(9, initial.lineAway.size)
        assertEquals(9, initial.lineHome.size)
        assertEquals("0", initial.lineAway.first())
        assertNull(initial.lineHome.first())
        assertEquals(1, initial.current?.batterSlot)
        assertEquals(HalfSide.TOP, initial.current?.side)
        assertEquals(listOf(false, false, false), initial.current?.bases)
        assertEquals(0L, initial.pitchesAccepted)
        assertEquals(0L, initial.completedPa)
    }

    @Test
    fun abortedNativeGameUsesThePartialScoreAndHasNoCurrentProjection() {
        val session = GameSession(seed = "42", maxPitches = 1)
        val port = NativeGamePort(session)
        val ended = port.advanceOnePitch()
        assertEquals(GameStatus.ABORTED, ended.status)
        assertEquals(session.snapshot.score.away.toString(), ended.scoreAway)
        assertEquals(session.snapshot.score.home.toString(), ended.scoreHome)
        assertNull(ended.current)
        assertNull(ended.outcome)
        assertNotNull(ended.reason)
        assertFalse(ended.walkoff)
    }
}
