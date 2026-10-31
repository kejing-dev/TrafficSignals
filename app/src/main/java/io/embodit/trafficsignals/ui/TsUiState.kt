package io.embodit.trafficsignals.ui

import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Mistake
import io.embodit.trafficsignals.domain.Question
import io.embodit.trafficsignals.domain.Verdict

/** Screens (design-package states; the S-number each one maps to is in [TsUiState.designState]). */
enum class Screen { STARTING, HOME, LESSONS, LEVELS, REVIEW, SETTINGS, LESSON, INTRO, QUIZ, RESULT, BLOCKED }

/** Phase of a quiz question (S6 / S7 / S8 / S9 / S14, plus the unscored demo verdict after a lane switch). */
enum class Phase { WATCH, CORRECT, WRONG, TIMEOUT, SWITCHING, DEMO }

enum class Dialog { NONE, LEAVE, EXIT, RESET }

data class Outcome(val question: Question, val yours: Answer?, val correct: Answer, val hinted: Boolean) {
    val ok: Boolean
        get() = yours == correct
}

data class TsUiState(
    val screen: Screen = Screen.STARTING,
    val dialog: Dialog = Dialog.NONE,
    val paused: Boolean = false,
    val zh: Boolean = true,
    val sound: Boolean = true,
    val big: Boolean = false,
    val slow: Boolean = false,
    // lesson (S3 / S4): 0 = orientation, 1..8 = gestures
    val lesson: Int = 1,
    val lessonStep: Int = 0,
    val lessonPlaying: Boolean = true,
    val lessonAngle: Facing = Facing.SOUTH,
    /** "Show the steps" opened from a verdict (I5): the lesson panel stands in for the verdict card. */
    val breakdown: Boolean = false,
    // quiz
    val level: Int = 1, // 0 = mistakes drill
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val phase: Phase = Phase.WATCH,
    val lane: Lane = Lane.STRAIGHT,
    val yours: Answer? = null,
    val hint: Boolean = false,
    val outcomes: List<Outcome> = emptyList(),
    /** Bumped whenever the officer should (re)play the current gesture. */
    val gestureEpoch: Int = 0,
    /** The demo lane switch of S9 (not scored). */
    val demo: Boolean = false,
    // progress
    val best: Map<Int, Int> = emptyMap(),
    val mistakes: List<Mistake> = emptyList(),
    val learned: Set<Int> = emptySet(),
    /** Bumped by [回正]: the scene places itself again from the current head pose. */
    val placeEpoch: Int = 0,
    val blockReason: String? = null,
    val sceneReady: Boolean = false,
    // debug seams (QA): hide every panel, side camera = officer turned 90°
    val panelsHidden: Boolean = false,
    val camSide: Boolean = false,
    val freeze: Boolean = false,
    val debugName: String? = null,
) {
    val question: Question?
        get() = questions.getOrNull(index)

    val verdict: Verdict?
        get() = question?.let { io.embodit.trafficsignals.domain.Judge.verdict(it.copy(lane = lane)) }

    val inQuiz: Boolean
        get() = screen == Screen.QUIZ || screen == Screen.INTRO

    val judged: Boolean
        get() = screen == Screen.QUIZ && phase in setOf(Phase.CORRECT, Phase.WRONG, Phase.TIMEOUT, Phase.DEMO)

    val timed: Boolean
        get() = level == io.embodit.trafficsignals.domain.Levels.TIMED_LEVEL

    val lessonGesture: Gesture?
        get() = if (lesson in 1..8) Gesture.entries[lesson - 1] else null

    /** The design state id of what is on screen (logged as `STATE`). */
    fun designState(): String {
        debugName?.let { return it }
        val base =
            when (screen) {
                Screen.STARTING -> "S1"
                Screen.HOME -> "S2"
                Screen.LESSONS -> "S16"
                Screen.LEVELS -> "S17"
                Screen.REVIEW -> "S11"
                Screen.SETTINGS -> "SET"
                Screen.LESSON -> if (lessonPlaying) "S3" else "S4"
                Screen.INTRO -> "S5"
                Screen.QUIZ ->
                    when (phase) {
                        Phase.WATCH -> "S6"
                        Phase.CORRECT -> "S7"
                        Phase.WRONG -> "S8"
                        Phase.SWITCHING -> "S9"
                        Phase.TIMEOUT -> "S14"
                        Phase.DEMO -> "S9d"
                    }
                Screen.RESULT -> "S10"
                Screen.BLOCKED -> "S13"
            }
        val extra = buildString {
            if (dialog == Dialog.LEAVE) append("+S12")
            if (dialog == Dialog.EXIT) append("+EXIT")
            if (dialog == Dialog.RESET) append("+RESET")
            if (paused) append("+S18")
            if (breakdown) append("+S4")
            if (!zh) append("+S15")
        }
        return base + extra
    }
}
