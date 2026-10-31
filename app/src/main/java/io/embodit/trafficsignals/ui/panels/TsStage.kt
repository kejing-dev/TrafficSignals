package io.embodit.trafficsignals.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.trafficsignals.ui.theme.TsColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.ui.foundation.content.SpatialView
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.TsEvent
import io.embodit.trafficsignals.ui.TsUiState
import io.embodit.trafficsignals.ui.debug.LocalProbePanel
import io.embodit.trafficsignals.ui.debug.Probe
import io.embodit.trafficsignals.ui.scene.PanelIds
import io.embodit.trafficsignals.ui.scene.TrafficScene

/**
 * The Stage content (window model stage_with_attachment_panels): one full-size SpatialView holding the crossroads and
 * the pooled attachment panels P1 / P2 / P3 / P4 / DLG. [TrafficScene] parents and places them.
 */
@Composable
internal fun TsStage(state: TsUiState, scene: TrafficScene, onEvent: (TsEvent) -> Unit) {
    val bindTick = scene.bindTick.intValue
    // the live theme colours, one `PALETTE role=#AARRGGBB` line each: tools/contrast.py reads them for the D13 table
    val cs = PicoTheme.colorScheme
    LaunchedEffect(cs) {
        fun hex(c: Color) = String.format(java.util.Locale.US, "#%08X", c.toArgb())
        val roles =
            listOf(
                "labelPrimary" to cs.labelPrimary, "labelSecondary" to cs.labelSecondary, "labelTertiary" to cs.labelTertiary,
                "labelQuaternary" to cs.labelQuaternary, "labelPrimaryLight" to cs.labelPrimaryLight,
                "fillPrimary" to cs.fillPrimary, "fillSecondary" to cs.fillSecondary, "fillTertiary" to cs.fillTertiary, "fillLight" to cs.fillLight,
                "dividerLine" to cs.dividerLine, "interaction" to cs.interaction, "passable" to cs.passable, "alert" to cs.alert, "error" to cs.error,
                "ink" to TsColors.Ink,
            )
        TsLog.i("PALETTE " + roles.joinToString(" ") { (n, c) -> "$n=${hex(c)}" })
    }
    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel(PanelIds.HOME) { PoolPanel(PanelIds.HOME, scene) { inView -> HomePanel(if (inView) state else null, onEvent) } }
            AttachmentPanel(PanelIds.QUIZ) { PoolPanel(PanelIds.QUIZ, scene) { inView -> QuizPanel(if (inView) state else null, onEvent) } }
            AttachmentPanel(PanelIds.VERDICT) { PoolPanel(PanelIds.VERDICT, scene) { inView -> VerdictPanel(if (inView) state else null, onEvent) } }
            AttachmentPanel(PanelIds.LESSON) { PoolPanel(PanelIds.LESSON, scene) { inView -> LessonPanel(if (inView) state else null, onEvent) } }
            AttachmentPanel(PanelIds.DIALOG) { PoolPanel(PanelIds.DIALOG, scene) { inView -> DialogPanel(if (inView) state else null, onEvent) } }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

/** Root of a pooled panel: [content] gets `inView` (a pooled panel draws only once it is in view, Spatial Ruler finding 3). */
@Composable
private fun PoolPanel(id: String, scene: TrafficScene, content: @Composable (inView: Boolean) -> Unit) {
    val inView = scene.panelShown[id] == true
    val watcher =
        Modifier.pointerInput(id) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Move) continue
                    if (Probe.enabled) {
                        val p = event.changes.firstOrNull()?.position
                        TsLog.i(String.format(java.util.Locale.US, "PTR panel=%s ev=%s x=%.1f y=%.1f", id, event.type, p?.x ?: 0f, p?.y ?: 0f))
                    }
                }
            }
        }
    CompositionLocalProvider(LocalProbePanel provides id) {
        Box(modifier = watcher) { content(inView) }
    }
}
