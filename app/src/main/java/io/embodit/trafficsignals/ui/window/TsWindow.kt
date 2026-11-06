package io.embodit.trafficsignals.ui.window

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.trafficsignals.R
import io.embodit.trafficsignals.domain.LevelGate
import io.embodit.trafficsignals.domain.StageLayout
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.Screen
import io.embodit.trafficsignals.ui.TsController
import io.embodit.trafficsignals.ui.TsEvent
import io.embodit.trafficsignals.ui.debug.LocalProbePanel
import io.embodit.trafficsignals.ui.panels.Ui
import io.embodit.trafficsignals.ui.panels.WindowPage
import io.embodit.trafficsignals.ui.theme.TsTheme

/** Id and tag of the main window (tag != null: "reuse" mode, one instance). */
const val PANEL_ID = "ControlPanel"
const val PANEL_TAG = "main"

/**
 * The main window's state shared by the Stage and the window (one process, native-window-move §3.4): the window reports
 * its lifecycle, the Stage opens / recalls it with its navigator. Main thread only.
 */
object WindowCtl {
    /** Last lifecycle event of the window ("ON_CREATE" … "ON_DESTROY"), "never" before the first open. */
    var panelState by mutableStateOf("never")
        private set

    /** Bumped on every ON_CREATE: the system has just put the window on the line of sight. */
    var createdEpoch by mutableIntStateOf(0)
        private set

    var navigator: SpatialNavigator? = null

    /** Debug `panels 0`: frame-check shots, no window and no chip. */
    var suppressed by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private var recallPending = false
    private var quitting = false
    private var askedAtMs = 0L
    private var opening = false
    private val gate = LevelGate()
    private var lastStepMs = 0L

    /** "平视前方": an open waits and the head is not level (D9, the system opens the window square to the line of sight). */
    var levelHint by mutableStateOf(false)
        private set

    val open: Boolean
        get() = panelState != "never" && panelState != "ON_DESTROY" && panelState != "ON_STOP"

    /** The Stage shows "打开面板" while the window is closed / minimised, or never came up within 8 s of asking. */
    fun chipWanted(now: Long): Boolean =
        if (gate.waiting || suppressed || (opening && now - askedAtMs < 8000)) false
        else when (panelState) {
            "ON_DESTROY", "ON_STOP" -> !recallPending
            "never" -> askedAtMs > 0 && now - askedAtMs > 8000
            else -> false
        }

    fun openWindow(why: String) {
        val now = SystemClock.elapsedRealtime()
        TsLog.i("WINDOW ask why=$why state=$panelState (opens when the head is level)")
        gate.ask(why, now)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    /** One Stage frame: [pitchDeg] = head pitch (+ up), null while the head pose is not valid yet. */
    fun stepGate(now: Long, pitchDeg: Float?) {
        lastStepMs = now
        if (!gate.waiting) {
            if (levelHint) levelHint = false
            return
        }
        val hint = gate.hintWanted(pitchDeg)
        if (hint != levelHint) {
            levelHint = hint
            TsLog.i(String.format(java.util.Locale.US, "LEVEL hint=%s pitch=%.1f waited=%d ms", hint, pitchDeg ?: Float.NaN, gate.waitedMs(now)))
        }
        val why = gate.step(now, pitchDeg) ?: return
        levelHint = false
        TsLog.i(String.format(java.util.Locale.US, "LEVEL ok pitch=%.1f -> open", pitchDeg ?: Float.NaN))
        openNow(why)
    }

    /** No Stage frames for a while: do not hold the window back (headset cold start: the Stage's frames start ~6 s after launch). */
    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!gate.waiting) return
                val now = SystemClock.elapsedRealtime()
                val stalled = if (lastStepMs == 0L) gate.waitedMs(now) > FIRST_FRAME_MS else now - lastStepMs > WATCHDOG_MS
                if (stalled) {
                    val why = gate.pending ?: return
                    gate.cancel()
                    levelHint = false
                    openNow("$why/noframes")
                } else main.postDelayed(this, WATCHDOG_MS)
            }
        }

    private fun openNow(why: String) {
        val nav = navigator ?: return TsLog.w("WINDOW open why=$why: no navigator")
        askedAtMs = SystemClock.elapsedRealtime()
        opening = true
        TsLog.i("WINDOW open why=$why state=$panelState")
        runCatching { nav.openWindowContainer(PANEL_ID, PANEL_TAG) }.onFailure { TsLog.w("WINDOW open failed: $it") }
    }

    /** The chip: a minimised window is restored where it was, a closed one opens in front of you. */
    fun reopen(why: String) {
        if (panelState == "ON_STOP" && navigator?.let { runCatching { it.restoreWindowContainer(PANEL_ID, PANEL_TAG) }.getOrDefault(false) } == true) {
            TsLog.i("WINDOW restore why=$why")
            return
        }
        openWindow(why)
    }

    /** [回正]: a second open of an open window only focuses it, so close, wait for ON_DESTROY, open (§3.5). */
    fun recall(why: String) {
        if (suppressed) return
        if (panelState == "never" || panelState == "ON_DESTROY") return openWindow(why)
        val nav = navigator ?: return
        recallPending = true
        TsLog.i("WINDOW recall why=$why: close, then open after ON_DESTROY")
        runCatching { nav.closeWindowContainer(PANEL_ID, PANEL_TAG) }.onFailure { TsLog.w("WINDOW close failed: $it"); recallPending = false }
    }

    /** Debug `panels 0|1`. */
    fun suppress(hide: Boolean) {
        suppressed = hide
        if (hide) {
            gate.cancel()
            levelHint = false
            if (open) runCatching { navigator?.closeWindowContainer(PANEL_ID, PANEL_TAG) }
        } else if (!open) openWindow("debug")
    }

    fun onLifecycle(event: String) {
        panelState = event
        TsLog.i("WINDOW lifecycle $event")
        if (event == "ON_CREATE") {
            createdEpoch++
            opening = false
        }
        // critic R4: the system − (minimise) takes the answers away just like ×, so a running L6 clock stops on ON_STOP too
        if (event == "ON_STOP" && !recallPending && !quitting && !suppressed) TsController.onWindowClosed()
        if (event == "ON_DESTROY") {
            if (recallPending) {
                recallPending = false
                main.postDelayed({ openWindow("recall") }, 300)
            } else if (!quitting && !suppressed) {
                // × closed the window: the answers went with it, so a running L6 clock must not run out behind your back
                TsController.onWindowClosed()
            }
        }
    }

    fun closeForQuit() {
        quitting = true
        recallPending = false
        gate.cancel()
        levelHint = false
        runCatching { navigator?.closeWindowContainer(PANEL_ID, PANEL_TAG) }
    }

    private const val WATCHDOG_MS = 3000L
    private const val FIRST_FRAME_MS = 15000L
}

const val TAB_HOME = "home"
const val TAB_LESSONS = "lessons"
const val TAB_LEVELS = "levels"
const val TAB_REVIEW = "review"
const val TAB_SETTINGS = "settings"

/** Which TabBar item is lit for a screen. */
fun tabOf(screen: Screen): String =
    when (screen) {
        Screen.STARTING, Screen.HOME, Screen.BLOCKED -> TAB_HOME
        Screen.LESSONS, Screen.LESSON -> TAB_LESSONS
        Screen.LEVELS, Screen.INTRO, Screen.QUIZ, Screen.RESULT -> TAB_LEVELS
        Screen.REVIEW -> TAB_REVIEW
        Screen.SETTINGS -> TAB_SETTINGS
    }

/** The content of the one system window (Planar, declared in Main.kt): native TabBar + the current page. */
@Composable
fun TsWindow() {
    val state by TsController.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> WindowCtl.onLifecycle(e.name) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val converter = LocalPhysicalLengthConverter.current
    LaunchedEffect(converter) {
        runCatching { converter.dpToLength(1000.dp, LengthUnit.Meters) / 1000f }.getOrNull()?.takeIf { it in 0.0002f..0.005f }?.let {
            TsLog.i(String.format(java.util.Locale.US, "WINDOW dp=%.5f m by the converter; drawn %.5f m, half angle %.1f (StageLayout)", it, StageLayout.WINDOW_DRAWN_M, StageLayout.windowHalfDeg()))
        }
    }
    val onEvent: (TsEvent) -> Unit = TsController::onEvent
    CompositionLocalProvider(LocalProbePanel provides "window") {
        TsTheme {
            MainWindow(
                pages =
                    listOf(
                        MainWindowPage(TAB_HOME, Ui.home.of(state.zh), R.drawable.ic_home),
                        MainWindowPage(TAB_LESSONS, Ui.learn.of(state.zh), R.drawable.ic_school),
                        MainWindowPage(TAB_LEVELS, Ui.judge.of(state.zh), R.drawable.ic_traffic),
                        MainWindowPage(TAB_REVIEW, Ui.review.of(state.zh), R.drawable.ic_menu_book),
                        MainWindowPage(TAB_SETTINGS, Ui.settings.of(state.zh), R.drawable.ic_settings),
                    ),
                current = tabOf(state.screen),
                onSelect = { onEvent(TsEvent.Tab(it)) },
            ) { WindowPage(state, onEvent) }
        }
    }
}
