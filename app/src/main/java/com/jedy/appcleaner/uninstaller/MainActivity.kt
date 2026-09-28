package com.jedy.appcleaner.uninstaller

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.core.locale.LocalizedContent
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppCleanerTheme
import com.jedy.appcleaner.uninstaller.navigation.AppCleanerNavHost
import com.jedy.appcleaner.uninstaller.navigation.DeepLinks
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // PRD §4 Screen 1: held only until the start route is known.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value.startRoute == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
