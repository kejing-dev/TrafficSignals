package io.embodit.trafficsignals.ui.scene

import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Move
import io.embodit.trafficsignals.domain.Vec3
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA q1-1 (profile D14 / D20): your ghost car never starts in your own seat. Intersection space, your eye at
 * (lane.x - 0.4, 14) (TrafficScene.EYE_TO_CENTRE / EYE_LEFT_OF_LANE), bonnet nose 1.95 m ahead of the eye, panels at 0.95 m.
 */
class GhostPathTest {
    private val half = TrafficScene.GHOST_HALF_LEN
    private val bonnetNose = 1.95f

    private fun check(lane: Lane, m: Move, wait: Boolean) {
        val ex = lane.x - TrafficScene.EYE_LEFT_OF_LANE
        val ez = TrafficScene.EYE_TO_CENTRE
        val p = Street.ghostRun(lane.x, m, wait, Vec3(ex, 0f, ez))
        for (i in 0..200) {
            val c = p.at(i / 200f)
            // nearest point of the car body (a 4.6 m long box) to the eye, horizontally
            val gap = hypot(c.x - ex, c.z - ez) - half - 0.9f
            assertTrue("lane=$lane move=$m t=${i / 200f} car body only ${gap} m from your eye", gap >= 2.45f)
            // never level with or behind your bonnet nose, so never between your eyes and a panel at 0.95 m
            assertTrue("lane=$lane move=$m centre z=${c.z}", c.z < ez - bonnetNose)
        }
    }

    @Test
    fun ghostStartsAheadOfYourBonnetForEveryLane() {
        for (lane in Lane.entries) check(lane, Judge.yourMove(lane), wait = false)
        check(Lane.LEFT, Move.WAIT, wait = true)
    }

    @Test
    fun ghostDrivesOutAlongTheVisibleRouteArrow() {
        // design scene.yourRoute: the arrow (and the ghost) start 3 m before the junction centre, beyond P2's road cover
        for (lane in Lane.entries) {
            val ex = lane.x - TrafficScene.EYE_LEFT_OF_LANE
            val run = Street.ghostRun(lane.x, Judge.yourMove(lane), false, Vec3(ex, 0f, TrafficScene.EYE_TO_CENTRE))
            // same curve and start parameter as the route ribbon in TrafficScene.renderFlows
            val arrowStart = Street.path(io.embodit.trafficsignals.domain.Compass.SOUTH, lane.x, Judge.yourMove(lane)).nearest(Vec3(lane.x, 0f, Street.ROUTE_START_D))
            assertTrue("lane=$lane t0=${run.t0} arrow=$arrowStart", run.t0 >= arrowStart - 1e-4f)
            // inside the junction mouth (the right turn bends round the near corner, so it starts on the crosswalk)
            assertTrue("lane=$lane starts at z=${run.at(0f).z}", run.at(0f).z <= 10.0f)
        }
    }

    @Test
    fun waitGhostEndsInsideTheWaitingBox() {
        val end = Street.ghostRun(Lane.LEFT.x, Move.WAIT, true, Vec3(Lane.LEFT.x - 0.4f, 0f, 14f)).at(1f)
        // painted box: x 0.25..2.9, front line 3.8..4.0, mouth at CROSS_IN - 0.3 = 8.7; car 1.8 m wide
        assertTrue(end.x - 0.9f >= 0.25f && end.x + 0.9f <= 2.9f)
        assertTrue("front ${end.z - half}", end.z - half >= 4.0f)
        assertTrue("tail ${end.z + half}", end.z + half <= 8.7f + 0.01f)
    }
}
