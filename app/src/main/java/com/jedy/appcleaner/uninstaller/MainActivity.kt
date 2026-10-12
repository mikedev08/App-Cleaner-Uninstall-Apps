package com.jedy.appcleaner.uninstaller

import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jedy.appcleaner.uninstaller.ads.AdManager
import com.jedy.appcleaner.uninstaller.ads.LocalAdManager
import com.jedy.appcleaner.uninstaller.ads.adGate
import com.jedy.appcleaner.uninstaller.core.locale.LocalizedContent
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.startup.StartupTrace
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppCleanerTheme
import com.jedy.appcleaner.uninstaller.feature.usageaccess.UsageAccessRoundTrip
import com.jedy.appcleaner.uninstaller.navigation.AppCleanerNavHost
import com.jedy.appcleaner.uninstaller.navigation.DeepLinks
import com.jedy.appcleaner.uninstaller.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Coming back from the Usage access page: granted, or "still off" on the screen they left. */
    @Inject lateinit var usageAccessRoundTrip: UsageAccessRoundTrip

    @Inject lateinit var adManager: AdManager

    override fun onStart() {
        super.onStart()
        adManager.onActivityStart()
    }

    override fun onResume() {
        super.onResume()
        usageAccessRoundTrip.onAppResumed()
        // A return from the background shows an interstitial (AdMob PRD §5.2, trigger B).
        adManager.onActivityResume(this)
    }

    override fun onStop() {
        super.onStop()
        adManager.onActivityStop(isChangingConfigurations)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) adManager.destroy()
    }

    /**
     * Every Settings page, the Play Store, the purchase sheet, the permission prompt and the ad's
     * own screen start through one of these three, directly or through a launcher. Coming back
     * from them is not a return from the background, so it must not show a resume interstitial.
     */
    override fun startActivity(intent: Intent, options: Bundle?) {
        adManager.suppressNextResume()
        super.startActivity(intent, options)
    }

    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        adManager.suppressNextResume()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    @Deprecated("Deprecated in Java")
    override fun startIntentSenderForResult(
        intent: IntentSender,
        requestCode: Int,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?,
    ) {
        adManager.suppressNextResume()
        @Suppress("DEPRECATION")
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
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
        adManager.onActivityCreate()
        // Design review §2A: no system scrim behind 3-button navigation. The default one was a
        // bluish-navy band in dark mode; content scrolls under transparent buttons instead, and
        // every scrolling screen ends with `bottomContentPadding()` so its last item clears them.
        // Bar icon colours follow the in-app theme (AppCleanerTheme), not the system one.
        val transparent = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        viewModel.resolveStartRoute()
        if (savedInstanceState == null) intent.toReminderLaunch()?.let(viewModel::onReminderLaunch)

        // Consent, then the ad SDK, then preloading (AdMob PRD §4.2), but never for Pro, and for a
        // first-run user only once the language step is behind them. Pro arriving drops every ad
        // at once; losing it starts them again.
        lifecycleScope.launch {
            val startRoute = viewModel.uiState.map { it.startRoute }.filterNotNull().first()
            if (startRoute != Routes.ONBOARDING) adManager.onPastLanguageStep()
            combine(adManager.isPro, adManager.pastLanguageStep, ::adGate)
                .distinctUntilChanged()
                .collect { adManager.onGateChanged(this@MainActivity) }
        }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val direction = if (state.language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            AppCleanerTheme(themeMode = state.themeMode, layoutDirection = direction) {
                LocalizedContent(language = state.language) {
                    CompositionLocalProvider(LocalAdManager provides adManager) {
                        state.startRoute?.let { AppCleanerNavHost(viewModel = viewModel, startRoute = it) }
                    }
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
