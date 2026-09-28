package com.jedy.appcleaner.uninstaller.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.data.billing.Premium
import com.jedy.appcleaner.uninstaller.data.prefs.AppPreferences
import com.jedy.appcleaner.uninstaller.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §6 item 18: the one-time "your premium ended" explanation. Shared by Home and Apps (both
 * used to be the old Home), so the logic lives once and outlives either ViewModel.
 *
 * Billing reports `false` until RevenueCat's cached CustomerInfo arrives, which would flash the
 * banner at every paying user on cold start. It (and the `is_premium` analytics param) wait
 * until premium was seen or [PREMIUM_SETTLE_MS] elapsed.
 */
@Singleton
class PremiumLapse @Inject constructor(
    premium: Premium,
    private val preferences: AppPreferences,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _settled = MutableStateFlow(false)
    val settled: StateFlow<Boolean> = _settled.asStateFlow()

    val showBanner: Flow<Boolean> = combine(
        premium.isPremium,
        preferences.wasPremium,
        preferences.premiumEndedBannerSeen,
        _settled,
    ) { isPremium, wasPremium, seen, settled -> settled && wasPremium && !isPremium && !seen }

    init {
        scope.launch {
            premium.isPremium.collect { isPremium ->
                if (!isPremium) return@collect
                _settled.value = true
                // Remember that this user once had premium, so a lapse can be explained.
                if (!preferences.wasPremium.first()) preferences.setWasPremium(true)
                // A renewed subscriber gets the one-time explanation again if it lapses again.
                if (preferences.premiumEndedBannerSeen.first()) preferences.setPremiumEndedBannerSeen(false)
            }
        }
        scope.launch {
            delay(PREMIUM_SETTLE_MS)
            _settled.value = true
        }
    }

    fun dismiss() {
        scope.launch { preferences.setPremiumEndedBannerSeen(true) }
    }

    private companion object {
        const val PREMIUM_SETTLE_MS = 2_500L
    }
}

/** The lapse banner: a soft gold-accented card with a pill "Renew" and a dismiss X. */
@Composable
internal fun PremiumEndedBanner(
    onRenew: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier.fillMaxWidth(), color = colors.premiumGoldSurface) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = colors.premiumGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.home_premium_ended),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                PrimaryButton(text = stringResource(R.string.home_premium_renew), onClick = onRenew)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_action_dismiss), tint = colors.textSecondary)
            }
        }
    }
}
