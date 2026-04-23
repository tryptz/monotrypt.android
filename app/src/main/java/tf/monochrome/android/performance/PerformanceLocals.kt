package tf.monochrome.android.performance

import androidx.compose.runtime.staticCompositionLocalOf
import tf.monochrome.android.MonochromeApp

/**
 * Ambient access to the resolved [PerformanceProfile] from any Composable
 * (including Modifier factories that can't be `@Inject`-ed). Provide it once
 * at the root with `CompositionLocalProvider(LocalPerformanceProfile provides
 * MonochromeApp.profile)`.
 *
 * `static` rather than dynamic: the profile is set exactly once at process
 * start and never mutates, so readers don't need invalidation tracking.
 */
val LocalPerformanceProfile = staticCompositionLocalOf<PerformanceProfile> {
    // Fallback for previews or unit-test composables that bypass the real root
    // provider. Falls through to whatever MonochromeApp resolved at startup;
    // if the Application class hasn't loaded (pure-JVM Compose preview tools),
    // pick HIGH so the preview renders the full glass chrome.
    runCatching { MonochromeApp.profile }
        .getOrElse { PerformanceProfile.forTier(DeviceTier.HIGH) }
}
