package tf.monochrome.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

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
    tertiaryContainer = MonoCard,
    onTertiaryContainer = MonoWhite,
    background = MonoBlack,
    onBackground = MonoWhite,
    surface = MonoSurface,
    onSurface = MonoWhite,
    surfaceVariant = MonoSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = MonoOutline,
    outlineVariant = MonoSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val OceanDarkScheme = darkColorScheme(
    primary = OceanPrimary,
    onPrimary = MonoBlack,
    primaryContainer = OceanSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = OceanCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = OceanCard,
    onTertiaryContainer = MonoWhite,
    background = OceanBackground,
    onBackground = MonoWhite,
    surface = OceanSurface,
    onSurface = MonoWhite,
    surfaceVariant = OceanSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = OceanOutline,
    outlineVariant = OceanSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val MidnightDarkScheme = darkColorScheme(
    primary = MidnightPrimary,
    onPrimary = MonoWhite,
    primaryContainer = MidnightSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = MidnightCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = MidnightCard,
    onTertiaryContainer = MonoWhite,
    background = MidnightBackground,
    onBackground = MonoWhite,
    surface = MidnightSurface,
    onSurface = MonoWhite,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = MidnightOutline,
    outlineVariant = MidnightSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val CrimsonDarkScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = MonoWhite,
    primaryContainer = CrimsonSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = CrimsonCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = CrimsonCard,
    onTertiaryContainer = MonoWhite,
    background = CrimsonBackground,
    onBackground = MonoWhite,
    surface = CrimsonSurface,
    onSurface = MonoWhite,
    surfaceVariant = CrimsonSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = CrimsonOutline,
    outlineVariant = CrimsonSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val ForestDarkScheme = darkColorScheme(
    primary = ForestPrimary,
    onPrimary = MonoBlack,
    primaryContainer = ForestSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = ForestCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = ForestCard,
    onTertiaryContainer = MonoWhite,
    background = ForestBackground,
    onBackground = MonoWhite,
    surface = ForestSurface,
    onSurface = MonoWhite,
    surfaceVariant = ForestSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = ForestOutline,
    outlineVariant = ForestSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val SunsetDarkScheme = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = MonoBlack,
    primaryContainer = SunsetSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = SunsetCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = SunsetCard,
    onTertiaryContainer = MonoWhite,
    background = SunsetBackground,
    onBackground = MonoWhite,
    surface = SunsetSurface,
    onSurface = MonoWhite,
    surfaceVariant = SunsetSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = SunsetOutline,
    outlineVariant = SunsetSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val CyberpunkDarkScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = MonoWhite,
    primaryContainer = CyberpunkSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = CyberpunkCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = CyberpunkCard,
    onTertiaryContainer = MonoWhite,
    background = CyberpunkBackground,
    onBackground = MonoWhite,
    surface = CyberpunkSurface,
    onSurface = MonoWhite,
    surfaceVariant = CyberpunkSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = CyberpunkOutline,
    outlineVariant = CyberpunkSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val NordDarkScheme = darkColorScheme(
    primary = NordPrimary,
    onPrimary = MonoBlack,
    primaryContainer = NordSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = NordCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = NordCard,
    onTertiaryContainer = MonoWhite,
    background = NordBackground,
    onBackground = MonoWhite,
    surface = NordSurface,
    onSurface = MonoWhite,
    surfaceVariant = NordSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = NordOutline,
    outlineVariant = NordSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val GruvboxDarkScheme = darkColorScheme(
    primary = GruvboxPrimary,
    onPrimary = MonoBlack,
    primaryContainer = GruvboxSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = GruvboxCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = GruvboxCard,
    onTertiaryContainer = MonoWhite,
    background = GruvboxBackground,
    onBackground = MonoWhite,
    surface = GruvboxSurface,
    onSurface = MonoWhite,
    surfaceVariant = GruvboxSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = GruvboxOutline,
    outlineVariant = GruvboxSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val DraculaDarkScheme = darkColorScheme(
    primary = DraculaPrimary,
    onPrimary = MonoBlack,
    primaryContainer = DraculaSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = DraculaCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = DraculaCard,
    onTertiaryContainer = MonoWhite,
    background = DraculaBackground,
    onBackground = MonoWhite,
    surface = DraculaSurface,
    onSurface = MonoWhite,
    surfaceVariant = DraculaSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = DraculaOutline,
    outlineVariant = DraculaSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val SolarizedDarkScheme = darkColorScheme(
    primary = SolarizedPrimary,
    onPrimary = MonoBlack,
    primaryContainer = SolarizedSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = SolarizedCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = SolarizedCard,
    onTertiaryContainer = MonoWhite,
    background = SolarizedBackground,
    onBackground = MonoWhite,
    surface = SolarizedSurface,
    onSurface = MonoWhite,
    surfaceVariant = SolarizedSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = SolarizedOutline,
    outlineVariant = SolarizedSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val LavenderDarkScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = MonoBlack,
    primaryContainer = LavenderSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = LavenderCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = LavenderCard,
    onTertiaryContainer = MonoWhite,
    background = LavenderBackground,
    onBackground = MonoWhite,
    surface = LavenderSurface,
    onSurface = MonoWhite,
    surfaceVariant = LavenderSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = LavenderOutline,
    outlineVariant = LavenderSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val GoldDarkScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = MonoBlack,
    primaryContainer = GoldSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = GoldCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = GoldCard,
    onTertiaryContainer = MonoWhite,
    background = GoldBackground,
    onBackground = MonoWhite,
    surface = GoldSurface,
    onSurface = MonoWhite,
    surfaceVariant = GoldSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = GoldOutline,
    outlineVariant = GoldSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val RosewaterDarkScheme = darkColorScheme(
    primary = RosewaterPrimary,
    onPrimary = MonoBlack,
    primaryContainer = RosewaterSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = RosewaterCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = RosewaterCard,
    onTertiaryContainer = MonoWhite,
    background = RosewaterBackground,
    onBackground = MonoWhite,
    surface = RosewaterSurface,
    onSurface = MonoWhite,
    surfaceVariant = RosewaterSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = RosewaterOutline,
    outlineVariant = RosewaterSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val MintDarkScheme = darkColorScheme(
    primary = MintPrimary,
    onPrimary = MonoBlack,
    primaryContainer = MintSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = MintCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = MintCard,
    onTertiaryContainer = MonoWhite,
    background = MintBackground,
    onBackground = MonoWhite,
    surface = MintSurface,
    onSurface = MonoWhite,
    surfaceVariant = MintSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = MintOutline,
    outlineVariant = MintSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)

val WhiteScheme = androidx.compose.material3.lightColorScheme(
    primary = WhitePrimary,
    onPrimary = MonoWhite,
    primaryContainer = WhiteSurfaceVariant,
    onPrimaryContainer = MonoBlack,
    secondary = MonoTextSecondary,
    onSecondary = MonoWhite,
    secondaryContainer = WhiteCard,
    onSecondaryContainer = MonoBlack,
    tertiary = MonoTextTertiary,
    onTertiary = MonoWhite,
    tertiaryContainer = WhiteCard,
    onTertiaryContainer = MonoBlack,
    background = WhiteBackground,
    onBackground = MonoBlack,
    surface = WhiteSurface,
    onSurface = MonoBlack,
    surfaceVariant = WhiteSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = WhiteOutline,
    outlineVariant = WhiteSurfaceVariant,
    error = ErrorRed,
    onError = MonoWhite
)

val ClearDarkScheme = darkColorScheme(
    primary = ClearPrimary,
    onPrimary = MonoBlack,
    primaryContainer = ClearSurfaceVariant,
    onPrimaryContainer = MonoWhite,
    secondary = MonoTextSecondary,
    onSecondary = MonoBlack,
    secondaryContainer = ClearCard,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoTextTertiary,
    onTertiary = MonoBlack,
    tertiaryContainer = ClearCard,
    onTertiaryContainer = MonoWhite,
    background = ClearBackground,
    onBackground = MonoWhite,
    surface = ClearSurface,
    onSurface = MonoWhite,
    surfaceVariant = ClearSurfaceVariant,
    onSurfaceVariant = MonoTextSecondary,
    outline = ClearOutline,
    outlineVariant = ClearSurfaceVariant,
    error = ErrorRed,
    onError = MonoBlack
)
/** Display names for theme selection UI */
val themeDisplayNames = mapOf(
    "monochrome_dark" to "Monochrome",
    "ocean" to "Ocean",
    "midnight" to "Midnight",
    "crimson" to "Crimson",
    "forest" to "Forest",
    "sunset" to "Sunset",
    "cyberpunk" to "Cyberpunk",
    "nord" to "Nord",
    "gruvbox" to "Gruvbox",
    "dracula" to "Dracula",
    "solarized" to "Solarized",
    "lavender" to "Lavender",
    "gold" to "Gold",
    "rosewater" to "Rosewater",
    "mint" to "Mint",
    "white" to "White",
    "clear" to "Clear"
)

fun getColorScheme(themeName: String) = when (themeName) {
    "ocean" -> OceanDarkScheme
    "midnight" -> MidnightDarkScheme
    "crimson" -> CrimsonDarkScheme
    "forest" -> ForestDarkScheme
    "sunset" -> SunsetDarkScheme
    "cyberpunk" -> CyberpunkDarkScheme
    "nord" -> NordDarkScheme
    "gruvbox" -> GruvboxDarkScheme
    "dracula" -> DraculaDarkScheme
    "solarized" -> SolarizedDarkScheme
    "lavender" -> LavenderDarkScheme
    "gold" -> GoldDarkScheme
    "rosewater" -> RosewaterDarkScheme
    "mint" -> MintDarkScheme
    "white" -> WhiteScheme
    "clear" -> ClearDarkScheme
    else -> MonochromeDarkScheme
}

@Composable
fun MonochromeTheme(
    themeName: String = "monochrome_dark",
    fontScale: Float = 1.0f,
    customFontFamily: FontFamily? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(themeName)
    val family = customFontFamily ?: InterFontFamily
    val typography = remember(fontScale, family) {
        buildTypography(family, fontScale)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
