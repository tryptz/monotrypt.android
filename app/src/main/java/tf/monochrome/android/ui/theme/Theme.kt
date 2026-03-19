package tf.monochrome.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

val MonochromeDarkScheme = darkColorScheme(
    primary = MonoWhite,
    onPrimary = MonoBlack,
    primaryContainer = MonoSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = MonoCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    background = MonoBlack,
    onBackground = MonoWhite,
    surface = MonoSurface,
    onSurface = MonoWhite,
    surfaceVariant = MonoSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    error = ErrorRed,
    onError = MonoBlack
)

val OceanDarkScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = MonoBlack,
    background = OceanBackground,
    onBackground = MonoWhite,
    surface = OceanSurface,
    onSurface = MonoWhite,
    surfaceVariant = OceanSurface,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    error = ErrorRed
)

val MidnightDarkScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MonoWhite,
    background = MidnightBackground,
    onBackground = MonoWhite,
    surface = MidnightSurface,
    onSurface = MonoWhite,
    surfaceVariant = MidnightSurface,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    error = ErrorRed
)

val CrimsonDarkScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = MonoWhite,
    background = CrimsonBackground,
    onBackground = MonoWhite,
    surface = CrimsonSurface,
    onSurface = MonoWhite,
    surfaceVariant = CrimsonSurface,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    error = ErrorRed
)

val ForestDarkScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = MonoBlack,
    background = ForestBackground,
    onBackground = MonoWhite,
    surface = ForestSurface,
    onSurface = MonoWhite,
    surfaceVariant = ForestSurface,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    error = ErrorRed
)

fun getColorScheme(themeName: String) = when (themeName) {
    "ocean" -> OceanDarkScheme
    "midnight" -> MidnightDarkScheme
    "crimson" -> CrimsonDarkScheme
    "forest" -> ForestDarkScheme
    else -> MonochromeDarkScheme
}

@Composable
fun MonochromeTheme(
    themeName: String = "monochrome_dark",
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(themeName)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MonochromeTypography,
        content = content
    )
}
