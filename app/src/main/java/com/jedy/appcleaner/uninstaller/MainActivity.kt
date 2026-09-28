package com.jedy.appcleaner.uninstaller

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.core.locale.LocalizedContent
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.startup.StartupTrace
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppCleanerTheme
import com.jedy.appcleaner.uninstaller.navigation.AppCleanerNavHost
import com.jedy.appcleaner.uninstaller.navigation.DeepLinks
import com.jedy.appcleaner.uninstaller.feature.usageaccess.UsageAccessRoundTrip
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Coming back from the Usage access page: granted, or "still off" on the screen they left. */
    @Inject lateinit var usageAccessRoundTrip: UsageAccessRoundTrip

    override fun onResume() {
        super.onResume()
        usageAccessRoundTrip.onAppResumed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainActivity.onCreate")
        // PRD §4 Screen 1: held only until the start route is known.
        var splashDropped = false
        installSplashScreen().setKeepOnScreenCondition {
            val keep = viewModel.uiState.value.startRoute == null
            if (!keep && !splashDropped) {
                splashDropped = true
                StartupTrace.mark("Splash released")
                // Posted so it runs after the frame that shows the start screen.
                window.decorView.post { (application as AppCleanerApplication).onFirstFrameDrawn() }
            }
            keep
        }
        super.onCreate(savedInstanceState)
        // Design review §2A: no system scrim behind 3-button navigation. The default one was a
        // bluish-navy band in dark mode; content scrolls under transparent buttons instead, and
        // every scrolling screen ends with `bottomContentPadding()` so its last item clears them.
        // Bar icon colours follow the in-app theme (AppCleanerTheme), not the system one.
        val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        viewModel.resolveStartRoute()
        if (savedInstanceState == null) intent.toReminderLaunch()?.let(viewModel::onReminderLaunch)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val direction = if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            AppCleanerTheme(themeMode = state.themeMode, layoutDirection = direction) {
                LocalizedContent(language = state.language) {
                    state.startRoute?.let { AppCleanerNavHost(viewModel = viewModel, startRoute = it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.toReminderLaunch()?.let(viewModel::onReminderLaunch)
    }
}

private fun Intent.toReminderLaunch(): ReminderLaunch? {
    val tab = getStringExtra(DeepLinks.EXTRA_OPEN_TAB)?.let { runCatching { HomeTab.valueOf(it) }.getOrNull() }
        ?: return null
    return ReminderLaunch(
        tab = tab,
        preselect = getStringArrayExtra(DeepLinks.EXTRA_PRESELECT)?.toList().orEmpty(),
        count = getIntExtra(DeepLinks.EXTRA_REMINDER_COUNT, 0),
        threshold = getIntExtra(DeepLinks.EXTRA_REMINDER_THRESHOLD, 0),
    )
}
