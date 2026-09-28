package com.jedy.appcleaner.uninstaller.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme

/**
 * The spring green deepened to a forest tone for the hero's base. The card stays this dark in
 * both themes on purpose: the accent-filled [PrimaryButton] then pops off it (a bright green CTA
 * on a bright green card would vanish), white copy stays at ~9:1 contrast, and the gold
 * [ProBadge] reads as jewellery rather than noise. Brand-derived, so it is not a new palette colour.
 */
private val HeroDeep = Color(0xFF0B3B20)
private val HeroMid = Color(0xFF166534)

/**
 * Free users: "Unlock App Cleaner Pro" with three benefits and the one CTA on the screen. The
 * whole card routes to the paywall too, so a tap anywhere on the pitch works. Depth comes from
 * layered green glows painted behind the content, not from a shadow.
 */
@Composable
internal fun ProHeroCard(onUpgrade: () -> Unit, modifier: Modifier = Modifier) {
    val accent = AppTheme.colors.accent
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(HeroMid, HeroDeep)))
            .drawBehind {
                // Two soft glows in the top-end corner: the "layered tint" depth of the v2 look.
                val corner = Offset(if (rtl) 0f else size.width, 0f)
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), corner, size.width * 0.62f),
                    radius = size.width * 0.62f,
                    center = corner,
                )
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.34f, center = corner)
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.2f, center = corner)
            }
            .clickable(onClick = onUpgrade)
            .padding(24.dp),
    ) {
        ProBadge(strong = true)
        Text(
            text = stringResource(R.string.settings_premium_upgrade_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 14.dp),
        )
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HeroBenefit(stringResource(R.string.settings_pro_benefit_unused))
            HeroBenefit(stringResource(R.string.settings_pro_benefit_storage))
            HeroBenefit(stringResource(R.string.settings_pro_benefit_reminders))
        }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = stringResource(R.string.settings_premium_upgrade_cta),
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeroBenefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
    }
}

/**
 * Premium users: a calm confirmation, no sell. "Manage subscription" only appears for a real
 * store entitlement; the debug override has nothing in Play to manage.
 */
@Composable
internal fun ProActiveCard(hasStoreSubscription: Boolean, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier.fillMaxWidth(), color = colors.accentSurface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_premium_active_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        if (hasStoreSubscription) R.string.settings_premium_active_body
                        else R.string.settings_premium_active_debug
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (hasStoreSubscription) {
            SecondaryButton(
                text = stringResource(R.string.settings_manage_subscription),
                onClick = onManage,
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
            )
        }
    }
}
