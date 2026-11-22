package com.example

import com.studyshield.shared.model.ClassSchedule
import com.studyshield.shared.model.FocusScheduleUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusScheduleUtilsTest {

    @Test
    fun clashDetectionBlocksSameDivisionOrSameTeacherOverlap() {
        val existing = listOf(
            ClassSchedule(
                id = "c1",
                teacherUid = "t1",
                division = "A",
                day = "MON",
                start = "09:00",
                end = "10:00",
                active = true
            )
        )

        // Same division overlap -> clash
        val divOverlap = ClassSchedule(
            teacherUid = "t2",
            division = "A",
            day = "MON",
            start = "09:30",
            end = "10:30"
        )
        assertNotNull(FocusScheduleUtils.findScheduleClash(divOverlap, existing))

        // Same teacher different division overlap -> clash
        val teacherOverlap = ClassSchedule(
            teacherUid = "t1",
            division = "B",
            day = "MON",
            start = "08:30",
            end = "09:15"
        )
        assertNotNull(FocusScheduleUtils.findScheduleClash(teacherOverlap, existing))

        // Adjacent back-to-back class -> no clash
        val adjacent = ClassSchedule(
            teacherUid = "t1",
            division = "A",
            day = "MON",
            start = "10:00",
            end = "11:00"
        )
        assertNull(FocusScheduleUtils.findScheduleClash(adjacent, existing))

        // Editing self -> ignored
        val selfEdit = ClassSchedule(
            id = "c1",
            teacherUid = "t1",
            division = "A",
            day = "MON",
            start = "09:00",
            end = "10:00"
        )
        assertNull(FocusScheduleUtils.findScheduleClash(selfEdit, existing, ignoreClassId = "c1"))
    }

    @Test
    fun dailyBreakCodeIsFourDigitsAndDeterministic() {
        val codeA1 = FocusScheduleUtils.computeDailyBreakCode("1MS22CS001", "20261122")
        val codeA2 = FocusScheduleUtils.computeDailyBreakCode("1ms22cs001", "20261122")
        val codeB = FocusScheduleUtils.computeDailyBreakCode("1MS22CS002", "20261122")
        val codeNextDay = FocusScheduleUtils.computeDailyBreakCode("1MS22CS001", "20261123")

        assertEquals(4, codeA1.length)
        assertTrue(codeA1.all { it.isDigit() })
        assertEquals(codeA1, codeA2)
        assertNotEquals(codeA1, codeB)
        assertNotEquals(codeA1, codeNextDay)
    }

    @Test
    fun isClassActiveNowChecksDayAndHalfOpenInterval() {
        val cls = ClassSchedule(
            day = "MON",
            start = "09:00",
            end = "10:00",
            active = true
        )
        assertTrue(FocusScheduleUtils.isClassActiveNow(cls, day = "MON", timeHHmm = "09:00"))
        assertTrue(FocusScheduleUtils.isClassActiveNow(cls, day = "MON", timeHHmm = "09:59"))
        assertFalse(FocusScheduleUtils.isClassActiveNow(cls, day = "MON", timeHHmm = "10:00"))
        assertFalse(FocusScheduleUtils.isClassActiveNow(cls, day = "TUE", timeHHmm = "09:30"))
    }

    @Test
    fun sortUpcomingLecturesOrdersLiveThenTodayThenFutureDays() {
        val finished = ClassSchedule(id = "past", day = "MON", start = "08:00", end = "09:00", active = true)
        val live = ClassSchedule(id = "live", day = "MON", start = "09:30", end = "10:30", active = true)
        val laterToday = ClassSchedule(id = "today", day = "MON", start = "14:00", end = "15:00", active = true)
        val tomorrow = ClassSchedule(id = "tue", day = "TUE", start = "09:00", end = "10:00", active = true)
        val sunday = ClassSchedule(id = "sun", day = "SUN", start = "10:00", end = "11:00", active = true)

        val upcomingOnly = FocusScheduleUtils.sortUpcomingLectures(
            classes = listOf(sunday, finished, laterToday, tomorrow, live),
            todayDay = "MON",
            timeHHmm = "09:45",
            includeFinishedToday = false
        )
        assertEquals(listOf("live", "today", "tue", "sun"), upcomingOnly.map { it.id })

        val withPast = FocusScheduleUtils.sortUpcomingLectures(
            classes = listOf(sunday, finished, laterToday, tomorrow, live),
            todayDay = "MON",
            timeHHmm = "09:45",
            includeFinishedToday = true
        )
        assertEquals(listOf("live", "today", "tue", "sun", "past"), withPast.map { it.id })
        assertEquals(
            "Starts in 4h 15m",
            FocusScheduleUtils.relativeStartLabel(laterToday, todayDay = "MON", timeHHmm = "09:45")
        )
        assertEquals(
            "Tomorrow • 09:00",
            FocusScheduleUtils.relativeStartLabel(tomorrow, todayDay = "MON", timeHHmm = "09:45")
        )
    }
}
