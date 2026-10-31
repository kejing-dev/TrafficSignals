package io.embodit.trafficsignals

import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Compass
import io.embodit.trafficsignals.domain.Content
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.HeadPose
import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Levels
import io.embodit.trafficsignals.domain.Question
import io.embodit.trafficsignals.domain.Quat4
import io.embodit.trafficsignals.domain.Side
import io.embodit.trafficsignals.domain.Upright
import io.embodit.trafficsignals.domain.Vec3
import io.embodit.trafficsignals.domain.Yaw
import io.embodit.trafficsignals.domain.nextAskableLane
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeTest {
    @Test
    fun youAreOnTheRightSideOfHim() {
        // design judging.frame: SOUTH -> front, WEST -> left, NORTH -> back, EAST -> right
        assertEquals(Side.FRONT, Facing.SOUTH.you)
        assertEquals(Side.LEFT, Facing.WEST.you)
        assertEquals(Side.BACK, Facing.NORTH.you)
        assertEquals(Side.RIGHT, Facing.EAST.you)
        // facing you, his left hand is on your right (east)
        assertEquals(Compass.EAST, Facing.SOUTH.left)
        assertEquals(Compass.SOUTH, Facing.WEST.left)
    }

    @Test
    fun fiftyCombinationsThirteenOfficial() {
        val all = Judge.all
        assertEquals(50, all.size)
        assertEquals(13, all.count { Judge.verdict(it)!!.official })
    }

    @Test
    fun designTableRows() {
        fun a(g: Gesture, f: Facing, l: Lane) = Judge.verdict(Question(g, f, l))?.answer
        assertEquals(Answer.GO, a(Gesture.STRAIGHT, Facing.EAST, Lane.STRAIGHT))
        assertEquals(Answer.STOP, a(Gesture.STRAIGHT, Facing.EAST, Lane.LEFT))
        assertNull(a(Gesture.STRAIGHT, Facing.WEST, Lane.STRAIGHT)) // U4 not asked
        assertEquals(Answer.WAIT, a(Gesture.LEFT_WAIT, Facing.WEST, Lane.LEFT))
        assertEquals(Answer.GO, a(Gesture.LEFT, Facing.WEST, Lane.LEFT))
        assertEquals(Answer.GO, a(Gesture.RIGHT, Facing.EAST, Lane.RIGHT))
        assertEquals(Answer.STOP, a(Gesture.STOP, Facing.SOUTH, Lane.RIGHT))
        assertNull(a(Gesture.STOP, Facing.NORTH, Lane.RIGHT)) // right lane only in the listed rows
        assertEquals(Answer.LANE, a(Gesture.LANE_CHANGE, Facing.SOUTH, Lane.LEFT))
        assertEquals(Answer.SLOW, a(Gesture.SLOW, Facing.SOUTH, Lane.RIGHT))
        assertEquals(Answer.PULL, a(Gesture.PULL_OVER, Facing.SOUTH, Lane.STRAIGHT))
        assertNull(a(Gesture.PULL_OVER, Facing.EAST, Lane.STRAIGHT))
    }

    @Test
    fun releasesFollowTheOfficer() {
        assertEquals(Compass.SOUTH, Judge.release(Gesture.STRAIGHT, Facing.EAST)!!.approach) // his right = you
        assertEquals(Compass.SOUTH, Judge.release(Gesture.LEFT_WAIT, Facing.WEST)!!.approach) // his left = you
        assertEquals(listOf(Compass.SOUTH), Judge.stopped(Gesture.STOP, Facing.SOUTH))
        assertNull(Judge.release(Gesture.STOP, Facing.SOUTH))
    }

    @Test
    fun levelsHaveTheirSizes() {
        val rnd = Random(1)
        for (l in 1..Levels.COUNT) {
            val qs = Levels.build(l, rnd)
            assertEquals("level $l", Levels.questions[l - 1], qs.size)
            assertTrue(qs.all { Judge.verdict(it) != null })
            assertEquals("level $l distinct", qs.size, qs.distinct().size)
        }
        assertTrue(Levels.build(1, rnd).all { it.facing == Facing.SOUTH })
        assertTrue(Levels.build(2, rnd).all { it.facing == Facing.EAST })
        assertEquals(9, Levels.build(3, rnd).count { it.facing == Facing.WEST })
        // L5: groups of the same signal and facing, lanes change inside a group
        val l5 = Levels.build(5, rnd)
        assertTrue(Levels.sameGroup(l5[0], l5[1]))
        assertFalse(Levels.sameGroup(l5[1], l5[2]))
    }

    @Test
    fun laneSwitchSkipsUnaskedLanes() {
        assertEquals(Lane.LEFT, nextAskableLane(Question(Gesture.STRAIGHT, Facing.EAST, Lane.STRAIGHT)))
        assertEquals(Lane.STRAIGHT, nextAskableLane(Question(Gesture.RIGHT, Facing.EAST, Lane.LEFT)))
        assertNull(nextAskableLane(Question(Gesture.LEFT, Facing.SOUTH, Lane.STRAIGHT)).takeIf { it == Lane.RIGHT })
    }

    @Test
    fun copyIsCompleteAndOriginalStepsCount43() {
        assertEquals(Gesture.TOTAL_STEPS, Gesture.entries.sumOf { it.steps })
        for (g in Gesture.entries) {
            assertEquals(g.name, g.steps, Content.steps[g]!!.size)
            assertNotNull(Content.gestureName[g])
            assertNotNull(Content.mnemonic[g])
        }
    }

    @Test
    fun questionKeyRoundTrips() {
        for (q in Judge.all) assertEquals(q, Question.parse(q.key))
    }
}

/** Placement hard gates (profile D9 / D10): yaw only, upright panels, four directions. */
class PlacementTest {
    private fun headWith(yawDeg: Float, pitchDeg: Float, rollDeg: Float): HeadPose {
        val q = Quat4.axisAngle(Vec3.UP, Math.toRadians(yawDeg.toDouble()).toFloat()) *
            Quat4.axisAngle(Vec3.RIGHT, Math.toRadians(pitchDeg.toDouble()).toFloat()) *
            Quat4.axisAngle(Vec3(0f, 0f, -1f), Math.toRadians(rollDeg.toDouble()).toFloat())
        return HeadPose(Vec3(0.3f, 1.25f, -0.4f), q)
    }

    @Test
    fun pitchAndRollNeverReachThePanels() {
        for ((p, r) in listOf(-30f to 0f, 30f to 0f, 0f to 20f)) {
            val head = headWith(10f, p, r)
            val (origin, yaw, eyeH) = Upright.frame(head, 1.2f)
            assertEquals("yaw from head with pitch $p roll $r", 10.0, Math.toDegrees(yaw.toDouble()), 1.0)
            // a panel 0.95 m ahead, 17° below the eye
            val local = Vec3(0f, eyeH - 0.29f, -0.95f)
            val at = Upright.toStage(origin, yaw, local)
            val rot = Upright.panelRotation(at, head.position)
            val up = rot.rotate(Vec3.UP)
            val angleUp = Math.toDegrees(acos(up.dot(Vec3.UP).coerceIn(-1f, 1f).toDouble()))
            assertTrue("panel up vs world up $angleUp", angleUp < 1.0)
            val toPanel = at - head.position
            val below = Math.toDegrees(atan2(-toPanel.y.toDouble(), kotlin.math.hypot(toPanel.x, toPanel.z).toDouble()))
            assertTrue("below eye $below", below in 10.0..20.0)
            val dist = kotlin.math.hypot(toPanel.x, toPanel.z)
            assertTrue("distance $dist", dist in 0.8f..1.2f)
        }
    }

    @Test
    fun fourDirectionsFaceTheUser() {
        for (yawDeg in listOf(0f, 90f, 180f, 270f)) {
            val head = headWith(yawDeg, 0f, 0f)
            val (origin, yaw, eyeH) = Upright.frame(head, 1.2f)
            val at = Upright.toStage(origin, yaw, Vec3(0f, eyeH - 0.3f, -0.95f))
            val rot = Upright.panelRotation(at, head.position)
            val normal = rot.rotate(Vec3(0f, 0f, 1f)) // the panel's face (+Z) points at the user
            val toUser = (head.position - at).let { Vec3(it.x, 0f, it.z).normalized() }
            val angle = Math.toDegrees(acos(normal.dot(toUser).coerceIn(-1f, 1f).toDouble()))
            assertTrue("yaw $yawDeg: panel faces the user within 10° ($angle)", angle < 10.0)
            // content lands ahead of the head, not behind
            val ahead = Yaw.dir(yaw)
            assertTrue((at - head.position).dot(ahead) > 0.5f)
        }
    }

    @Test
    fun zeroPoseIsNotValid() {
        assertFalse(Upright.valid(HeadPose(Vec3.ZERO, Quat4.IDENTITY)))
        assertFalse(Upright.valid(null))
        assertTrue(Upright.valid(HeadPose(Vec3(0f, 1.3f, 0f), Quat4.IDENTITY)))
    }
}

/** Officer calibration and clip timing (profile D25), read from the baked assets/officer_clips.json. */
class OfficerAssetTest {
    private val clips = JSONObject(File("src/main/assets/officer_clips.json").readText())

    @Test
    fun calibratedToDesignSize() {
        val cal = clips.getJSONObject("calibration")
        val h = cal.getDouble("heightM")
        assertTrue("height $h vs 1.75 m", abs(h - 1.75) / 1.75 < 0.1)
        val feet = cal.getJSONArray("feetCentre")
        // feet centre at the origin (x, y in the horizontal plane) within 1 cm
        assertTrue(abs(feet.getDouble(0)) < 0.01 && abs(feet.getDouble(1)) < 0.01)
        val front = cal.getJSONArray("front")
        // Blender -Y = glTF +Z = the officer's front; within 2°
        val ang = Math.toDegrees(acos((-front.getDouble(1)).coerceIn(-1.0, 1.0)))
        assertTrue("front off by $ang", ang < 2.0)
    }

    @Test
    fun everyGestureHasItsSteps() {
        val c = clips.getJSONObject("clips")
        for (g in Gesture.entries) {
            val steps = c.getJSONObject(g.id).getJSONArray("steps")
            assertEquals(g.id, g.steps, steps.length())
            val dur = c.getJSONObject(g.id).getDouble("duration")
            assertTrue("${g.id} lasts $dur s (design 3 - 7 s)", dur in 3.0..7.5)
        }
    }

    @Test
    fun armAnglesMatchTheOfficialText() {
        val c = clips.getJSONObject("clips")
        fun upper(g: String, k: Int, side: String) = c.getJSONObject(g).getJSONArray("measured").getJSONObject(k).getJSONObject(side).getInt("upperToDown")
        assertEquals(135.0, upper("stop", 0, "l").toDouble(), 5.0) // 与身体成135度
        assertEquals(90.0, upper("straight", 0, "l").toDouble(), 5.0) // 向左平伸与身体成90度
        assertEquals(45.0, upper("leftWait", 0, "l").toDouble(), 5.0) // 与身体成45度
        assertEquals(15.0, upper("leftWait", 1, "l").toDouble(), 5.0) // 与身体成15度
        assertEquals(90.0, upper("laneChange", 0, "r").toDouble(), 5.0)
        assertEquals(45.0, upper("pullOver", 0, "r").toDouble(), 5.0)
    }
}
