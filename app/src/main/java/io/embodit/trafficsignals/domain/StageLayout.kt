package io.embodit.trafficsignals.domain

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2

/**
 * Where everything stands, pure so the JVM tests can walk it (profile D14, D41; v0.1.2).
 *
 * Driver frame: road under your eye, -Z ahead along the road, +X right. The system opens the main window on your line
 * of sight ~1.75 m away and nothing can move it (factory native-window-move §4), so the road turns instead: at
 * placement the driver frame is turned left of your line of sight by [seatYaw], which leaves the officer
 * [OFFICER_LEFT_DEG] to the left of the window's centre in every lane. The eye sits on the turning axis: a lane or seat change
 * never moves you.
 *
 * The officer is ALWAYS on a post: the centre post of the crossroads ([postFeet], 14 m along the road) or the lesson
 * post ([LESSON_SPOT], 6 m). He is placed there before the scene is shown, so no frame has him at the driver origin,
 * which is where you sit (2026-11-05 headset: "别把交警模型放我身上").
 */
object StageLayout {
    const val EYE_H = 1.2f
    const val EYE_TO_CENTRE = 14.0f
    const val EYE_LEFT_OF_LANE = 0.4f
    const val POST_H = 0.40f
    const val OFFICER_H = 1.75f

    /** His arm reach from his axis (baked GLB: arms straight out). */
    const val ARM_REACH = 0.85f
    const val BIG = 1.5f
    const val MIN_CLEARANCE_M = 1.5f

    /** Main window (system Planar): size in window dp, distance on the line of sight. */
    const val WINDOW_W = 760f
    const val WINDOW_H = 560f
    const val WINDOW_DIST = 1.75f
    /** The system dot / × / handle / − row under the window (factory feedback: 77 drawn dp, kept 80). */
    const val WINDOW_CAPTION_DP = 80f
    /** The native TabBar capsule above the window (not wider than the window). */
    const val WINDOW_TABBAR_DP = 88f
    /**
     * Metres per window dp AS DRAWN at 1.75 m. LocalPhysicalLengthConverter says 0.0008 but the system draws the window
     * 2.2 times that (factory feedback, sign-hands 0.00173; here: 960 dp spanned 51.6° = 0.00176). Avoidance uses this one.
     */
    const val WINDOW_DRAWN_M = 0.00173f

    /** The officer's centre seen from your line of sight (the window's centre), degrees to the left; ×1.5 needs more room. */
    const val OFFICER_LEFT_DEG = 36f
    const val OFFICER_LEFT_BIG_DEG = 39f

    /** The lesson post stands 6 m out, where he spans more: this much further left than the crossroads post. */
    const val LESSON_EXTRA_LEFT_DEG = 4f

    /** Bonnet top below the eye: the window's system row (down to -19.6°) stays above its far edge (-20.0°). */
    const val HOOD_DROP = 0.60f

    const val LESSON_DIST = 6f

    /** Junction centre in the driver frame for a lane (design scene.intersection.you). */
    fun junctionLocal(laneX: Float): Vec3 = Vec3(-(laneX - EYE_LEFT_OF_LANE), 0f, -EYE_TO_CENTRE)

    /** His feet on the crossroads post. */
    fun postFeet(laneX: Float): Vec3 = junctionLocal(laneX) + Vec3(0f, POST_H, 0f)

    /** Bearing from your seat along the road, radians, + = left of the road's ahead. */
    private fun bearing(p: Vec3): Float = atan2(-p.x, -p.z)

    /** The foot of the lesson post: 6 m out, [LESSON_EXTRA_LEFT_DEG] left of the crossroads post seen from the straight lane (where lessons happen). */
    val LESSON_SPOT: Vec3 = run {
        val b = bearing(junctionLocal(Lane.STRAIGHT.x)) + Math.toRadians(LESSON_EXTRA_LEFT_DEG.toDouble()).toFloat()
        Vec3(-LESSON_DIST * kotlin.math.sin(b), 0f, -LESSON_DIST * kotlin.math.cos(b))
    }

    fun lessonFeet(): Vec3 = LESSON_SPOT + Vec3(0f, POST_H, 0f)

    /** Where he stands on a screen: lessons on the near post, everything else (opening, reset, home, quiz) on the crossroads post. */
    fun officerFeet(lessonPost: Boolean, laneX: Float): Vec3 = if (lessonPost) lessonFeet() else postFeet(laneX)

    /** The opening spot, set when the scene is built, before anything is shown. */
    fun openingFeet(): Vec3 = postFeet(Lane.STRAIGHT.x)

    /** How far the road's ahead is turned left of your line of sight in this lane, radians. */
    fun seatYaw(laneX: Float, big: Boolean): Float =
        Math.toRadians((if (big) OFFICER_LEFT_BIG_DEG else OFFICER_LEFT_DEG).toDouble()).toFloat() - bearing(junctionLocal(laneX))

    /** The driver frame's yaw in Stage space for a line-of-sight yaw. */
    fun driverYaw(sightYaw: Float, laneX: Float, big: Boolean): Float = sightYaw + seatYaw(laneX, big)

    /** A point on the line of sight frame (x right of the sight line) from the driver frame. */
    fun inSightFrame(local: Vec3, laneX: Float, big: Boolean): Vec3 = Quat4.axisAngle(Vec3.UP, seatYaw(laneX, big)).rotate(Vec3(local.x, local.y - EYE_H, local.z))

    /** Half-width of the window's outer frame as an angle from the line of sight, degrees. */
    fun windowHalfDeg(): Float = Math.toDegrees(atan((WINDOW_W * WINDOW_DRAWN_M / 2 / WINDOW_DIST).toDouble())).toFloat()

    /** How far the window's system row reaches below the line of sight, degrees. */
    fun windowBottomDeg(): Float = Math.toDegrees(atan(((WINDOW_H / 2 + WINDOW_CAPTION_DP) * WINDOW_DRAWN_M / WINDOW_DIST).toDouble())).toFloat()

    /** Clear angle between the window's left edge and his nearest reach, degrees (profile D41: at least 10). */
    fun windowGapDeg(feet: Vec3, laneX: Float, big: Boolean): Float {
        val scale = if (big) BIG else 1f
        val p = inSightFrame(feet, laneX, big)
        val range = kotlin.math.hypot(p.x, p.z)
        val centre = Math.toDegrees(atan2(-p.x, -p.z).toDouble()).toFloat() // + = left of the sight line
        val reach = Math.toDegrees(atan((ARM_REACH * scale / range).toDouble())).toFloat()
        return centre - reach - windowHalfDeg()
    }

    /** The "open panel" key / the look-level hint in the driver-free sight frame: [dist] m out, [belowDeg] below the eye. */
    fun chipAt(eye: Vec3, sightYaw: Float, dist: Float, belowDeg: Float): Vec3 {
        val d = Yaw.dir(sightYaw)
        val drop = dist * kotlin.math.tan(Math.toRadians(belowDeg.toDouble())).toFloat()
        return Vec3(eye.x + d.x * dist, eye.y - drop, eye.z + d.z * dist)
    }
}

/**
 * Places the driver frame from the head (design container.placement, profile D1 / D9 / D29), pure: the scene feeds time
 * and the head (position, line-of-sight yaw) every frame.
 *
 *  request (start / [回正] / window reopened somewhere else)
 *    -> sample [windowMs] (median), first placement
 *    -> settle: for [settleMs] keep following the head (tracking settling; you looked at the button when you pressed it,
 *       D29: the seat ends where you end up facing, not where the button was)
 *    -> locked. Once within 10 s of the first placement it re-places if the head is > 70° or > 1.5 m away (D1).
 */
class SeatPlacer(
    private val windowMs: Long = 500L,
    private val timeoutMs: Long = 4_000L,
    private val settleMs: Long = 2_000L,
) {
    class Spot(val eye: Vec3, val yaw: Float, val why: String, /** a visible jump: fade in again */ val jump: Boolean)

    var placed = false
        private set
    var eye = Vec3.ZERO
        private set
    var yaw = 0f
        private set
    private var pending: String? = null
    private var startMs = 0L
    private val samples = ArrayList<Pair<Vec3, Float>>()
    private var settleUntil = 0L
    private var firstAt = 0L
    private var replacedOnce = false

    val waiting: Boolean
        get() = pending != null

    fun request(why: String, now: Long) {
        pending = why
        startMs = now
        samples.clear()
    }

    /** The window was just opened on the current line of sight: re-place if the seat is off it by more than 5°. */
    fun windowOpened(now: Long, sightYaw: Float) {
        if (placed && pending == null && abs(Yaw.wrap(sightYaw - yaw)) > WINDOW_OFF_RAD) request("window", now)
    }

    /** Returns a new place when one was decided this frame. [head] = null: no valid head pose this frame. */
    fun step(now: Long, head: Pair<Vec3, Float>?): Spot? {
        val why = pending
        if (why != null) {
            if (head != null) samples += head
            val enough = now - startMs >= windowMs && samples.size >= 3
            val timedOut = now - startMs >= timeoutMs
            if (!enough && !timedOut) return null
            if (samples.isEmpty()) {
                startMs = now // no valid head yet: nothing is shown, keep waiting
                return null
            }
            val spot = median(samples)
            pending = null
            samples.clear()
            val jump = placed
            if (!placed) firstAt = now
            placed = true
            settleUntil = now + settleMs
            eye = spot.first
            yaw = spot.second
            return Spot(eye, yaw, why, jump)
        }
        if (!placed || head == null) return null
        if (now < settleUntil) {
            eye = head.first
            yaw = head.second
            return Spot(eye, yaw, "settle", false)
        }
        if (!replacedOnce && now - firstAt < 10_000L) {
            val off = kotlin.math.hypot(head.first.x - eye.x, head.first.z - eye.z)
            val turn = abs(Yaw.wrap(head.second - yaw))
            if (off > 1.5f || turn > AGAIN_RAD) {
                replacedOnce = true
                eye = head.first
                yaw = head.second
                return Spot(eye, yaw, "auto-again", true)
            }
        }
        return null
    }

    private fun median(s: List<Pair<Vec3, Float>>): Pair<Vec3, Float> {
        val xs = s.map { it.first.x }.sorted()
        val ys = s.map { it.first.y }.sorted()
        val zs = s.map { it.first.z }.sorted()
        val ref = s.last().second
        val yaws = s.map { ref + Yaw.wrap(it.second - ref) }.sorted()
        return Vec3(xs[xs.size / 2], ys[ys.size / 2], zs[zs.size / 2]) to Yaw.wrap(yaws[yaws.size / 2])
    }

    private companion object {
        val WINDOW_OFF_RAD = Math.toRadians(5.0).toFloat()
        val AGAIN_RAD = Math.toRadians(70.0).toFloat()
    }
}
