package io.embodit.trafficsignals.ui

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.debug.Probe
import io.embodit.trafficsignals.ui.panels.TsStage
import io.embodit.trafficsignals.ui.scene.TrafficFrameSystem
import io.embodit.trafficsignals.ui.scene.TrafficScene
import io.embodit.trafficsignals.ui.window.WindowCtl

/** The Stage: wires [TsController] (state), [TrafficScene] (entities, per-frame work) and opens the system window. */
@Composable
fun TsScreen() {
    val context = LocalContext.current
    remember(context) {
        Probe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        TsController.start(context)
        var c = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        TsController.activity = c as? Activity
        true
    }
    val state by TsController.state.collectAsStateWithLifecycle()
    val scene = remember { TrafficScene(context.applicationContext, TsController::onEvent) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(scene, lifecycle) {
        registerSystem<TrafficFrameSystem>()
        Probe.host = object : Probe.Host { override fun scene(): TrafficScene = scene }
        val observer =
            LifecycleEventObserver { _, event ->
                TsLog.i("LIFECYCLE event=$event")
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        scene.pause()
                        TsController.onEvent(TsEvent.Background(true))
                    }
                    Lifecycle.Event.ON_START -> scene.resume()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        TsLog.i("STAGE enter")
        onDispose {
            lifecycle.removeObserver(observer)
            Probe.host = null
            unregisterSystem<TrafficFrameSystem>()
            scene.detach()
            TsLog.i("STAGE disposed")
        }
    }

    // the main window: opened when the Stage comes up (once the head is level); [回正] closes and re-opens it
    val navigator = LocalSpatialNavigator.current
    DisposableEffect(navigator) {
        WindowCtl.navigator = navigator
        onDispose { if (WindowCtl.navigator === navigator) WindowCtl.navigator = null }
    }
    LaunchedEffect(Unit) { WindowCtl.openWindow("stage") }
    LaunchedEffect(state.placeEpoch) { if (state.placeEpoch > 0) WindowCtl.recall("recenter ${state.placeEpoch}") }

    LaunchedEffect(scene, state) { scene.render(state) }
    TsStage(state = state, scene = scene)
}
