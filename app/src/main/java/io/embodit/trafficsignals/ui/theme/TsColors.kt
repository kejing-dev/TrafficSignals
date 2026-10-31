package io.embodit.trafficsignals.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The design's fixed colours (design-package palette). The four semantic roles go into PicoTheme through [TsTheme];
 * the twelve grey roles inherit SpatialUI Vibrant. Brand tokens only colour domain visuals (the mini map, chips).
 */
object TsColors {
    val Interaction = Color(0xFF3D8BFF) // design-style: fixed-figma-color interaction
    val Passable = Color(0xFF5FD38A) // design-style: fixed-figma-color passable
    val Alert = Color(0xFFFFC233) // design-style: fixed-figma-color alert
    val Error = Color(0xFFFF6B5E) // design-style: fixed-figma-color error

    val Asphalt = Color(0xFF3A3F45) // design-style: fixed-figma-color brandTokens.asphalt (mini map)
    val LaneWhite = Color(0xFFF2F2EE) // design-style: fixed-figma-color brandTokens.laneWhite
    val Vest = Color(0xFFC6F432) // design-style: fixed-figma-color brandTokens.vest
    val IconBg = Color(0xFF1D4E89) // design-style: fixed-figma-color brandTokens.iconBg
    val Ink = Color(0xFF111418) // design-style: fixed-figma-color ink on light fills
}
