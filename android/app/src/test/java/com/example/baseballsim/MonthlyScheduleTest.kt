package com.example.baseballsim

import com.example.baseballsim.core.Score
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class MonthlyScheduleTest {
    @Test fun monthStartsOnSundayAndPadsOnlyDaysOutsideTheMonth() {
        val march = scheduleWeeks(YearMonth.of(2026, 3))
        assertTrue(march.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 3, 1), march.first().first())
        assertEquals(LocalDate.of(2026, 3, 27), march[3][5])
        assertEquals(listOf(29, 30, 31, null, null, null, null), march.last().map { it?.dayOfMonth })

        val february = scheduleWeeks(YearMonth.of(2028, 2))
        assertEquals(listOf(null, null, 1, 2, 3, 4, 5), february.first().map { it?.dayOfMonth })
        assertEquals((1..29).toList(), february.flatten().filterNotNull().map { it.dayOfMonth })
        assertEquals(LocalDate.of(2028, 2, 29),
            scheduleMonthDate(LocalDate.of(2028, 1, 31), YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 2, 28),
            scheduleMonthDate(LocalDate.of(2026, 1, 31), YearMonth.of(2026, 2)))
    }

    @Test fun calendarUsesTheExistingDayOrderAcrossMonthsAndYears() {
        val first = pennantLocalDate(1L, 1)
        assertEquals(LocalDate.of(2026, 3, 26), first)
        assertTrue(scheduleDay(first, first)!!.isRestDay)
        assertNull(scheduleDay(first, first.minusDays(1)))
        assertEquals((1..6).toList(), scheduleDay(first, LocalDate.of(2026, 3, 27))!!.gameOrdinals)
        assertEquals(7, scheduleDay(first, LocalDate.of(2026, 4, 1))!!.dayIndex)
        assertEquals(140, scheduleDay(first, first.plusDays(139))!!.dayIndex)
        assertNull(scheduleDay(first, first.plusDays(140)))
        val nextYear = pennantLocalDate(2L, 1)
        assertEquals(LocalDate.of(2027, 3, 27), nextYear.plusDays(1))
        assertEquals(2, scheduleDay(nextYear, nextYear.plusDays(1))!!.dayIndex)
    }

    @Test fun everyOutcomeUsesTheSelectedClubPerspective() {
        val home = scheduleScore(Score(3L, 5L), isHome = true)
        assertEquals(ScheduleScore(5L, 3L), home)
        assertEquals("○", home.symbol)
        assertEquals("勝ち", home.outcome)
        val away = scheduleScore(Score(3L, 5L), isHome = false)
        assertEquals(ScheduleScore(3L, 5L), away)
        assertEquals("●", away.symbol)
        assertEquals("負け", away.outcome)
        val awayWin = scheduleScore(Score(8L, 6L), isHome = false)
        assertEquals(ScheduleScore(8L, 6L), awayWin)
        assertEquals("○", awayWin.symbol)
        val draw = scheduleScore(Score(2L, 2L), isHome = false)
        assertEquals(ScheduleScore(2L, 2L), draw)
        assertEquals("△", draw.symbol)
        assertEquals("引き分け", draw.outcome)
    }
}
