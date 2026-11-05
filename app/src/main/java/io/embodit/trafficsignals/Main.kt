package io.embodit.trafficsignals

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.trafficsignals.domain.StageLayout
import io.embodit.trafficsignals.ui.TsScreen
import io.embodit.trafficsignals.ui.theme.TsTheme
import io.embodit.trafficsignals.ui.window.PANEL_ID
import io.embodit.trafficsignals.ui.window.TsWindow

/**
 * App entry (design container STAGE_FULL, skeleton C). The default Stage is Full (manifest `pico.spatial.stage.style=3`):
 * opening the app puts you in the driver's seat at the crossroads. v0.1.2 (profile D8 / D21, factory snippet
 * native-window-move): all 2D content is one system Planar window opened from the Stage; the system moves / closes it.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage { TsTheme { TsScreen() } }
        WindowContainer(
            PANEL_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = StageLayout.WINDOW_W.dp, height = StageLayout.WINDOW_H.dp),
        ) { TsWindow() }
    }
