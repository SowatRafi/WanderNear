package com.wandernear.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * WanderNear's brand palette.
 *
 * A warm, trustworthy travel look: a calm teal-green (maps, nature, journeys) lifted
 * by a warm amber accent (discovery, sunshine). Deliberately NOT the default Material
 * purple, and it matches the app's teal launcher icon (#0B6E4F). Neutrals are subtly
 * warm ("paper/sand") so white cards feel cosy rather than clinical.
 *
 * Both a light and a dark scheme are hand-tuned so the app looks intentional in either
 * mode (never inverted colours), and every foreground/background pair meets WCAG AA.
 */

// ---- Light ----------------------------------------------------------------------
val LightColors = lightColorScheme(
    primary = Color(0xFF0E7A63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2DC),
    onPrimaryContainer = Color(0xFF00201A),
    inversePrimary = Color(0xFF67D9BC),
    secondary = Color(0xFF4B635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DE),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF96500F),          // warm amber — accents, "mood" header (darkened for AA text contrast)
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF341400),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F4EF),         // warm sand
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFFFFFF),            // cards pop, clean white
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFE7E4DD),     // warm neutral (chips, dividers)
    onSurfaceVariant = Color(0xFF4C4A44),
    outline = Color(0xFF7C7A72),
    outlineVariant = Color(0xFFCFCCC3),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFEFF1ED),
    surfaceTint = Color(0xFF0E7A63),
    surfaceBright = Color(0xFFFDFBF7),
    surfaceDim = Color(0xFFDDDAD3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F0EA),
    surfaceContainer = Color(0xFFEDEAE3),
    surfaceContainerHigh = Color(0xFFE7E4DC),
    surfaceContainerHighest = Color(0xFFE1DED6),
)

// ---- Dark -----------------------------------------------------------------------
val DarkColors = darkColorScheme(
    primary = Color(0xFF6ADABD),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFF88F7D9),
    inversePrimary = Color(0xFF0E7A63),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCEE9DE),
    tertiary = Color(0xFFFFB782),
    onTertiary = Color(0xFF592D00),
    tertiaryContainer = Color(0xFF854300),
    onTertiaryContainer = Color(0xFFFFDCC0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF161D1A),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF3E4B45),
    onSurfaceVariant = Color(0xFFBEC9C2),
    outline = Color(0xFF88938C),
    outlineVariant = Color(0xFF3E4B45),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDFE4DF),
    inverseOnSurface = Color(0xFF2D322F),
    surfaceTint = Color(0xFF6ADABD),
    surfaceBright = Color(0xFF353B38),
    surfaceDim = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A221E),
    surfaceContainerHigh = Color(0xFF242C28),
    surfaceContainerHighest = Color(0xFF2F3733),
)

// ---- Category accents -----------------------------------------------------------
// Each place category gets a soft tinted "badge" (icon colour + container colour) so
// cards are colourful and scannable at a glance. Tuned to read on the warm neutral
// surfaces above. `dark` swaps to brighter icon / deeper container for the dark theme.
data class CategoryTint(val icon: Color, val container: Color)

fun categoryTint(category: String, dark: Boolean): CategoryTint = when (category) {
    "food" -> if (dark) CategoryTint(Color(0xFFFFB59A), Color(0xFF5A2418)) else CategoryTint(Color(0xFFC24B24), Color(0xFFFCE4DB))
    "worship" -> if (dark) CategoryTint(Color(0xFFC9BDF7), Color(0xFF322A57)) else CategoryTint(Color(0xFF5B4CB0), Color(0xFFE9E5FA))
    "outdoor" -> if (dark) CategoryTint(Color(0xFF8FD9A6), Color(0xFF1E3D2A)) else CategoryTint(Color(0xFF2E8B57), Color(0xFFDDF2E4))
    "shopping" -> if (dark) CategoryTint(Color(0xFFF6A8CC), Color(0xFF531633)) else CategoryTint(Color(0xFFC13F76), Color(0xFFFBE3EE))
    "culture" -> if (dark) CategoryTint(Color(0xFFF3B96B), Color(0xFF4A3111)) else CategoryTint(Color(0xFF895410), Color(0xFFF8EAD4))
    "attraction" -> if (dark) CategoryTint(Color(0xFF7FD3E0), Color(0xFF123C44)) else CategoryTint(Color(0xFF1C7C8C), Color(0xFFD7EFF3))
    "safety" -> if (dark) CategoryTint(Color(0xFF9EBBF7), Color(0xFF1E3155)) else CategoryTint(Color(0xFF3D5FB0), Color(0xFFE1E8FB))
    "health" -> if (dark) CategoryTint(Color(0xFFF6A9A6), Color(0xFF551A18)) else CategoryTint(Color(0xFFC13B37), Color(0xFFFBE3E2))
    "fuel" -> if (dark) CategoryTint(Color(0xFF8FD9A6), Color(0xFF1E3D2A)) else CategoryTint(Color(0xFF2E8B57), Color(0xFFDDF2E4))
    "parking" -> if (dark) CategoryTint(Color(0xFF9EBBF7), Color(0xFF1E3155)) else CategoryTint(Color(0xFF3D5FB0), Color(0xFFE1E8FB))
    else -> if (dark) CategoryTint(Color(0xFF9DD2C4), Color(0xFF204039)) else CategoryTint(Color(0xFF3B7C6E), Color(0xFFDDEEE9))
}
