package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReelPlannerTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 9, 1)

    private fun photo(day: Long, id: String = "p$day") =
        ProgressPhoto(id, start.plusDays(day).atTime(8, 0).toInstant(zone), 1200, 1600)

    private fun at(day: Long) = start.plusDays(day).atTime(12, 0).toInstant(zone)

    @Test
    fun `first reel is due one interval after the anchor`() {
        val s = ReelSettings()
        assertEquals(start.plusDays(28), ReelPlanner.nextDue(start, s))
        assertNull(ReelPlanner.dueOnOrBefore(start, s, start.plusDays(27)))
        assertEquals(start.plusDays(28), ReelPlanner.dueOnOrBefore(start, s, start.plusDays(28)))
    }

    @Test
    fun `a long gap handles only the latest due date, no backlog`() {
        val s = ReelSettings(interval = ReelInterval.DAYS_3)
        assertEquals(start.plusDays(9), ReelPlanner.dueOnOrBefore(start, s, start.plusDays(10)))
    }

    @Test
    fun `changing the interval counts from the last handled reel`() {
        val s = ReelSettings(interval = ReelInterval.WEEK_1, lastDue = start.plusDays(28))
        assertEquals(start.plusDays(35), ReelPlanner.nextDue(start, s))
    }

    @Test
    fun `due with enough new photos makes a regular reel`() {
        val photos = listOf(photo(0), photo(7), photo(14), photo(27))
        val d = ReelPlanner.decide(start.plusDays(28), start, photos, null, ReelSettings(), zone = zone)
        assertTrue(d is ReelDecision.Make)
        d as ReelDecision.Make
        assertEquals(ReelKind.REGULAR, d.kind)
        assertEquals(start.plusDays(28), d.handledDue)
        assertEquals(listOf("p0", "p7", "p14", "p27"), d.frames.map { it.id })
    }

    @Test
    fun `nothing new since the last reel skips the due date quietly`() {
        val photos = listOf(photo(0), photo(7), photo(14))
        val d = ReelPlanner.decide(start.plusDays(28), start, photos, at(20), ReelSettings(), zone = zone)
        assertEquals(ReelDecision.Skip(start.plusDays(28)), d)
    }

    @Test
    fun `fewer than three photos never makes a reel`() {
        val d = ReelPlanner.decide(start.plusDays(28), start, listOf(photo(0), photo(20)), null, ReelSettings(), zone = zone)
        assertEquals(ReelDecision.Skip(start.plusDays(28)), d)
    }

    @Test
    fun `off means no reels`() {
        val photos = listOf(photo(0), photo(7), photo(14))
        assertEquals(ReelDecision.Skip(null), ReelPlanner.decide(start.plusDays(28), start, photos, null, ReelSettings(enabled = false), zone = zone))
    }

    @Test
    fun `a milestone makes a reel between due dates and covers one due soon`() {
        val photos = listOf(photo(0), photo(7), photo(20))
        val d = ReelPlanner.decide(start.plusDays(26), start, photos, null, ReelSettings(), milestoneKg = 2.0, zone = zone) as ReelDecision.Make
        assertEquals(ReelKind.MILESTONE, d.kind)
        assertEquals(2.0, d.milestoneKg!!, 0.0)
        assertEquals(start.plusDays(28), d.handledDue) // the regular reel two days later is folded in
        // A milestone that already had its reel doesn't make another.
        assertEquals(
            ReelDecision.Skip(null),
            ReelPlanner.decide(start.plusDays(10), start, photos, null, ReelSettings(lastMilestoneKg = 2.0), milestoneKg = 2.0, zone = zone),
        )
    }

    @Test
    fun `reaching the goal makes one goal reel`() {
        val photos = listOf(photo(0), photo(7), photo(20))
        val d = ReelPlanner.decide(start.plusDays(21), start, photos, null, ReelSettings(), goalReached = start, zone = zone)
        assertEquals(ReelKind.GOAL, (d as ReelDecision.Make).kind)
        assertEquals(ReelDecision.Skip(null), ReelPlanner.decide(start.plusDays(21), start, photos, null, ReelSettings(goalReelFor = start), goalReached = start, zone = zone))
    }

    @Test
    fun `frames keep the first, every new photo and at most one a week before`() {
        // Daily photos for 8 weeks; last reel after day 49.
        val photos = (0L..55L).map { photo(it) }
        val frames = ReelPlanner.frames(photos, at(49), zone = zone)
        assertEquals("p0", frames.first().id)
        assertEquals((50L..55L).map { "p$it" }, frames.takeLast(6).map { it.id })
        assertTrue(frames.size <= ReelPlanner.MAX_FRAMES)
        val middle = frames.drop(1).dropLast(6).map { ProgressMath.week(start, it.date(zone)) }
        assertEquals(middle.distinct(), middle) // one per week
    }

    @Test
    fun `too many new photos keeps the newest`() {
        val photos = (0L..30L).map { photo(it) }
        val frames = ReelPlanner.frames(photos, at(0), zone = zone)
        assertEquals(ReelPlanner.MAX_FRAMES, frames.size)
        assertEquals("p0", frames.first().id)
        assertEquals("p30", frames.last().id)
        assertEquals("p20", frames[1].id)
    }
}
