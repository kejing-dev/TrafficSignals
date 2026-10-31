package io.embodit.trafficsignals.domain

import java.util.Random

/**
 * The rules of the crossroads (design-package `judging`, `content.levels`). Pure Kotlin, JVM-tested.
 *
 * Compass in intersection space: you always wait at the SOUTH approach (+Z), north is -Z, east is +X. The officer
 * stands on the centre post and faces one of four directions; his "front" vector is the unit vector of that direction.
 */
enum class Compass(val x: Float, val z: Float) {
    NORTH(0f, -1f),
    EAST(1f, 0f),
    SOUTH(0f, 1f),
    WEST(-1f, 0f);

    val vec: Vec3
        get() = Vec3(x, 0f, z)

    /** Turning left from this heading (counter-clockwise seen from above). */
    fun leftOf(): Compass = when (this) { NORTH -> WEST; WEST -> SOUTH; SOUTH -> EAST; EAST -> NORTH }

    fun opposite(): Compass = when (this) { NORTH -> SOUTH; SOUTH -> NORTH; EAST -> WEST; WEST -> EAST }

    /** Yaw that turns local +Z (the officer's front in the GLB) onto this direction, radians, about +Y. */
    val yaw: Float
        get() = kotlin.math.atan2(x, z)
}

/** Where the officer faces (design pivot enum). SOUTH = facing you. */
enum class Facing(val compass: Compass) {
    SOUTH(Compass.SOUTH),
    WEST(Compass.WEST),
    NORTH(Compass.NORTH),
    EAST(Compass.EAST);

    /** The officer's left hand side. Facing south his left hand is east (your right). */
    val left: Compass
        get() = compass.leftOf()

    val right: Compass
        get() = left.opposite()

    val back: Compass
        get() = compass.opposite()

    /** Which side of him an approach is on. */
    fun sideOf(approach: Compass): Side =
        when (approach) {
            compass -> Side.FRONT
            left -> Side.LEFT
            right -> Side.RIGHT
            else -> Side.BACK
        }

    /** Your side (you are the SOUTH approach). */
    val you: Side
        get() = sideOf(Compass.SOUTH)

    fun approachOf(side: Side): Compass = when (side) { Side.FRONT -> compass; Side.LEFT -> left; Side.RIGHT -> right; Side.BACK -> back }

    companion object {
        /** The facing that puts you on [side] of him. */
        fun forYouOn(side: Side): Facing = entries.first { it.you == side }
    }
}

enum class Side { FRONT, LEFT, BACK, RIGHT }

/** Your lane at the south approach (design scene.intersection.lanes: centre-line offset in metres). */
enum class Lane(val x: Float) {
    LEFT(1.5f),
    STRAIGHT(4.5f),
    RIGHT(7.5f);

    fun next(): Lane = entries[(ordinal + 1) % entries.size]
}

enum class Gesture(val id: String, val steps: Int) {
    STOP("stop", 2),
    STRAIGHT("straight", 7),
    LEFT("left", 6),
    LEFT_WAIT("leftWait", 5),
    RIGHT("right", 6),
    LANE_CHANGE("laneChange", 5),
    SLOW("slow", 5),
    PULL_OVER("pullOver", 7);

    companion object {
        fun byId(id: String): Gesture? = entries.firstOrNull { it.id.equals(id, true) || it.name.equals(id, true) }

        const val TOTAL_STEPS = 43
    }
}

enum class Answer(val id: String) {
    GO("go"),
    STOP("stop"),
    WAIT("wait"),
    SLOW("slow"),
    LANE("lane"),
    PULL("pull");

    companion object {
        fun byId(id: String): Answer? = entries.firstOrNull { it.id.equals(id, true) || it.name.equals(id, true) }
    }
}

/** How the turn of one released approach moves (for the flow arrows). */
enum class Move { LEFT, STRAIGHT, RIGHT, WAIT }

/** One question: the gesture, where he faces, your lane. */
data class Question(val gesture: Gesture, val facing: Facing, val lane: Lane) {
    val key: String
        get() = "${gesture.id},${facing.name},${lane.name}"

    companion object {
        fun parse(s: String): Question? {
            val p = s.split(',', ' ', ';').filter { it.isNotBlank() }
            if (p.size < 3) return null
            val g = Gesture.byId(p[0]) ?: return null
            val f = Facing.entries.firstOrNull { it.name.equals(p[1], true) } ?: return null
            val l = Lane.entries.firstOrNull { it.name.equals(p[2], true) } ?: return null
            return Question(g, f, l)
        }
    }
}

data class Verdict(val answer: Answer, val official: Boolean)

/** The released traffic of a gesture, as approaches + moves (what the green arrows show). */
data class Release(val approach: Compass, val moves: List<Move>)

object Judge {
    private val TWO = listOf(Lane.LEFT, Lane.STRAIGHT)
    private val ALL = Lane.entries

    /** The correct answer, or null when the combination is not asked (design judging.excluded). */
    fun verdict(q: Question): Verdict? {
        val side = q.facing.you
        val lane = q.lane
        return when (q.gesture) {
            Gesture.STOP ->
                when {
                    side == Side.FRONT -> Verdict(Answer.STOP, true)
                    lane in TWO -> Verdict(Answer.STOP, false)
                    else -> null
                }
            Gesture.STRAIGHT ->
                when {
                    side == Side.RIGHT && lane == Lane.STRAIGHT -> Verdict(Answer.GO, true)
                    side == Side.RIGHT && lane == Lane.LEFT -> Verdict(Answer.STOP, false)
                    (side == Side.FRONT || side == Side.BACK) && lane in TWO -> Verdict(Answer.STOP, false)
                    side == Side.LEFT && lane == Lane.LEFT -> Verdict(Answer.STOP, false)
                    else -> null
                }
            Gesture.LEFT ->
                when {
                    side == Side.LEFT && lane == Lane.LEFT -> Verdict(Answer.GO, true)
                    side == Side.LEFT && lane == Lane.STRAIGHT -> Verdict(Answer.STOP, false)
                    side != Side.LEFT && lane in TWO -> Verdict(Answer.STOP, false)
                    else -> null
                }
            Gesture.LEFT_WAIT ->
                when {
                    side == Side.LEFT && lane == Lane.LEFT -> Verdict(Answer.WAIT, true)
                    side == Side.LEFT && lane == Lane.STRAIGHT -> Verdict(Answer.STOP, false)
                    side != Side.LEFT && lane in TWO -> Verdict(Answer.STOP, false)
                    else -> null
                }
            Gesture.RIGHT ->
                when {
                    side == Side.RIGHT && lane == Lane.RIGHT -> Verdict(Answer.GO, true)
                    side == Side.RIGHT && lane in TWO -> Verdict(Answer.STOP, false)
                    side != Side.RIGHT && lane in TWO -> Verdict(Answer.STOP, false)
                    else -> null
                }
            Gesture.LANE_CHANGE -> if (side == Side.FRONT) Verdict(Answer.LANE, true) else null
            Gesture.SLOW -> if (side == Side.FRONT) Verdict(Answer.SLOW, false) else null
            Gesture.PULL_OVER -> if (side == Side.FRONT) Verdict(Answer.PULL, true) else null
        }
    }

    /** Every askable question (50). */
    val all: List<Question> by lazy {
        Gesture.entries.flatMap { g -> Facing.entries.flatMap { f -> ALL.map { l -> Question(g, f, l) } } }.filter { verdict(it) != null }
    }

    /** Who the gesture lets go (null side = nobody is released, e.g. stop). */
    fun release(g: Gesture, facing: Facing): Release? =
        when (g) {
            Gesture.STOP -> null
            Gesture.STRAIGHT -> Release(facing.right, listOf(Move.STRAIGHT))
            Gesture.LEFT -> Release(facing.left, listOf(Move.LEFT))
            Gesture.LEFT_WAIT -> Release(facing.left, listOf(Move.WAIT))
            Gesture.RIGHT -> Release(facing.right, listOf(Move.RIGHT))
            Gesture.LANE_CHANGE, Gesture.SLOW, Gesture.PULL_OVER -> null
        }

    /** Approaches that get a red bar at their stop line. */
    fun stopped(g: Gesture, facing: Facing): List<Compass> =
        when (g) {
            Gesture.STOP -> listOf(facing.compass)
            Gesture.STRAIGHT, Gesture.LEFT, Gesture.LEFT_WAIT, Gesture.RIGHT -> {
                val rel = release(g, facing)!!.approach
                Compass.entries.filter { it != rel }
            }
            else -> emptyList()
        }

    /** The move of your route in your lane. */
    fun yourMove(lane: Lane): Move = when (lane) { Lane.LEFT -> Move.LEFT; Lane.STRAIGHT -> Move.STRAIGHT; Lane.RIGHT -> Move.RIGHT }
}

/** The six levels (design content.levels) plus the mistakes drill. */
object Levels {
    const val COUNT = 6
    val questions = intArrayOf(10, 10, 10, 12, 9, 18)
    const val TIMED_LEVEL = 6
    const val LANE_LEVEL = 5
    const val TIMED_SEC = 5f
    const val DRILL_MAX = 12

    fun build(level: Int, rnd: Random): List<Question> {
        val all = Judge.all
        return when (level) {
            1 -> all.filter { it.facing == Facing.SOUTH && it.gesture in setOf(Gesture.STOP, Gesture.LANE_CHANGE, Gesture.SLOW, Gesture.PULL_OVER) }.shuffled(rnd).take(10)
            2 -> all.filter { it.facing == Facing.EAST }.shuffled(rnd).take(10)
            3 -> {
                val west = all.filter { it.facing == Facing.WEST }.shuffled(rnd)
                val control = Question(Gesture.STOP, Facing.SOUTH, Lane.entries[rnd.nextInt(3)])
                (west + control).take(10).shuffled(rnd)
            }
            4 -> all.filter { it.gesture in setOf(Gesture.STOP, Gesture.STRAIGHT, Gesture.LEFT, Gesture.LEFT_WAIT, Gesture.RIGHT) }.shuffled(rnd).take(12)
            5 ->
                listOf(
                    Question(Gesture.STRAIGHT, Facing.EAST, Lane.LEFT), Question(Gesture.STRAIGHT, Facing.EAST, Lane.STRAIGHT),
                    Question(Gesture.LEFT, Facing.WEST, Lane.LEFT), Question(Gesture.LEFT, Facing.WEST, Lane.STRAIGHT),
                    Question(Gesture.LEFT_WAIT, Facing.WEST, Lane.LEFT), Question(Gesture.LEFT_WAIT, Facing.WEST, Lane.STRAIGHT),
                    Question(Gesture.RIGHT, Facing.EAST, Lane.LEFT), Question(Gesture.RIGHT, Facing.EAST, Lane.STRAIGHT), Question(Gesture.RIGHT, Facing.EAST, Lane.RIGHT),
                )
            6 -> all.shuffled(rnd).take(18)
            else -> emptyList()
        }
    }

    /** In level 5 a group continues (same gesture and facing) when the next question only changes the lane. */
    fun sameGroup(a: Question?, b: Question?): Boolean = a != null && b != null && a.gesture == b.gesture && a.facing == b.facing && a.lane != b.lane

    fun drill(mistakes: List<Mistake>, rnd: Random): List<Question> = mistakes.map { it.question }.distinct().shuffled(rnd).take(DRILL_MAX)

    /** The recommended next level: the first one without a best score, else the weakest. */
    fun recommended(best: Map<Int, Int>): Int {
        for (l in 1..COUNT) if (best[l] == null) return l
        return (1..COUNT).minByOrNull { (best[it] ?: 0).toFloat() / questions[it - 1] } ?: 1
    }
}

data class Mistake(val question: Question, val yours: Answer?, val at: Long)

/** The officer's next lane in "switch lane and look again": the next lane with an askable question. */
fun nextAskableLane(q: Question): Lane? {
    var l = q.lane.next()
    repeat(2) {
        if (Judge.verdict(q.copy(lane = l)) != null) return l
        l = l.next()
    }
    return null
}
