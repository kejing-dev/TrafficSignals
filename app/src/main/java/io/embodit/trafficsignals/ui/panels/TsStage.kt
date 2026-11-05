package io.embodit.trafficsignals.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.content.SpatialView
import io.embodit.trafficsignals.platform.TsLog
import io.embodit.trafficsignals.ui.TsUiState
import io.embodit.trafficsignals.ui.debug.LocalProbePanel
import io.embodit.trafficsignals.ui.scene.PanelIds
import io.embodit.trafficsignals.ui.scene.TrafficScene
import io.embodit.trafficsignals.ui.theme.TsColors

/**
 * The Stage content: one full-size SpatialView holding the crossroads. All 2D UI lives in the system window (TsWindow,
 * D8); the only attachment panel is the chip that brings the window back and the look-level hint (same slot).
 */
@Composable
internal fun TsStage(state: TsUiState, scene: TrafficScene) {
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
            AttachmentPanel(PanelIds.OPEN) {
                CompositionLocalProvider(LocalProbePanel provides PanelIds.OPEN) {
                    // a pooled panel draws only once it is in view (Spatial Ruler finding 3)
                    Box(Modifier.size(ChipSize.W.dp, ChipSize.H.dp)) { if (scene.panelShown[PanelIds.OPEN] == true) OpenChip(state.zh) }
                }
            }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}
