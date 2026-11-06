package io.embodit.trafficsignals.ui

import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Facing
import io.embodit.trafficsignals.domain.Gesture
import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Lane
import io.embodit.trafficsignals.domain.Levels
import io.embodit.trafficsignals.domain.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** QA q3-1 (no answer shown before it is judged) and q3-2 (a "leave?" on top holds the signal and the L6 clock). */
class QuizRulesTest {
    private val q = Question(Gesture.STOP, Facing.EAST, Lane.STRAIGHT)
    private fun base(phase: Phase, paused: Boolean = false) =
        TsUiState(screen = Screen.QUIZ, level = Levels.TIMED_LEVEL, questions = listOf(q), lane = q.lane, phase = phase, paused = paused)

    @Test
    fun keysAreNeverMarkedBeforeTheQuestionIsJudged() {
        for (phase in listOf(Phase.WATCH, Phase.SWITCHING)) for (paused in listOf(false, true)) {
            val s = base(phase, paused)
            for (a in Answer.entries) assertEquals("$phase paused=$paused $a", KeyMark.NONE, s.answerMark(a))
        }
    }

    @Test
    fun keysAreMarkedOnceJudged() {
        val right = Judge.verdict(q)!!.answer
        val wrong = Answer.entries.first { it != right }
        val s = base(Phase.WRONG).copy(yours = wrong)
        assertEquals(KeyMark.RIGHT, s.answerMark(right))
        assertEquals(KeyMark.WRONG, s.answerMark(wrong))
    }

    @Test
    fun answersCountOnlyWhileWatching() {
        assertTrue(base(Phase.WATCH).canAnswer)
        assertFalse(base(Phase.WATCH, paused = true).canAnswer)
        assertFalse(base(Phase.SWITCHING).canAnswer)
        assertFalse(base(Phase.WATCH).copy(dialog = Dialog.LEAVE).canAnswer)
    }

    @Test
    fun verdictStepsUseTheNearPost() {
        assertTrue(base(Phase.WRONG).copy(breakdown = true).officerNear)
        assertTrue(TsUiState(screen = Screen.LESSON).officerNear)
        assertFalse(base(Phase.WATCH).officerNear)
        assertFalse(TsUiState(screen = Screen.HOME).officerNear)
    }

    private fun startL6() {
        val c = TsController
        c.onEvent(TsEvent.OpenLevel(Levels.TIMED_LEVEL))
        c.onEvent(TsEvent.Start)
        assertEquals(Screen.QUIZ, c.state.value.screen)
    }

    @Test
    fun aSignalEndingBehindTheLeaveDialogStartsNoClock() {
        val c = TsController
        startL6()
        c.onEvent(TsEvent.Home)
        assertEquals(Dialog.LEAVE, c.state.value.dialog)
        c.onEvent(TsEvent.GestureDone)
        assertFalse("no clock behind the dialog", c.clockRunning)
        assertEquals("nothing recorded", 0, c.state.value.outcomes.size)
        c.onEvent(TsEvent.CloseDialog)
        assertTrue("the clock starts after keep going", c.clockRunning)
        assertEquals(Phase.WATCH, c.state.value.phase)
        c.onEvent(TsEvent.Home)
        assertFalse("and stops again on the next leave?", c.clockRunning)
        c.onEvent(TsEvent.Leave)
        assertEquals(Screen.HOME, c.state.value.screen)
        assertFalse(c.clockRunning)
    }

    @Test
    fun aTabTapAsksTooAndHoldsTheClock() {
        val c = TsController
        startL6()
        c.onEvent(TsEvent.GestureDone)
        assertTrue(c.clockRunning)
        c.onEvent(TsEvent.Tab("settings"))
        assertEquals(Dialog.LEAVE, c.state.value.dialog)
        assertFalse(c.clockRunning)
        c.onEvent(TsEvent.CloseDialog)
        assertTrue(c.clockRunning)
        c.onEvent(TsEvent.Tab("settings"))
        c.onEvent(TsEvent.Leave)
        assertEquals(Screen.SETTINGS, c.state.value.screen)
    }

    @Test
    fun aPausedQuestionStartsNoClockAndShowsNoAnswer() {
        val c = TsController
        startL6()
        c.onEvent(TsEvent.Background(true)) // the window's × during the signal
        c.onEvent(TsEvent.GestureDone) // the scene hears of the pause a frame late
        assertFalse("no clock while paused", c.clockRunning)
        assertEquals(Phase.WATCH, c.state.value.phase)
        for (a in Answer.entries) assertEquals(KeyMark.NONE, c.state.value.answerMark(a))
        c.onEvent(TsEvent.Pick(Answer.GO))
        assertEquals("no answer counts while paused", Phase.WATCH, c.state.value.phase)
        c.onEvent(TsEvent.Resume)
        assertFalse(c.state.value.paused)
        c.onEvent(TsEvent.GestureDone) // the signal played again
        assertTrue(c.clockRunning)
        c.onEvent(TsEvent.Home)
        c.onEvent(TsEvent.Leave)
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        @JvmStatic
        @BeforeClass
        fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    }
}
