package com.formready.resizer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class DarkModeOption(val label: String) {
    SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark")
}

enum class ThemePalette(val label: String, val swatch: Color, val premium: Boolean = false) {
    INDIGO("Indigo", Color(0xFF3B5BDB)),
    EMERALD("Emerald", Color(0xFF13795B)),
    SUNSET("Sunset", Color(0xFFD9480F)),
    OCEAN("Ocean", Color(0xFF0B7285)),
    ROSE("Rose", Color(0xFFB02A5B)),
    GRAPHITE("Graphite", Color(0xFF4A5057)),
    MIDNIGHT("Midnight", Color(0xFF243B6B), premium = true),
    SAND("Sand", Color(0xFF8A6A2F), premium = true),
    FOREST("Forest", Color(0xFF2F6B35), premium = true);

    fun scheme(dark: Boolean): ColorScheme = when (this) {
        INDIGO -> if (dark) IndigoDark else IndigoLight
        EMERALD -> if (dark) EmeraldDark else EmeraldLight
        SUNSET -> if (dark) SunsetDark else SunsetLight
        OCEAN -> if (dark) OceanDark else OceanLight
        ROSE -> if (dark) RoseDark else RoseLight
        GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
        MIDNIGHT -> if (dark) MidnightDark else MidnightLight
        SAND -> if (dark) SandDark else SandLight
        FOREST -> if (dark) ForestDark else ForestLight
    }
}

private fun light(primary: Long, container: Long, secondary: Long, tertiary: Long) = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color.White,
    primaryContainer = Color(container),
    onPrimaryContainer = Color(0xFF12131A),
    secondary = Color(secondary),
    onSecondary = Color.White,
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(0xFF12131A),
    tertiary = Color(tertiary),
    onTertiary = Color.White,
    surface = Color(0xFFFCFCFE),
    onSurface = Color(0xFF15161B),
    surfaceVariant = Color(0xFFE7E8EF),
    onSurfaceVariant = Color(0xFF45464F),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF15161B),
    outline = Color(0xFF767782)
)

private fun dark(primary: Long, container: Long, secondary: Long, tertiary: Long) = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(0xFF0A0B10),
    primaryContainer = Color(container),
    onPrimaryContainer = Color(0xFFE8E9F2),
    secondary = Color(secondary),
    onSecondary = Color(0xFF0A0B10),
    secondaryContainer = Color(container),
    onSecondaryContainer = Color(0xFFE8E9F2),
    tertiary = Color(tertiary),
    onTertiary = Color(0xFF0A0B10),
    surface = Color(0xFF141519),
    onSurface = Color(0xFFE5E6EC),
    surfaceVariant = Color(0xFF2C2D34),
    onSurfaceVariant = Color(0xFFC6C7D0),
    background = Color(0xFF0F1013),
    onBackground = Color(0xFFE5E6EC),
    outline = Color(0xFF8E8F99)
)

internal val IndigoLight = light(0xFF3B5BDB, 0xFFDDE3FB, 0xFF5568C9, 0xFF7048E8)
internal val IndigoDark = dark(0xFFAEBDFF, 0xFF2A3A75, 0xFF9FB0F5, 0xFFC7B2FF)

internal val EmeraldLight = light(0xFF13795B, 0xFFD3F0E3, 0xFF2F8F6E, 0xFF0C7C91)
internal val EmeraldDark = dark(0xFF6FDCB4, 0xFF1B4E3E, 0xFF74D3B6, 0xFF6ED2E6)

internal val SunsetLight = light(0xFFD9480F, 0xFFFFE0D0, 0xFFC2410C, 0xFFB5179E)
internal val SunsetDark = dark(0xFFFFB086, 0xFF7A2E0C, 0xFFFFA271, 0xFFF39DE4)

internal val OceanLight = light(0xFF0B7285, 0xFFCFEAF2, 0xFF1C7ED6, 0xFF3B5BDB)
internal val OceanDark = dark(0xFF73D6E8, 0xFF13505C, 0xFF8ABEF5, 0xFFAEBDFF)

internal val RoseLight = light(0xFFB02A5B, 0xFFFBD9E5, 0xFFC2255C, 0xFF862E9E)
internal val RoseDark = dark(0xFFFFA1C0, 0xFF6E2040, 0xFFFF9BBA, 0xFFDDA8F5)

internal val GraphiteLight = light(0xFF3C4149, 0xFFE2E4E9, 0xFF55606E, 0xFF1C7ED6)
internal val GraphiteDark = dark(0xFFC3C8D2, 0xFF3A3F47, 0xFFB2BAC6, 0xFF8ABEF5)

internal val MidnightLight = light(0xFF243B6B, 0xFFD8DFF2, 0xFF3E5590, 0xFF17607A)
internal val MidnightDark = dark(0xFF9FB6EC, 0xFF1D2C4F, 0xFFA9BCE8, 0xFF7FC6DD)

internal val SandLight = light(0xFF8A6A2F, 0xFFF5E7CB, 0xFFA07A34, 0xFF7A5B2A)
internal val SandDark = dark(0xFFE6C88A, 0xFF57451E, 0xFFD9BC80, 0xFFCBB07A)

internal val ForestLight = light(0xFF2F6B35, 0xFFD8EEDA, 0xFF3F7F46, 0xFF1F6B5E)
internal val ForestDark = dark(0xFF97D89C, 0xFF24492A, 0xFF8FCE96, 0xFF7ACBBC)
