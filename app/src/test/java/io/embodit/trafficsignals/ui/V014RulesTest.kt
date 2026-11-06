package io.embodit.trafficsignals.ui

import io.embodit.trafficsignals.domain.Judge
import io.embodit.trafficsignals.domain.Levels
import io.embodit.trafficsignals.ui.window.WindowCtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * v0.1.4, critic of v0.1.3: B1 ([再来一次] after a drill that cleared the book), R1 / R4 (the L6 clock runs only while the
 * signal is over and the answers are up: six ways to take them away, each stops the clock and the next signal's end
 * starts a full 5 s), R3 ([清空] asks first).
 */
class V014RulesTest {
    private val c = TsController
    private var t = 0L
    private val full = (Levels.TIMED_SEC * 1000).toLong()

    @Before
    fun clock() {
        t = 1_000_000L
        c.now = { t }
        c.onEvent(TsEvent.Home)
        c.onEvent(TsEvent.Leave)
    }

    @After
    fun leave() {
        c.onEvent(TsEvent.Home)
        c.onEvent(TsEvent.Leave)
    }

    /** L6, the first signal over, 3 s of the 5 gone: the case where v0.1.3 timed out in the middle of the replay. */
    private fun clockHalfGone() {
        c.onEvent(TsEvent.OpenLevel(Levels.TIMED_LEVEL))
        c.onEvent(TsEvent.Start)
        assertEquals(Screen.QUIZ, c.state.value.screen)
        assertEquals(Phase.WATCH, c.state.value.phase)
        c.onEvent(TsEvent.GestureDone)
        assertTrue(c.clockRunning)
        t += 3000
        assertEquals(2000L, c.clockLeftMs)
    }

    /** After [action] the clock stands; ([继续] if it paused), the replayed signal's end starts a whole 5 s. */
    private fun checkStopsAndRestartsFull(name: String, action: () -> Unit) {
        clockHalfGone()
        action()
        assertFalse("$name: the clock stops", c.clockRunning)
        assertEquals("$name: nothing held over", 0L, c.clockLeftMs)
        t += 10_000 // longer than the old clock had left: nothing may time out meanwhile
        assertEquals("$name: still watching", Phase.WATCH, c.state.value.phase)
        if (c.state.value.paused) {
            val epoch = c.state.value.gestureEpoch
            c.onEvent(TsEvent.Resume)
            assertTrue("$name: [继续] replays the signal", c.state.value.gestureEpoch > epoch)
            assertFalse("$name: no clock during the replay", c.clockRunning)
        }
        c.onEvent(TsEvent.GestureDone)
        assertTrue("$name: the replay's end starts the clock", c.clockRunning)
        assertEquals("$name: a whole 5 s, not the old 2 s", full, c.clockLeftMs)
        assertEquals("$name: nothing recorded", 0, c.state.value.outcomes.size)
        leave()
    }

    @Test
    fun replayStopsTheClock() = checkStopsAndRestartsFull("replay") {
        val epoch = c.state.value.gestureEpoch
        c.onEvent(TsEvent.Replay)
        assertTrue(c.state.value.gestureEpoch > epoch)
    }

    @Test
    fun resumeStopsTheClock() = checkStopsAndRestartsFull("resume") {
        c.onEvent(TsEvent.Background(true))
        assertTrue(c.state.value.paused)
    }

    @Test
    fun backgroundStopsTheClock() = checkStopsAndRestartsFull("background") {
        c.onEvent(TsEvent.Background(true))
    }

    @Test
    fun windowMinimisedStopsTheClock() = checkStopsAndRestartsFull("window − (ON_STOP)") {
        WindowCtl.onLifecycle("ON_STOP")
        assertTrue(c.state.value.paused)
    }

    @Test
    fun windowClosedStopsTheClock() = checkStopsAndRestartsFull("window × (ON_DESTROY)") {
        WindowCtl.onLifecycle("ON_DESTROY")
        assertTrue(c.state.value.paused)
    }

    @Test
    fun windowClosedAndReopenedStopsTheClock() = checkStopsAndRestartsFull("window closed and reopened") {
        WindowCtl.onLifecycle("ON_DESTROY")
        WindowCtl.onLifecycle("ON_CREATE")
        WindowCtl.onLifecycle("ON_START")
        WindowCtl.onLifecycle("ON_RESUME")
        assertTrue("reopening does not start the clock by itself", c.state.value.paused)
    }

    @Test
    fun resumeTwiceInARowStillWaitsForTheSignal() {
        clockHalfGone()
        c.onEvent(TsEvent.Background(true))
        c.onEvent(TsEvent.Resume)
        c.onEvent(TsEvent.Background(true))
        c.onEvent(TsEvent.Resume)
        assertFalse(c.clockRunning)
        c.onEvent(TsEvent.GestureDone)
        assertEquals(full, c.clockLeftMs)
    }

    @Test
    fun theLeaveDialogStillHoldsAndContinuesTheClock() {
        // q3-2 unchanged: "leave?" over the question holds the clock, [继续] continues it (the answers come back as they were)
        clockHalfGone()
        c.onEvent(TsEvent.Home)
        assertFalse(c.clockRunning)
        assertEquals(2000L, c.clockLeftMs)
        c.onEvent(TsEvent.CloseDialog)
        assertTrue(c.clockRunning)
        assertEquals(2000L, c.clockLeftMs)
    }

    @Test
    fun drillCleanedOutThenAgainOpensTheRoundAgain() {
        c.debugSeedMistakes(5)
        c.onEvent(TsEvent.OpenReview)
        c.onEvent(TsEvent.Drill)
        assertEquals(Screen.INTRO, c.state.value.screen)
        assertEquals(0, c.state.value.level)
        val round = c.state.value.questions
        assertEquals(5, round.size)
        c.onEvent(TsEvent.Start)
        var guard = 0
        while (c.state.value.screen == Screen.QUIZ && guard++ < 100) {
            val s = c.state.value
            when (s.phase) {
                Phase.SWITCHING -> c.onEvent(TsEvent.LaneArrived)
                Phase.WATCH -> c.onEvent(TsEvent.Pick(Judge.verdict(s.question!!.copy(lane = s.lane))!!.answer))
                else -> c.onEvent(TsEvent.NextQuestion)
            }
        }
        val r = c.state.value
        assertEquals(Screen.RESULT, r.screen)
        assertEquals("all right", 5, r.outcomes.count { it.ok })
        assertTrue("the drill cleared the book", r.mistakes.isEmpty())
        assertTrue("[再来一次] is live", r.canAgain)
        c.onEvent(TsEvent.Again)
        val a = c.state.value
        assertEquals("[再来一次] answers: the intro of the same round", Screen.INTRO, a.screen)
        assertEquals(0, a.level)
        assertEquals(round.toSet(), a.questions.toSet())
        c.onEvent(TsEvent.Start)
        assertEquals(Screen.QUIZ, c.state.value.screen)
    }

    @Test
    fun againIsGreyedWhenThereIsNothingToPlay() {
        assertFalse(TsUiState(screen = Screen.RESULT, level = 0).canAgain)
        assertTrue(TsUiState(screen = Screen.RESULT, level = 3).canAgain)
    }

    @Test
    fun clearAsksFirst() {
        c.debugSeedMistakes(4)
        c.onEvent(TsEvent.OpenReview)
        c.onEvent(TsEvent.AskClear)
        assertEquals(Dialog.CLEAR, c.state.value.dialog)
        assertEquals("nothing gone yet", 4, c.state.value.mistakes.size)
        c.onEvent(TsEvent.CloseDialog)
        assertEquals(Dialog.NONE, c.state.value.dialog)
        assertEquals("[取消] keeps them", 4, c.state.value.mistakes.size)
        c.onEvent(TsEvent.AskClear)
        c.onEvent(TsEvent.ClearMistakes)
        assertEquals(Dialog.NONE, c.state.value.dialog)
        assertTrue(c.state.value.mistakes.isEmpty())
        assertEquals(Screen.REVIEW, c.state.value.screen)
        c.onEvent(TsEvent.AskClear)
        assertEquals("an empty book asks nothing", Dialog.NONE, c.state.value.dialog)
    }

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        @JvmStatic
        @BeforeClass
        fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    }
}
