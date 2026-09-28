package com.jedy.appcleaner.uninstaller.core.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/**
 * The in-app language's locale, for formatting dates and numbers. Use this rather than
 * `LocalConfiguration`: bottom sheets and dialogs run in their own window, which replaces
 * `LocalConfiguration` with the device's, so a sheet would format "28 ספט׳ 2026" for an English UI.
 */
val LocalAppLocale = staticCompositionLocalOf<Locale> { Locale.getDefault() }

/**
 * Re-resolves string resources for [language] without restarting the activity.
 *
 * PRD §1 pins the layout to LTR and flips to RTL only for Arabic and Hebrew; that part is handled
 * by the theme. This wrapper is the other half — it makes `stringResource` read from the chosen
 * locale's resources, so tapping a language on the picker changes the copy under the user's finger.
 *
 * Two details are load-bearing, and getting either wrong breaks the app at runtime rather than at
 * compile time:
 *
 *  - [LocalResources] must be provided. Compose 1.8+ resolves `stringResource` through it, not
 *    through `LocalContext.current.resources`, so overriding the context alone changes nothing.
 *  - The provided context must keep the Activity in its `ContextWrapper` base chain. AndroidX
 *    resolves owners such as `ActivityResultRegistryOwner` (and `LocalActivity`) by walking base
 *    contexts up from `LocalContext.current`; a bare `createConfigurationContext()` result
 *    dead-ends before the Activity, and every `rememberLauncherForActivityResult` call in the app
 *    then throws "No ActivityResultRegistryOwner was provided".
 */
@Composable
fun LocalizedContent(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localizedConfiguration = remember(language, configuration) {
        Configuration(configuration).apply {
            val locale = language.toLocale()
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedContext = remember(context, localizedConfiguration) {
        LocalizedContextWrapper(context, context.createConfigurationContext(localizedConfiguration))
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources,
        LocalConfiguration provides localizedConfiguration,
        LocalAppLocale provides language.toLocale(),
        content = content,
    )
}

/**
 * Serves the localized resources while delegating everything else — including `getBaseContext()`
 * — to the original Activity context. `getTheme()` is deliberately *not* overridden: dialog and
 * bottom-sheet windows resolve their theme through it and must keep the Activity's.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val configured: Context,
) : ContextWrapper(base) {
    override fun getResources(): Resources = configured.resources
    override fun getAssets(): AssetManager = configured.assets
}
