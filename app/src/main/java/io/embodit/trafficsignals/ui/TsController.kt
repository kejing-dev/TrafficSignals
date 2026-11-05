package io.embodit.trafficsignals.ui

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.mutableFloatStateOf
import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Levels
import io.embodit.trafficsignals.domain.Mistake
import io.embodit.trafficsignals.domain.Question
import io.embodit.trafficsignals.domain.nextAskableLane
import io.embodit.trafficsignals.platform.Sound
import io.embodit.trafficsignals.platform.Store
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.window.TAB_HOME
import io.embodit.trafficsignals.ui.window.TAB_LESSONS
import io.embodit.trafficsignals.ui.window.TAB_LEVELS
import io.embodit.trafficsignals.ui.window.TAB_REVIEW
import io.embodit.trafficsignals.ui.window.TAB_SETTINGS
import io.embodit.trafficsignals.ui.window.WindowCtl
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface TsEvent {
    data object Home : TsEvent
    /** A TabBar item (ui.window.TAB_*). */
    data class Tab(val id: String) : TsEvent
    data object OpenLessons : TsEvent
    data object OpenLevels : TsEvent
    data object OpenReview : TsEvent
    data object OpenSettings : TsEvent
    data object Back : TsEvent
    data class OpenLesson(val lesson: Int) : TsEvent
    data object LessonPlayPause : TsEvent
    data class LessonStep(val step: Int) : TsEvent
    data class LessonStepBy(val delta: Int) : TsEvent
    data class LessonAngle(val facing: Facing) : TsEvent
    data object NextLesson : TsEvent
    data class OpenLevel(val level: Int) : TsEvent
    data object Drill : TsEvent
    data object ClearMistakes : TsEvent
    data object Start : TsEvent
    data class Pick(val answer: Answer) : TsEvent
    data object Replay : TsEvent
    data object Hint : TsEvent
    data object NextQuestion : TsEvent
    data object SwitchLane : TsEvent
    data object Breakdown : TsEvent
    data object CloseBreakdown : TsEvent
    data object AskLeave : TsEvent
    data object Leave : TsEvent
    data object CloseDialog : TsEvent
    data object Again : TsEvent
    data object NextLevel : TsEvent
    data class Lang(val zh: Boolean) : TsEvent
    data class SoundOn(val on: Boolean) : TsEvent
    data class Big(val big: Boolean) : TsEvent
    data class Slow(val slow: Boolean) : TsEvent
    data object Recenter : TsEvent
    data object AskExit : TsEvent
    data object Exit : TsEvent
    data object AskReset : TsEvent
    data object ResetProgress : TsEvent
    data object Resume : TsEvent
    data class Background(val inBackground: Boolean) : TsEvent
    data object SceneReady : TsEvent
    data class Blocked(val reason: String?) : TsEvent
    /** From the scene: the officer finished the current gesture once. */
    data object GestureDone : TsEvent
    /** From the scene: a lane fade finished; the officer may start again. */
    data object LaneArrived : TsEvent
    /** The lesson's frozen step reported by the scene while playing. */
    data class LessonAt(val step: Int) : TsEvent
}

/**
 * All behaviour (design-package states / interactions). One direction of data: panel taps, the scene and the debug
 * receiver send [TsEvent]s here, [state] goes to the panels and the scene. Debug commands use these same events
 * (factory rule: no shortcuts that write the state directly).
 */
object TsController {
    private val _state = MutableStateFlow(TsUiState())
    val state: StateFlow<TsUiState> = _state

    /** L6 countdown 1 → 0 (drawn every frame by P2; not part of the state to keep recompositions local). */
    val countdown = mutableFloatStateOf(1f)

    private var store: Store? = null
    val sound = Sound()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var deadlineLeftMs = 0L
    private var deadlineAtMs = 0L
    private var rnd = Random(SystemClock.elapsedRealtime())
    private var started = false
    var activity: Activity? = null

    fun start(context: Context) {
        if (started) return
        started = true
        val s = Store(context.applicationContext)
        store = s
        sound.enabled = s.sound
        scope.launch(Dispatchers.Default) { sound.warm() }
        _state.value = TsUiState(zh = s.zh, sound = s.sound, big = s.big, slow = s.slow, best = s.best, mistakes = s.mistakes, learned = s.learned)
        log("start")
    }

    private fun set(why: String, f: (TsUiState) -> TsUiState) {
        val before = _state.value
        val after = f(before)
        if (after == before) return
        _state.value = after
        if (after.designState() != before.designState()) log(why)
    }

    private fun log(why: String) {
        val s = _state.value
        TsLog.i("STATE ${s.designState()} why=$why q=${s.question?.key ?: "-"} lane=${s.lane} idx=${s.index}/${s.questions.size} lesson=${s.lesson}:${s.lessonStep}")
    }

    fun designState(): String = _state.value.designState()

    fun onEvent(e: TsEvent) {
        val s = _state.value
        if (e !is TsEvent.LessonAt && e !is TsEvent.GestureDone) TsLog.i("EVENT $e")
        when (e) {
            TsEvent.Home -> if (s.inQuiz && s.screen == Screen.QUIZ) askLeave(null) else goHome("home")
            is TsEvent.Tab -> tab(e.id)
            TsEvent.OpenLessons -> goTo(Screen.LESSONS, "lessons")
            TsEvent.OpenLevels -> goTo(Screen.LEVELS, "levels")
            TsEvent.OpenReview -> goTo(Screen.REVIEW, "review")
            TsEvent.OpenSettings -> goTo(Screen.SETTINGS, "settings")
            TsEvent.Back ->
                when (s.screen) {
                    Screen.LESSON -> if (s.breakdown) onEvent(TsEvent.CloseBreakdown) else set("back") { it.copy(screen = Screen.LESSONS) }
                    else -> goHome("back")
                }
            is TsEvent.OpenLesson -> {
                val l = e.lesson.coerceIn(0, 8)
                val learned = s.learned + l
                store?.learned = learned
                set("lesson") { it.copy(screen = Screen.LESSON, lesson = l, lessonStep = 0, lessonPlaying = true, lessonAngle = Facing.SOUTH, breakdown = false, learned = learned, gestureEpoch = it.gestureEpoch + 1, dialog = Dialog.NONE) }
            }
            TsEvent.LessonPlayPause -> set("play") { it.copy(lessonPlaying = !it.lessonPlaying, gestureEpoch = if (!it.lessonPlaying) it.gestureEpoch + 1 else it.gestureEpoch) }
            is TsEvent.LessonStep -> set("step") { it.copy(lessonStep = e.step.coerceIn(0, stepCount(it) - 1), lessonPlaying = false) }
            is TsEvent.LessonStepBy -> set("step") { it.copy(lessonStep = (it.lessonStep + e.delta).coerceIn(0, stepCount(it) - 1), lessonPlaying = false) }
            is TsEvent.LessonAngle -> set("angle") { it.copy(lessonAngle = e.facing) }
            TsEvent.NextLesson -> onEvent(TsEvent.OpenLesson(if (s.lesson >= 8) 0 else s.lesson + 1))
            is TsEvent.LessonAt -> if (s.screen == Screen.LESSON && s.lessonPlaying && s.lessonStep != e.step) _state.value = s.copy(lessonStep = e.step)
            is TsEvent.OpenLevel -> openLevel(e.level, Levels.build(e.level, rnd))
            TsEvent.Drill -> if (s.mistakes.isNotEmpty()) openLevel(0, Levels.drill(s.mistakes, rnd))
            TsEvent.ClearMistakes -> {
                store?.mistakes = emptyList()
                set("clear") { it.copy(mistakes = emptyList()) }
            }
            TsEvent.Start -> if (s.screen == Screen.INTRO) beginQuestion(0, "start")
            is TsEvent.Pick -> pick(e.answer)
            TsEvent.Replay -> if (s.screen == Screen.QUIZ) set("replay") { it.copy(gestureEpoch = it.gestureEpoch + 1) }
            TsEvent.Hint -> if (s.screen == Screen.QUIZ && s.phase == Phase.WATCH) set("hint") { it.copy(hint = !it.hint) }
            TsEvent.NextQuestion -> nextQuestion()
            TsEvent.SwitchLane -> {
                val q = s.question ?: return
                val lane = nextAskableLane(q.copy(lane = s.lane)) ?: return
                set("switch") { it.copy(phase = Phase.SWITCHING, lane = lane, demo = true, breakdown = false) }
            }
            TsEvent.LaneArrived -> if (s.phase == Phase.SWITCHING) set("arrived") { it.copy(phase = Phase.WATCH, yours = null, hint = false, gestureEpoch = it.gestureEpoch + 1) }
            TsEvent.Breakdown -> {
                val g = s.question?.gesture ?: return
                set("breakdown") { it.copy(breakdown = true, lesson = g.ordinal + 1, lessonStep = 0, lessonPlaying = false, lessonAngle = it.question!!.facing) }
            }
            TsEvent.CloseBreakdown -> set("breakdown-close") { it.copy(breakdown = false, gestureEpoch = it.gestureEpoch + 1) }
            TsEvent.AskLeave -> askLeave(null)
            TsEvent.Leave -> tabNow(s.leaveTo ?: TAB_HOME, "leave")
            TsEvent.CloseDialog -> {
                set("dialog-close") { it.copy(dialog = Dialog.NONE) }
                if (!s.paused) resumeTimer()
            }
            TsEvent.Again -> openLevel(s.level, if (s.level == 0) Levels.drill(s.mistakes, rnd) else Levels.build(s.level, rnd))
            TsEvent.NextLevel -> if (s.level in 1 until Levels.COUNT) onEvent(TsEvent.OpenLevel(s.level + 1)) else onEvent(TsEvent.OpenReview)
            is TsEvent.Lang -> {
                store?.zh = e.zh
                set("lang") { it.copy(zh = e.zh) }
            }
            is TsEvent.SoundOn -> {
                store?.sound = e.on
                sound.enabled = e.on
                TsLog.i("SOUND on=${e.on}")
                set("sound") { it.copy(sound = e.on) }
            }
            is TsEvent.Big -> {
                store?.big = e.big
                set("big") { it.copy(big = e.big) }
            }
            is TsEvent.Slow -> {
                store?.slow = e.slow
                set("slow") { it.copy(slow = e.slow, gestureEpoch = it.gestureEpoch + 1) }
            }
            TsEvent.Recenter -> set("recenter") { it.copy(placeEpoch = it.placeEpoch + 1) }
            TsEvent.AskExit -> set("exit-ask") { it.copy(dialog = Dialog.EXIT) }
            TsEvent.AskReset -> set("reset-ask") { it.copy(dialog = Dialog.RESET) }
            TsEvent.ResetProgress -> {
                store?.resetProgress()
                set("reset") { it.copy(dialog = Dialog.NONE, best = emptyMap(), mistakes = emptyList(), learned = emptySet()) }
            }
            TsEvent.Exit -> exit()
            TsEvent.Resume -> {
                // the officer starts the signal again (it was frozen while away); the L6 clock continues
                set("resume") { it.copy(paused = false, gestureEpoch = if (it.screen == Screen.QUIZ && it.phase == Phase.WATCH) it.gestureEpoch + 1 else it.gestureEpoch) }
                resumeTimer()
            }
            is TsEvent.Background ->
                if (e.inBackground) {
                    pauseTimer()
                    if (s.screen != Screen.STARTING) set("background") { it.copy(paused = true) }
                }
            TsEvent.SceneReady -> if (s.screen == Screen.STARTING) set("ready") { it.copy(screen = Screen.HOME, sceneReady = true) } else set("ready") { it.copy(sceneReady = true) }
            is TsEvent.Blocked -> set("blocked") { it.copy(screen = Screen.BLOCKED, blockReason = e.reason, dialog = Dialog.NONE) }
            TsEvent.GestureDone -> gestureDone()
        }
    }

    private fun stepCount(s: TsUiState): Int = s.lessonGesture?.steps ?: 4

    private fun goHome(why: String) = goTo(Screen.HOME, why)

    /** Leaves any level: the clock, the hum and the question go, the seat returns to the straight lane. */
    private fun goTo(screen: Screen, why: String) {
        stopTimer()
        set(why) { it.copy(screen = screen, dialog = Dialog.NONE, leaveTo = null, paused = false, breakdown = false, phase = Phase.WATCH, demo = false, hint = false, lane = Lane.STRAIGHT, questions = emptyList(), index = 0, outcomes = emptyList()) }
    }

    /** TabBar tap. During a question it asks first (S12), like [主页]; a lit tab in a sub-page goes back to its list. */
    private fun tab(id: String) {
        val s = _state.value
        if (s.screen == Screen.BLOCKED) return
        if (s.screen == Screen.QUIZ) {
            askLeave(id)
            return
        }
        tabNow(id, "tab")
    }

    /** S12: the L6 clock waits while the page is the question "leave?" (the answers are not on screen). */
    private fun askLeave(to: String?) {
        pauseTimer()
        set("leave-ask") { it.copy(dialog = Dialog.LEAVE, leaveTo = to) }
    }

    private fun tabNow(id: String, why: String) =
        goTo(
            when (id) {
                TAB_LESSONS -> Screen.LESSONS
                TAB_LEVELS -> Screen.LEVELS
                TAB_REVIEW -> Screen.REVIEW
                TAB_SETTINGS -> Screen.SETTINGS
                else -> Screen.HOME
            },
            why,
        )

    /** The window's × during a timed question: the answers are gone, so the clock waits (D41: controls follow their window). */
    fun onWindowClosed() {
        val s = _state.value
        if (s.screen == Screen.QUIZ && s.timed && s.phase == Phase.WATCH && !s.paused) {
            TsLog.i("WINDOW closed during the clock: paused")
            onEvent(TsEvent.Background(true))
        }
    }

    private fun openLevel(level: Int, questions: List<Question>) {
        if (questions.isEmpty()) return
        stopTimer()
        set("level") {
            it.copy(screen = Screen.INTRO, level = level, questions = questions, index = 0, outcomes = emptyList(), phase = Phase.WATCH, lane = questions.first().lane, yours = null, hint = false, demo = false, breakdown = false, dialog = Dialog.NONE)
        }
    }

    private fun beginQuestion(i: Int, why: String) {
        val s = _state.value
        val q = s.questions.getOrNull(i) ?: return
        stopTimer()
        countdown.floatValue = 1f
        val laneChanges = q.lane != s.lane && s.screen == Screen.QUIZ
        set(why) {
            it.copy(screen = Screen.QUIZ, index = i, lane = q.lane, phase = if (laneChanges) Phase.SWITCHING else Phase.WATCH, yours = null, hint = false, demo = false, breakdown = false, gestureEpoch = if (laneChanges) it.gestureEpoch else it.gestureEpoch + 1)
        }
    }

    private fun pick(a: Answer) {
        val s = _state.value
        if (s.screen != Screen.QUIZ || s.phase != Phase.WATCH || s.dialog != Dialog.NONE || s.paused) return
        val q = s.question ?: return
        val v = Judge.verdict(q.copy(lane = s.lane)) ?: return
        stopTimer()
        val ok = v.answer == a
        if (ok) sound.right() else sound.wrong()
        if (s.demo) {
            set("demo") { it.copy(phase = Phase.DEMO, yours = a) }
            return
        }
        record(q, a, v.answer, s.hint)
        set(if (ok) "right" else "wrong") { it.copy(phase = if (ok) Phase.CORRECT else Phase.WRONG, yours = a) }
        if (v.answer == Answer.GO || v.answer == Answer.WAIT) sound.carsGo()
    }

    private fun record(q: Question, yours: Answer?, correct: Answer, hinted: Boolean) {
        val s = _state.value
        val outcome = Outcome(q, yours, correct, hinted)
        var mistakes = s.mistakes
        if (yours != correct) {
            mistakes = (mistakes.filter { it.question != q } + Mistake(q, yours, System.currentTimeMillis())).takeLast(io.embodit.trafficsignals.platform.Store.MAX_MISTAKES)
            store?.mistakes = mistakes
        } else if (s.level == 0) {
            mistakes = mistakes.filter { it.question != q }
            store?.mistakes = mistakes
        }
        _state.value = s.copy(outcomes = s.outcomes + outcome, mistakes = mistakes)
    }

    private fun nextQuestion() {
        val s = _state.value
        if (s.screen != Screen.QUIZ || s.phase == Phase.WATCH || s.phase == Phase.SWITCHING) return
        val next = s.index + 1
        if (next >= s.questions.size) {
            finishLevel()
            return
        }
        beginQuestion(next, "next")
    }

    private fun finishLevel() {
        val s = _state.value
        stopTimer()
        val score = s.outcomes.count { it.ok }
        var best = s.best
        if (s.level > 0 && score >= (best[s.level] ?: -1)) {
            best = best + (s.level to score)
            store?.best = best
        }
        set("result") { it.copy(screen = Screen.RESULT, best = best, phase = Phase.WATCH, breakdown = false, lane = Lane.STRAIGHT) }
    }

    private fun gestureDone() {
        val s = _state.value
        if (s.screen != Screen.QUIZ || s.phase != Phase.WATCH) return
        if (s.timed && !s.demo && timerJob == null) startTimer((Levels.TIMED_SEC * 1000).toLong())
    }

    // ---------------------------------------------------------------- L6 countdown

    private fun startTimer(ms: Long) {
        deadlineLeftMs = ms
        resumeTimer()
    }

    private fun resumeTimer() {
        if (deadlineLeftMs <= 0L || timerJob != null) return
        deadlineAtMs = SystemClock.elapsedRealtime() + deadlineLeftMs
        timerJob =
            scope.launch {
                while (true) {
                    val left = deadlineAtMs - SystemClock.elapsedRealtime()
                    countdown.floatValue = (left / (Levels.TIMED_SEC * 1000f)).coerceIn(0f, 1f)
                    if (left <= 0) break
                    delay(50)
                }
                timerJob = null
                deadlineLeftMs = 0L
                timeUp()
            }
    }

    private fun pauseTimer() {
        val j = timerJob ?: return
        deadlineLeftMs = (deadlineAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
        j.cancel()
        timerJob = null
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        deadlineLeftMs = 0L
        countdown.floatValue = 1f
    }

    private fun timeUp() {
        val s = _state.value
        if (s.screen != Screen.QUIZ || s.phase != Phase.WATCH) return
        val q = s.question ?: return
        val v = Judge.verdict(q.copy(lane = s.lane)) ?: return
        sound.wrong()
        countdown.floatValue = 0f
        record(q, null, v.answer, s.hint)
        set("timeout") { it.copy(phase = Phase.TIMEOUT, yours = null) }
    }

    /** D26, same order as the factory template: release, close the window and the Stage, remove the tasks, end the process. */
    private fun exit() {
        TsLog.i("EXIT confirmed: release + close window + closeStage + finishAndRemoveTask + exit")
        stopTimer()
        WindowCtl.closeForQuit()
        val nav = WindowCtl.navigator
        val a = activity
        scope.launch {
            runCatching { nav?.closeStage() }.onFailure { TsLog.w("EXIT closeStage: $it") }
            runCatching {
                val am = a?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.appTasks?.forEach { t -> runCatching { t.finishAndRemoveTask() } }
                a?.finishAndRemoveTask()
            }.onFailure { TsLog.w("EXIT finishAndRemoveTask: $it") }
            delay(400)
            kotlin.system.exitProcess(0)
        }
    }

    // ---------------------------------------------------------------- debug seams (same events where one exists)

    fun debugQuestion(q: Question) {
        // an untimed level label: a debug question must not time out (S14 has its own command)
        openLevel(4, listOf(q))
        beginQuestion(0, "debug-q")
    }

    fun debugSeedMistakes(n: Int) {
        val list = Judge.all.shuffled(Random(5)).take(n).map { Mistake(it, Answer.GO, System.currentTimeMillis()) }
        store?.mistakes = list
        set("seed") { it.copy(mistakes = list) }
    }

    fun debugResult(right: Int, total: Int) {
        val qs = Judge.all.shuffled(Random(9)).take(total)
        val outcomes = qs.mapIndexed { i, q -> val v = Judge.verdict(q)!!.answer; Outcome(q, if (i < right) v else Answer.entries.first { it != v }, v, false) }
        set("debug-result") { it.copy(screen = Screen.RESULT, level = 4, questions = qs, outcomes = outcomes, index = total - 1) }
    }

    fun debugTimeout() {
        if (_state.value.screen == Screen.QUIZ && _state.value.phase == Phase.WATCH) {
            timerJob?.cancel()
            timerJob = null
            deadlineLeftMs = 0L
            timeUp()
        }
    }

    fun debugPanels(show: Boolean) {
        set("panels") { it.copy(panelsHidden = !show) }
        WindowCtl.suppress(!show)
    }

    fun debugCam(side: Boolean) = set("cam") { it.copy(camSide = side) }

    fun debugFreeze(on: Boolean) = set("freeze") { it.copy(freeze = on) }

    fun debugName(name: String?) = set("name") { it.copy(debugName = name) }

    fun debugBlocked() = onEvent(TsEvent.Blocked("debug"))

    @Suppress("unused")
    private fun gestureOf(id: String) = Gesture.byId(id)
}
