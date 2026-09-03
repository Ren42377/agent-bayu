package dev.agentbayu.app.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskScheduleTest {

    @Test
    fun aTimedTaskFiresAtItsOwnTime() {
        val due = at("2026-09-03", "14:30")
        assertEquals(due, triggerAtMillis(task(dueAtMillis = due, hasTime = true), TEST_ZONE))
    }

    @Test
    fun aDateOnlyTaskFiresAtNineInTheMorning() {
        val target = task(dueAtMillis = at("2026-09-03", "14:30"))
        assertEquals(at("2026-09-03", "09:00"), triggerAtMillis(target, TEST_ZONE))
    }

    @Test
    fun aDeadlineFiresAtNineOnTheDeadlineDay() {
        val target = task(deadlineAtMillis = at("2026-09-05", "18:00"))
        assertEquals(at("2026-09-05", "09:00"), triggerAtMillis(target, TEST_ZONE))
    }

    @Test
    fun aTaskWithoutDatesNeverFires() {
        assertNull(triggerAtMillis(task(), TEST_ZONE))
        assertNull(nextTriggerMillis(task(), TEST_ZONE, at("2026-09-03", "10:00")))
    }

    @Test
    fun aCompletedTaskNeverFires() {
        val target = task(
            dueAtMillis = at("2026-09-03", "08:00"),
            hasTime = true,
            completed = true
        )
        assertNull(nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "07:00")))
    }

    @Test
    fun aFutureTriggerIsKeptAsIs() {
        val due = at("2026-09-03", "14:30")
        val target = task(dueAtMillis = due, hasTime = true)
        assertEquals(due, nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00")))
    }

    @Test
    fun anOverdueTaskIsNudgedTheNextMorning() {
        val target = task(dueAtMillis = at("2026-09-03", "08:00"), hasTime = true)
        assertEquals(
            at("2026-09-04", "09:00"),
            nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00"))
        )
    }

    @Test
    fun anOverdueTaskIsNudgedTheSameMorningWhenNineIsStillAhead() {
        val target = task(dueAtMillis = at("2026-09-03", "06:00"), hasTime = true)
        assertEquals(
            at("2026-09-03", "09:00"),
            nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "07:00"))
        )
    }

    @Test
    fun aDailySeriesSkipsToTheFirstFutureOccurrence() {
        val target = task(
            dueAtMillis = at("2026-09-01", "08:00"),
            hasTime = true,
            repeat = daily()
        )
        assertEquals(
            at("2026-09-04", "08:00"),
            nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00"))
        )
    }

    @Test
    fun aWeeklySeriesWalksTheSelectedWeekdays() {
        val target = task(
            dueAtMillis = at("2026-09-01", "08:00"),
            hasTime = true,
            repeat = TaskRepeat(unit = RepeatUnit.WEEK, weekdays = listOf(2, 4))
        )
        assertEquals(
            at("2026-09-03", "08:00"),
            nextTriggerMillis(target, TEST_ZONE, at("2026-09-01", "10:00"))
        )
    }

    @Test
    fun aWeeklySeriesJumpsToTheNextWeekAfterTheLastWeekday() {
        val target = task(
            dueAtMillis = at("2026-09-03", "08:00"),
            hasTime = true,
            repeat = TaskRepeat(unit = RepeatUnit.WEEK, weekdays = listOf(2, 4))
        )
        assertEquals(
            at("2026-09-08", "08:00"),
            nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00"))
        )
    }

    @Test
    fun monthlyAndYearlySeriesKeepTheTimeOfDay() {
        val base = at("2026-09-01", "08:00")
        val monthly = task(
            dueAtMillis = base,
            hasTime = true,
            repeat = TaskRepeat(unit = RepeatUnit.MONTH)
        )
        val yearly = task(
            dueAtMillis = base,
            hasTime = true,
            repeat = TaskRepeat(unit = RepeatUnit.YEAR)
        )
        val now = at("2026-09-03", "10:00")
        assertEquals(at("2026-10-01", "08:00"), nextTriggerMillis(monthly, TEST_ZONE, now))
        assertEquals(at("2027-09-01", "08:00"), nextTriggerMillis(yearly, TEST_ZONE, now))
    }

    @Test
    fun aSeriesThatEndsOnADateStopsFiring() {
        val target = task(
            dueAtMillis = at("2026-09-01", "08:00"),
            hasTime = true,
            repeat = daily(endAtMillis = at("2026-09-03", "23:59"))
        )
        assertNull(nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00")))
    }

    @Test
    fun aSeriesThatEndsAfterACountStopsFiring() {
        val target = task(
            dueAtMillis = at("2026-09-01", "08:00"),
            hasTime = true,
            repeat = daily(endAfterCount = 2)
        )
        assertNull(nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00")))
    }

    @Test
    fun anEndBoundIsReadFromTheDateAndTheCount() {
        val bounded = daily(endAtMillis = at("2026-09-03", "23:59"), endAfterCount = 3)
        assertFalse(isSeriesFinished(bounded, 2, at("2026-09-03", "08:00")))
        assertTrue(isSeriesFinished(bounded, 3, at("2026-09-03", "08:00")))
        assertTrue(isSeriesFinished(bounded, 1, at("2026-09-04", "08:00")))
        assertFalse(isSeriesFinished(daily(), 99, at("2030-01-01", "08:00")))
    }

    @Test
    fun aRepeatingTaskInTheFutureIsNotRolled() {
        val due = at("2026-09-05", "08:00")
        val target = task(dueAtMillis = due, hasTime = true, repeat = daily())
        assertEquals(due, nextTriggerMillis(target, TEST_ZONE, at("2026-09-03", "10:00")))
    }

    @Test
    fun theDateSortPutsUndatedTasksLast() {
        val undated = task(id = "a", position = 0)
        val later = task(id = "b", dueAtMillis = at("2026-09-05", "08:00"), position = 1)
        val sooner = task(id = "c", deadlineAtMillis = at("2026-09-04", "08:00"), position = 2)
        assertEquals(
            listOf("c", "b", "a"),
            sortTasks(listOf(undated, later, sooner), TaskSort.DATE).map { it.id }
        )
    }

    @Test
    fun theStarredSortPutsTheNewestStarFirst() {
        val plain = task(id = "a", position = 0)
        val older = task(
            id = "b",
            starred = true,
            starredAtMillis = at("2026-09-01", "10:00"),
            position = 1
        )
        val newer = task(
            id = "c",
            starred = true,
            starredAtMillis = at("2026-09-02", "10:00"),
            position = 2
        )
        assertEquals(
            listOf("c", "b", "a"),
            sortTasks(listOf(plain, older, newer), TaskSort.STARRED).map { it.id }
        )
    }

    @Test
    fun theDefaultSortFollowsPosition() {
        val tasks = listOf(
            task(id = "a", position = 2),
            task(id = "b", position = 0),
            task(id = "c", position = 1)
        )
        assertEquals(listOf("b", "c", "a"), sortTasks(tasks, TaskSort.MY_ORDER).map { it.id })
    }
}
