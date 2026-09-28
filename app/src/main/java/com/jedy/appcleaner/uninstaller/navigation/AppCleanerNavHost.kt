package com.jedy.appcleaner.uninstaller.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jedy.appcleaner.uninstaller.MainViewModel
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.feature.history.HistoryScreen
import com.jedy.appcleaner.uninstaller.feature.home.HomeScreen
import com.jedy.appcleaner.uninstaller.feature.onboarding.OnboardingScreen
import com.jedy.appcleaner.uninstaller.feature.paywall.PaywallScreen
import com.jedy.appcleaner.uninstaller.feature.settings.SettingsScreen
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallProgressScreen
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallResultScreen
import com.jedy.appcleaner.uninstaller.feature.usageaccess.UsageAccessScreen

@Composable
fun AppCleanerNavHost(
    viewModel: MainViewModel,
    startRoute: String,
    navController: NavHostController = rememberNavController(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openPaywall: (PaywallSource) -> Unit = { navController.navigate(Routes.paywall(it)) }
    val openUsageAccess: (UsageAccessTrigger) -> Unit = { navController.navigate(Routes.usageAccess(it)) }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.background),
        enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                language = state.language,
                onLanguageSelected = viewModel::onLanguageSelected,
                onFinished = { skipped ->
                    viewModel.onOnboardingFinished(skipped)
                    // PRD §3 step 3: the dismissible paywall follows onboarding, once.
                    navController.navigate(Routes.paywall(PaywallSource.ONBOARDING)) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenPaywall = openPaywall,
                onOpenUsageAccess = openUsageAccess,
                onBatchStarted = { navController.navigate(Routes.uninstallProgress(it)) },
            )
        }
        composable(
            route = Routes.PAYWALL,
            arguments = listOf(navArgument(Routes.ARG_SOURCE) { type = NavType.StringType }),
            enterTransition = { slideInVertically { it } },
            popExitTransition = { slideOutVertically { it } },
        ) { entry ->
            val source = PaywallSource.from(entry.arguments?.getString(Routes.ARG_SOURCE))
            PaywallScreen(
                source = source,
                onClose = {
                    if (source == PaywallSource.ONBOARDING) {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.PAYWALL) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable(
            route = Routes.USAGE_ACCESS,
            arguments = listOf(navArgument(Routes.ARG_TRIGGER) { type = NavType.StringType }),
        ) { entry ->
            UsageAccessScreen(
                trigger = UsageAccessTrigger.from(entry.arguments?.getString(Routes.ARG_TRIGGER)),
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                language = state.language,
                onLanguageSelected = viewModel::onLanguageSelected,
                onBack = { navController.popBackStack() },
                onOpenPaywall = openPaywall,
                onOpenUsageAccess = openUsageAccess,
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.UNINSTALL_PROGRESS,
            arguments = listOf(navArgument(Routes.ARG_BATCH) { type = NavType.LongType }),
        ) { entry ->
            UninstallProgressScreen(
                batchId = entry.arguments?.getLong(Routes.ARG_BATCH) ?: 0L,
                onFinished = { batchId ->
                    navController.navigate(Routes.uninstallResult(batchId)) {
                        popUpTo(Routes.UNINSTALL_PROGRESS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.UNINSTALL_RESULT,
            arguments = listOf(navArgument(Routes.ARG_BATCH) { type = NavType.LongType }),
        ) { entry ->
            UninstallResultScreen(
                batchId = entry.arguments?.getLong(Routes.ARG_BATCH) ?: 0L,
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onOpenUnused = {
                    viewModel.requestHomeTab(HomeTab.UNUSED)
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
    }
}
