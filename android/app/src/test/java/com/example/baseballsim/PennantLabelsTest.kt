package com.example.baseballsim

import com.example.baseballsim.season.ClubTotals
import com.example.baseballsim.season.Standing
import org.junit.Assert.assertEquals
import org.junit.Test

class PennantLabelsTest {
    @Test fun calendarStartsIn2026AndDerivesActualWeekdays() {
        assertEquals("2026年3月26日（木）", pennantDate(1, 1))
        assertEquals("2026年3月27日（金）", pennantDate(1, 2))
        assertEquals("2026年4月1日（水）", pennantDate(1, 7))
        assertEquals("2027年3月27日（土）", pennantDate(2, 2))
        assertEquals("2028年3月27日（月）", pennantDate(3, 2))
    }

    @Test fun standingsExcludeDrawsAndMeasureAgainstLeagueLeader() {
        val leader = Standing(ClubTotals("L1-T01", 10, 6, 3, 1), 1)
        val other = Standing(ClubTotals("L1-T02", 10, 4, 4, 2), 2)
        val allDraws = Standing(ClubTotals("L1-T03", 10, 0, 0, 10), null)
        assertEquals(".667", winPercentage(leader))
        assertEquals(".500", winPercentage(other))
        assertEquals("—", winPercentage(allDraws))
        assertEquals("—", gamesBehind(leader, leader))
        assertEquals("1.5", gamesBehind(other, leader))
        assertEquals("—", gamesBehind(allDraws, leader))
    }
}
