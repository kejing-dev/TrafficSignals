package io.embodit.trafficsignals.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-05 headset: "别把交警模型放我身上，还是在台上比较好" (profile D14) and the window that must not cover him (D41).
 * The officer's spot and the seat come from these functions only; TrafficScene has no other place for him.
 */
class StageLayoutTest {
    private val eyeLocal = Vec3(0f, StageLayout.EYE_H, 0f)

    private fun head(yawDeg: Float, x: Float = 0.3f, z: Float = -0.4f) =
        HeadPose(Vec3(x, 1.65f, z), Quat4.axisAngle(Vec3.UP, Math.toRadians(yawDeg.toDouble()).toFloat()))

    /** Distance from the head to the nearest part of him, Stage space, for a placement from [h]. */
    private fun clearance(h: HeadPose, feetLocal: Vec3, laneX: Float, big: Boolean): Float {
        val origin = Vec3(h.position.x, h.position.y - StageLayout.EYE_H, h.position.z)
        val yaw = StageLayout.driverYaw(h.yaw(0f), laneX, big)
        val feet = Upright.toStage(origin, yaw, feetLocal)
        val flat = kotlin.math.hypot(feet.x - h.position.x, feet.z - h.position.z)
        return flat - StageLayout.ARM_REACH * (if (big) StageLayout.BIG else 1f)
    }

    @Test
    fun openingSpotIsOnThePostFarFromYou() {
        val feet = StageLayout.openingFeet()
        assertEquals("he stands on the post top", StageLayout.POST_H, feet.y, 1e-4f)
        assertTrue("opening spot ${feet.length} m out", (feet - eyeLocal).length >= StageLayout.MIN_CLEARANCE_M)
        for (yaw in listOf(0f, 90f, 180f, 270f, 33f)) {
            val c = clearance(head(yaw), feet, Lane.STRAIGHT.x, false)
            assertTrue("opening, head yaw $yaw: $c m", c >= StageLayout.MIN_CLEARANCE_M)
        }
    }

    @Test
    fun everySpotKeepsClearOfYou_openResetReopenLessonQuiz() {
        for (lesson in listOf(false, true)) for (lane in Lane.entries) for (big in listOf(false, true)) for (yaw in listOf(0f, 90f, 180f, 270f)) {
            val feet = StageLayout.officerFeet(lesson, lane.x)
            val c = clearance(head(yaw), feet, lane.x, big)
            assertTrue("lesson=$lesson lane=$lane big=$big yaw=$yaw: $c m", c >= StageLayout.MIN_CLEARANCE_M)
            assertEquals("always on a post top", StageLayout.POST_H, feet.y, 1e-4f)
        }
    }

    @Test
    fun afterAPlacementTheSpotDoesNotDependOnHeadTurnsOrResets() {
        // re-placing (reset, reopen) only moves the whole driver frame: in that frame he stays exactly on his post
        val a = StageLayout.officerFeet(false, Lane.STRAIGHT.x)
        val placer = SeatPlacer()
        placer.request("reset", 0)
        var t = 0L
        while (t < 3000L) {
            placer.step(t, head(120f).position to head(120f).yaw(0f))
            t += 16
        }
        assertEquals(a, StageLayout.officerFeet(false, Lane.STRAIGHT.x))
        assertTrue(placer.placed)
    }

    @Test
    fun windowNeverCoversHim() {
        // the window frame (the TabBar and the system row fit inside its width), lane by lane, lesson post, life size and x1.5
        for (lesson in listOf(false, true)) for (lane in Lane.entries) for (big in listOf(false, true)) {
            val feet = StageLayout.officerFeet(lesson, lane.x)
            val gap = StageLayout.windowGapDeg(feet, lane.x, big)
            assertTrue("lesson=$lesson lane=$lane big=$big gap=$gap", gap >= 10f)
        }
    }

    @Test
    fun officerBearingIsTheSameInEveryLane() {
        for (lane in Lane.entries) {
            val p = StageLayout.inSightFrame(StageLayout.postFeet(lane.x), lane.x, false)
            val deg = Math.toDegrees(kotlin.math.atan2(-p.x, -p.z).toDouble())
            assertEquals("lane $lane", StageLayout.OFFICER_LEFT_DEG.toDouble(), deg, 0.1)
        }
    }

    @Test
    fun aSeatTurnNeverMovesYourEye() {
        for (lane in Lane.entries) for (big in listOf(false, true)) assertTrue(StageLayout.inSightFrame(eyeLocal, lane.x, big).length < 1e-4f)
    }

    @Test
    fun ownCarStaysClearOfTheWindowsSystemRow() {
        for (lane in Lane.entries) for (big in listOf(false, true)) {
            val gap = StageLayout.hoodGapDeg(lane.x, big)
            assertTrue("lane=$lane big=$big: ${StageLayout.windowBottomDeg()}° row, gap $gap°", gap >= 2.5f)
        }
    }

    @Test
    fun verdictStepsStandOnTheNearPostInEveryLaneAndClearOfTheWindow() {
        for (lane in Lane.entries) for (big in listOf(false, true)) {
            val feet = StageLayout.officerFeet(true, lane.x)
            assertEquals(StageLayout.LESSON_DIST, kotlin.math.hypot(feet.x, feet.z), 1e-3f)
            val gap = StageLayout.windowGapDeg(feet, lane.x, big)
            assertTrue("lane=$lane big=$big gap=$gap", gap >= 10f)
        }
    }

    @Test
    fun lessonPostIsSixMetresOutJustLeftOfTheCrossroadsPost() {
        val a = StageLayout.lessonSpot(Lane.STRAIGHT.x)
        val b = StageLayout.junctionLocal(Lane.STRAIGHT.x)
        assertEquals(StageLayout.LESSON_DIST, kotlin.math.hypot(a.x, a.z), 1e-3f)
        val diff = Math.toDegrees((kotlin.math.atan2(-a.x, -a.z) - kotlin.math.atan2(-b.x, -b.z)).toDouble())
        assertEquals(StageLayout.LESSON_EXTRA_LEFT_DEG.toDouble(), diff, 0.1)
    }

    @Test
    fun chipIsBelowTheSightLineAndUpright() {
        val h = head(40f)
        val at = StageLayout.chipAt(h.position, h.yaw(0f), 1f, 16f)
        assertTrue(at.y < h.position.y)
        val rot = Upright.panelRotation(at, h.position)
        assertEquals(1f, rot.rotate(Vec3.UP).dot(Vec3.UP), 1e-4f)
        // never in the officer's way: he is 36° to the left of the sight line it hangs on
        assertTrue(StageLayout.OFFICER_LEFT_DEG > 20f)
    }
}

class SeatPlacerTest {
    private val rad = { d: Float -> Math.toRadians(d.toDouble()).toFloat() }
    private val eye = Vec3(0.2f, 1.6f, -0.1f)

    private fun run(p: SeatPlacer, from: Long, to: Long, yawDeg: (Long) -> Float?): List<SeatPlacer.Spot> {
        val out = ArrayList<SeatPlacer.Spot>()
        var t = from
        while (t <= to) {
            p.step(t, yawDeg(t)?.let { eye to rad(it) })?.let { out += it }
            t += 16
        }
        return out
    }

    @Test
    fun nothingIsPlacedWithoutAValidHead() {
        val p = SeatPlacer()
        p.request("start", 0)
        assertTrue(run(p, 0, 20_000) { null }.isEmpty())
        assertFalse(p.placed)
        assertNotNull(run(p, 20_016, 21_000) { 10f }.firstOrNull())
        assertTrue(p.placed)
    }

    @Test
    fun looksAtTheButtonThenTurnsToFrontContentEndsInFront() {
        // D29: [回正] pressed while looking 30° aside at the key, head back to the front within a second and stays
        val p = SeatPlacer()
        p.request("start", 0)
        run(p, 0, 3000) { 0f }
        for (button in listOf(30f, -30f, 12f)) {
            val t0 = 20_000L
            p.request("recenter", t0)
            val first = run(p, t0, t0 + 600) { button }.first()
            assertTrue("the first place is a visible jump: it fades in", first.jump)
            run(p, t0 + 616, t0 + 6000) { t -> if (t < t0 + 1400) button * (1 - (t - t0 - 600) / 800f) else 0f }
            assertEquals("button at $button", 0f, Math.toDegrees(p.yaw.toDouble()).toFloat(), 2f)
        }
    }

    @Test
    fun locksAfterSettling() {
        val p = SeatPlacer()
        p.request("start", 0)
        run(p, 0, 3000) { 5f }
        val locked = p.yaw
        assertTrue("head turns 40°, the seat stays", run(p, 3016, 9000) { 45f }.isEmpty())
        assertEquals(locked, p.yaw, 1e-5f)
    }

    @Test
    fun farTurnReplacesOnceInTheFirstTenSeconds() {
        val p = SeatPlacer()
        p.request("start", 0)
        run(p, 0, 3000) { 0f }
        val again = run(p, 3016, 5000) { 120f }
        assertEquals(1, again.size)
        assertEquals("auto-again", again[0].why)
        assertTrue(run(p, 5016, 7000) { 0f }.isEmpty())
    }

    @Test
    fun windowOpenedElsewhereMovesTheSeatOnlyWhenOffByMoreThan5Degrees() {
        val p = SeatPlacer()
        p.request("start", 0)
        run(p, 0, 3000) { 0f }
        p.windowOpened(20_000, rad(4f))
        assertFalse(p.waiting)
        p.windowOpened(20_000, rad(25f))
        assertTrue(p.waiting)
        val s = run(p, 20_000, 20_600) { 25f }.first()
        assertTrue(s.jump)
        assertEquals("window", s.why)
        assertEquals(25f, Math.toDegrees(s.yaw.toDouble()).toFloat(), 0.5f)
    }

    @Test
    fun yawWrapsAcrossMinusPi() {
        val p = SeatPlacer()
        p.request("start", 0)
        val s = run(p, 0, 700) { t -> if (t % 32 == 0L) 179f else -179f }.first()
        assertTrue(kotlin.math.abs(Math.toDegrees(s.yaw.toDouble())) > 175.0)
    }

    @Test
    fun noRequestNoPlacement() {
        assertNull(SeatPlacer().step(0, eye to 0f))
    }
}
