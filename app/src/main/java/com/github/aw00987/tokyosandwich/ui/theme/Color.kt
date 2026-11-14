package com.github.aw00987.tokyosandwich.ui.theme

import androidx.compose.ui.graphics.Color

// Fallback "water" palette, used when dynamic color is unavailable.
val WaterBlue80 = Color(0xFF9ECAFF)
val WaterBlueGrey80 = Color(0xFFBBC7DB)
val Teal80 = Color(0xFF80D5D0)

val WaterBlue40 = Color(0xFF0061A4)
val WaterBlueGrey40 = Color(0xFF535F70)
val Teal40 = Color(0xFF006A66)

// Electricity: a light yellow, fixed (not from the dynamic palette) so the TEPCO card reads as electricity.
val ElecYellowContainerLight = Color(0xFFFFF1B8)
val ElecYellowOnContainerLight = Color(0xFF3A2E00)
val ElecYellowLineLight = Color(0xFFB38600)
val ElecYellowOnLineLight = Color(0xFFFFFFFF)

val ElecYellowContainerDark = Color(0xFF4A3D0C)
val ElecYellowOnContainerDark = Color(0xFFFFF0B5)
val ElecYellowLineDark = Color(0xFFFFD54F)
val ElecYellowOnLineDark = Color(0xFF3A2E00)

// Gas: a light orange, fixed like the electricity colours, so the Tokyo Gas card reads as gas (a flame).
val GasFlameContainerLight = Color(0xFFFFDCC6)
val GasFlameOnContainerLight = Color(0xFF3A1A04)
val GasFlameLineLight = Color(0xFFC75A12)
val GasFlameOnLineLight = Color(0xFFFFFFFF)

val GasFlameContainerDark = Color(0xFF5A2D10)
val GasFlameOnContainerDark = Color(0xFFFFDCC6)
val GasFlameLineDark = Color(0xFFFFA36B)
val GasFlameOnLineDark = Color(0xFF3A1A04)

// The ring chart's slices: eight hues in a fixed order, the same hues stepped for the dark surface. More sub-items
// than these share the neutral "other" slice; a ninth hue is never made up.
val PieSlicesLight = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
)
val PieSlicesDark = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF0CA30C), Color(0xFF9085E9), Color(0xFFE66767),
)
val PieOtherLight = Color(0xFF8F8E88)
val PieOtherDark = Color(0xFF7C7B75)
