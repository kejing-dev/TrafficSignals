package io.embodit.trafficsignals.debug

import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Question
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.Dialog
import io.embodit.trafficsignals.ui.Phase
import io.embodit.trafficsignals.ui.Screen
import io.embodit.trafficsignals.ui.TsController
import io.embodit.trafficsignals.ui.TsEvent
import io.embodit.trafficsignals.ui.debug.Probe

/**
 * QA hooks (design qaHooks). Each command answers `CMD <name> -> <result>` (tag TrafficSignals); every state change logs
 * `STATE <design id> why=...`. Commands drive the same [TsEvent]s as the panels wherever one exists.
 *
 *   state S1|S2|S16|S17|S11|SET|S5|S6|S7|S8|S9|S10|S12|S13|S14|S15|S18   go to a design state (S6 = level 4 first question)
 *   lesson <0..8> [step <k>] [freeze]   open a lesson; step k (0-based) freezes on that step
 *   step <k> | play                      freeze on step k / play again (lesson, breakdown or `freeze` quiz)
 *   level <1..6>                         level intro (S5); `start` begins it
 *   q <gesture>,<FACING>,<LANE>          one-question level, e.g. q leftWait,WEST,LEFT
 *   facing <SOUTH|WEST|NORTH|EAST>       officer at the post facing that way, stop signal (readability shots)
 *   freeze <k>|off                       hold the current quiz gesture at step k
 *   answer <go|stop|wait|slow|lane|pull> | hint | replay | next | switchLane | breakdown | home | back | start
 *   timeout                              L6 time-out now (S14)
 *   result <a>/<b>                       a fake result page (S10)
 *   seedMistakes <n> | clear             n fake mistakes (S11 with content) / empty the mistakes book
 *   dialog leave|exit|reset|close        the self-built dialogs (S12 ...)
 *   dialog confirm|cancel                press the open dialog's right / left button (confirm on exit = TsEvent.Exit, the app quits)
 *   quit                                 settings -> [Quit app] -> [Quit]: the full D26 exit path
 *   block                                the "can't open" dialog (S13)
 *   lang zh|en | sound on|off | big on|off | slow on|off
 *   panels 0|1 | cam front|side          hide every panel / officer turned 90° (frame-check side view)
 *   stick <x>|off                        fake thumbstick X (-1..1) for I8
 *   yaw <deg>|off | recenter             fake head yaw for the placement / re-centre test
 *   background | resume                  S18 (as if the app went to the background) and back
 *   name <label>|clear | dump
 */
object DebugCommands {
    fun run(cmd: String, s: String?) {
        val result = runCatching { exec(cmd.lowercase(), s?.trim()) }.getOrElse { "error: $it" }
        TsLog.i("CMD $cmd -> $result")
    }

    private val c = TsController

    private fun ev(e: TsEvent) = c.onEvent(e)

    private fun on(w: String?) = w == null || w == "on" || w == "1" || w == "true"

    private fun exec(cmd: String, s: String?): String {
        val args = (s ?: "").split(Regex("[\\s]+")).filter { it.isNotBlank() }
        when (cmd) {
            "state" -> goState(args.firstOrNull()?.uppercase() ?: return "need id")
            "lesson" -> {
                ev(TsEvent.OpenLesson(args[0].toInt()))
                val k = args.indexOf("step").takeIf { it >= 0 }?.let { args[it + 1].toInt() }
                if (k != null) ev(TsEvent.LessonStep(k))
            }
            "step" -> ev(TsEvent.LessonStep(args[0].toInt()))
            "play" -> if (!c.state.value.lessonPlaying) ev(TsEvent.LessonPlayPause)
            "level" -> ev(TsEvent.OpenLevel(args[0].toInt()))
            "q" -> c.debugQuestion(Question.parse(s ?: "") ?: return "bad question")
            "facing" -> {
                val f = Facing.valueOf(args[0].uppercase())
                c.debugQuestion(Question(Gesture.STOP, f, Lane.STRAIGHT))
            }
            "freeze" ->
                if (args.firstOrNull() == "off") c.debugFreeze(false)
                else {
                    c.debugFreeze(true)
                    ev(TsEvent.LessonStep(args.firstOrNull()?.toInt() ?: 0))
                }
            "answer" -> ev(TsEvent.Pick(Answer.byId(args[0]) ?: return "bad answer"))
            "hint" -> ev(TsEvent.Hint)
            "replay" -> ev(TsEvent.Replay)
            "next" -> ev(TsEvent.NextQuestion)
            "switchlane" -> ev(TsEvent.SwitchLane)
            "breakdown" -> ev(TsEvent.Breakdown)
            "home" -> ev(TsEvent.Home)
            "back" -> ev(TsEvent.Back)
            "start" -> ev(TsEvent.Start)
            "timeout" -> c.debugTimeout()
            "result" -> {
                val (a, b) = (s ?: "8/10").split('/').map { it.trim().toInt() }
                c.debugResult(a, b)
            }
            "clear" -> ev(TsEvent.ClearMistakes)
            "seedmistakes" -> c.debugSeedMistakes(args.firstOrNull()?.toInt() ?: 6)
            "dialog" ->
                when (args.firstOrNull()) {
                    "leave" -> ev(TsEvent.AskLeave)
                    "exit" -> ev(TsEvent.AskExit)
                    "reset" -> ev(TsEvent.AskReset)
                    // q1-7: press the dialog's buttons with the same events the panel sends
                    "confirm" -> return confirm()
                    "cancel" -> ev(TsEvent.CloseDialog)
                    else -> ev(TsEvent.CloseDialog)
                }
            // q1-7: the whole D26 path in one go, like a user: settings -> [Quit app] -> [Quit]
            "quit" -> {
                ev(TsEvent.OpenSettings)
                ev(TsEvent.AskExit)
                return confirm()
            }
            "block" -> c.debugBlocked()
            "lang" -> ev(TsEvent.Lang(args.firstOrNull() != "en"))
            "sound" -> ev(TsEvent.SoundOn(on(args.firstOrNull())))
            "big" -> ev(TsEvent.Big(on(args.firstOrNull())))
            "slow" -> ev(TsEvent.Slow(on(args.firstOrNull())))
            "panels" -> c.debugPanels(args.firstOrNull() != "0")
            "cam" -> c.debugCam(args.firstOrNull() == "side")
            "stick" -> Probe.host?.scene()?.setDebugStick(args.firstOrNull()?.takeIf { it != "off" }?.toFloat())
            "yaw" -> Probe.host?.scene()?.debugYaw = args.firstOrNull()?.takeIf { it != "off" }?.toFloat()
            "recenter" -> ev(TsEvent.Recenter)
            "background" -> ev(TsEvent.Background(true))
            "resume" -> ev(TsEvent.Resume)
            "name" -> c.debugName(args.joinToString(" ").takeIf { it.isNotBlank() && it != "clear" })
            "dump" -> Probe.host?.scene()?.dump()?.forEach { TsLog.i("DUMP $it") }
            else -> return "unknown"
        }
        return "ok state=${c.designState()}"
    }

    /** The right-hand button of the open dialog: [Quit] (exit / blocked), [Reset], or [Leave] for the leave dialog. */
    private fun confirm(): String {
        val st = c.state.value
        val e =
            when {
                st.screen == Screen.BLOCKED -> TsEvent.Exit
                st.dialog == Dialog.EXIT -> TsEvent.Exit
                st.dialog == Dialog.RESET -> TsEvent.ResetProgress
                st.dialog == Dialog.LEAVE -> TsEvent.Leave
                else -> return "no dialog open state=${c.designState()}"
            }
        TsLog.i("CMD confirm -> $e")
        ev(e)
        return "ok confirmed=$e state=${c.designState()}"
    }

    private fun goState(id: String) {
        when (id) {
            "S1" -> Unit
            "S2" -> ev(TsEvent.Home)
            "S16" -> ev(TsEvent.OpenLessons)
            "S17" -> ev(TsEvent.OpenLevels)
            "S11" -> ev(TsEvent.OpenReview)
            "SET", "SETTINGS" -> ev(TsEvent.OpenSettings)
            "S3" -> ev(TsEvent.OpenLesson(3))
            "S4" -> {
                ev(TsEvent.OpenLesson(3))
                ev(TsEvent.LessonStep(1))
            }
            "S5" -> ev(TsEvent.OpenLevel(4))
            "S6" -> {
                ev(TsEvent.OpenLevel(4))
                ev(TsEvent.Start)
            }
            "S7", "S8" -> {
                ev(TsEvent.OpenLevel(4))
                ev(TsEvent.Start)
                val v = c.state.value.verdict ?: return
                ev(TsEvent.Pick(if (id == "S7") v.answer else Answer.entries.first { it != v.answer }))
            }
            "S9" -> {
                if (!c.state.value.judged) goState("S7")
                ev(TsEvent.SwitchLane)
            }
            "S10" -> c.debugResult(8, 10)
            "S12" -> {
                if (c.state.value.screen != Screen.QUIZ) goState("S6")
                ev(TsEvent.AskLeave)
            }
            "S13" -> c.debugBlocked()
            "S14" -> {
                ev(TsEvent.OpenLevel(6))
                ev(TsEvent.Start)
                c.debugTimeout()
            }
            "S15" -> ev(TsEvent.Lang(false))
            "S18" -> ev(TsEvent.Background(true))
        }
        if (c.state.value.screen == Screen.QUIZ && c.state.value.phase == Phase.SWITCHING) TsLog.i("CMD note: lane switching")
    }
}
