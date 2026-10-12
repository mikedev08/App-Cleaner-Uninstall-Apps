package com.jedy.appcleaner.uninstaller.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jedy.appcleaner.uninstaller.BuildConfig
import com.jedy.appcleaner.uninstaller.MainViewModel
import com.jedy.appcleaner.uninstaller.ads.AdSurface
import com.jedy.appcleaner.uninstaller.ads.LocalAdManager
import com.jedy.appcleaner.uninstaller.ads.PinnedAdLayout
import com.jedy.appcleaner.uninstaller.core.analytics.AdPlacement
import com.jedy.appcleaner.uninstaller.core.model.HomeTab
import com.jedy.appcleaner.uninstaller.core.model.PaywallSource
import com.jedy.appcleaner.uninstaller.core.model.UsageAccessTrigger
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.feature.apps.AppsScreen
import com.jedy.appcleaner.uninstaller.feature.history.HistoryScreen
import com.jedy.appcleaner.uninstaller.feature.home.HomeScreen
import com.jedy.appcleaner.uninstaller.feature.onboarding.OnboardingScreen
import com.jedy.appcleaner.uninstaller.feature.paywall.PaywallScreen
import com.jedy.appcleaner.uninstaller.feature.scan.ScanScreen
import com.jedy.appcleaner.uninstaller.feature.settings.SettingsScreen
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallProgressScreen
import com.jedy.appcleaner.uninstaller.feature.uninstall.UninstallResultScreen
import com.jedy.appcleaner.uninstaller.feature.usageaccess.UsageAccessScreen
import kotlinx.coroutines.launch

@Composable
fun AppCleanerNavHost(
    viewModel: MainViewModel,
    startRoute: String,
    navController: NavHostController = rememberNavController(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openPaywall: (PaywallSource) -> Unit = { navController.navigate(Routes.paywall(it)) }
    val openUsageAccess: (UsageAccessTrigger) -> Unit = { navController.navigate(Routes.usageAccess(it)) }

    // Ads (AdMob PRD §5): the current route decides where an interstitial may show, and arriving at
    // Home from one of the screens below is the in-session show point (trigger C).
    val ads = LocalAdManager.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(navController, ads, activity) {
        if (ads == null || activity == null) return@LaunchedEffect
        var previous: String? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route
            ads.surface = adSurfaceFor(route)
            if (route == Routes.HOME && previous in ReturnsToHome) ads.tryShowInSession(activity)
            previous = route
        }
    }

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
                    // PRD §3 step 3: the dismissible paywall follows onboarding, once; a build
                    // without subscriptions goes straight Home.
                    val next = if (BuildConfig.SUBSCRIPTIONS) Routes.paywall(PaywallSource.ONBOARDING) else Routes.HOME
                    navController.navigate(next) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            PinnedAdLayout(AdPlacement.HOME) {
                HomeScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenPaywall = openPaywall,
                    onOpenUsageAccess = openUsageAccess,
                    onBatchStarted = { navController.navigate(Routes.uninstallProgress(it)) },
                    onOpenScan = { navController.navigate(Routes.SCAN) },
                    onOpenApps = { navController.navigate(Routes.apps(it)) },
                )
            }
        }
        composable(Routes.SCAN) {
            ScanScreen(
                onClose = { navController.popBackStack() },
                onOpenApps = { tab ->
                    navController.navigate(Routes.apps(tab)) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onOpenPaywall = openPaywall,
                onOpenUsageAccess = openUsageAccess,
            )
        }
        composable(
            route = Routes.APPS,
            arguments = listOf(navArgument(Routes.ARG_TAB) { type = NavType.StringType }),
        ) { entry ->
            val tab = entry.arguments?.getString(Routes.ARG_TAB)
                ?.let { runCatching { HomeTab.valueOf(it) }.getOrNull() } ?: HomeTab.ALL
            AppsScreen(
                initialTab = tab,
                onBack = { navController.popBackStack() },
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
                    // Trigger A (AdMob PRD §5.2): every close without buying. Pro just bought sees nothing.
                    if (source == PaywallSource.ONBOARDING) {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.PAYWALL) { inclusive = true } }
                        if (ads != null && activity != null) scope.launch { ads.showAfterLaunchOffer(activity) }
                    } else {
                        navController.popBackStack()
                        if (activity != null) ads?.showAfterOffer(activity)
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
            PinnedAdLayout(AdPlacement.SETTINGS) {
                SettingsScreen(
                    language = state.language,
                    onLanguageSelected = viewModel::onLanguageSelected,
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = openPaywall,
                    onOpenUsageAccess = openUsageAccess,
                )
            }
        }
        composable(Routes.HISTORY) {
            PinnedAdLayout(AdPlacement.HISTORY) { HistoryScreen(onBack = { navController.popBackStack() }) }
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
                    navController.navigate(Routes.apps(HomeTab.UNUSED)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onRetry = { newBatchId ->
                    navController.navigate(Routes.uninstallProgress(newBatchId)) {
                        popUpTo(Routes.UNINSTALL_RESULT) { inclusive = true }
                    }
                },
            )
        }
    }
}

/** Leaving these for Home is a natural break: the in-session interstitial's moment. */
private val ReturnsToHome = setOf(Routes.UNINSTALL_RESULT, Routes.APPS, Routes.HISTORY, Routes.SETTINGS)

private fun adSurfaceFor(route: String?): AdSurface = when (route) {
    Routes.HOME -> AdSurface.HOME
    Routes.SCAN -> AdSurface.SCAN
    Routes.APPS -> AdSurface.APPS
    Routes.HISTORY -> AdSurface.HISTORY
    Routes.SETTINGS -> AdSurface.SETTINGS
    Routes.UNINSTALL_PROGRESS -> AdSurface.UNINSTALL
    Routes.UNINSTALL_RESULT -> AdSurface.RESULT
    Routes.PAYWALL -> AdSurface.PAYWALL
    Routes.USAGE_ACCESS -> AdSurface.USAGE_ACCESS
    // Onboarding, and anything unknown: no interstitial.
    else -> AdSurface.ONBOARDING
}
