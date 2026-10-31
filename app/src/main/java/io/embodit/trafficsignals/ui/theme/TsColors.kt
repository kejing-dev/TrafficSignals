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

    /**
     * The interaction blue as a FILL under text (primary keys, selected segment, chips, icon discs): #3D8BFF lifted 15 %
     * towards white. Ink on #3D8BFF is 5.6:1 by value but only 4.1-4.5:1 on the 2160 px capture (thin strokes on a
     * saturated fill); on #5A9CFF it is 6.8:1 by value. Outlines, the mini map and the 3D route keep #3D8BFF (D13, v0.1.1).
     */
    val InteractionFill = Color(0xFF5A9CFF) // design-style: fixed-figma-color interaction (lifted for text, deviation v0.1.1)
}
