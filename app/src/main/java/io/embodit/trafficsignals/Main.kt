package io.embodit.trafficsignals

import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import io.embodit.trafficsignals.ui.TsScreen
import io.embodit.trafficsignals.ui.theme.TsTheme

/**
 * App entry (design container STAGE_FULL, skeleton C). The default Stage is Full (manifest `pico.spatial.stage.style=3`):
 * opening the app puts you in the driver's seat at the crossroads; there is no Shared Space window.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage { TsTheme { TsScreen() } }
    }
