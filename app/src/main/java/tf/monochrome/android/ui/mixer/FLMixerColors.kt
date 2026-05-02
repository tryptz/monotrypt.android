package tf.monochrome.android.ui.mixer

import androidx.compose.ui.graphics.Color

/**
 * FL Studio-inspired color palette for the mixer console.
 *
 * These colours provide the professional DAW aesthetic regardless of the
 * user's chosen app theme.  Every mixer composable references this object
 * instead of MaterialTheme.colorScheme.
 */
object FLColors {

    // ── Backgrounds ────────────────────────────────────────────────────
    val background      = Color(0xFF161822)
    val stripBg         = Color(0xFF1A1D2A)
    val stripBgSelected = Color(0xFF222840)
    val stripBorder     = Color(0xFF282C3C)
    val headerBg        = Color(0xFF1E2130)

    // ── Level Meters ───────────────────────────────────────────────────
    val meterGreenDark  = Color(0xFF3C7A28)
    val meterGreen      = Color(0xFF5CAF34)
    val meterGreenBright= Color(0xFF7CD044)
    val meterYellow     = Color(0xFFB0B830)
    val meterRed        = Color(0xFFC04040)
    val meterBg         = Color(0xFF0A0C14)

    // ── Fader ──────────────────────────────────────────────────────────
    val faderTrack      = Color(0xFF0C0E18)
    val faderThumb      = Color(0xFF4A4E60)
    val faderThumbBorder= Color(0xFF606478)

    // ── Text ───────────────────────────────────────────────────────────
    val textActive      = Color(0xFF90A840)
    val textBright      = Color(0xFFB0C860)
    val textDim         = Color(0xFF505868)
    val textWhite       = Color(0xFFD0D4E0)

    // ── Buttons ────────────────────────────────────────────────────────
    val muteActive      = Color(0xFFE53935)
    val muteInactive    = Color(0xFF2A2E3C)
    val soloActive      = Color(0xFFFFC107)
    val soloInactive    = Color(0xFF2A2E3C)

    // ── Insert Rack ────────────────────────────────────────────────────
    val insertBg            = Color(0xFF1A1D2A)
    val insertHeaderBg      = Color(0xFF222538)
    val insertSlotBg        = Color(0xFF1E2130)
    val insertSlotActive    = Color(0xFF283048)
    val insertSlotBorder    = Color(0xFF303448)
    val insertSlotText      = Color(0xFF708050)
    val insertSlotTextActive= Color(0xFF90B848)

    // ── Accent ─────────────────────────────────────────────────────────
    val accent          = Color(0xFF5CAF34)
}
