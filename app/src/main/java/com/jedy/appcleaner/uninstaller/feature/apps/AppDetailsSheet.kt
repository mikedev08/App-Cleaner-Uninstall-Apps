package com.jedy.appcleaner.uninstaller.feature.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SizeBar
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRD §4 Screen 7, the App Details sheet, opened from a row on any tab. A big icon and the size
 * (neutral text, with the one "Large" chip when it is) lead; "What's using space" splits App / Data
 * / Cache for every user (it needs Usage Access, never Pro). "App info" is labelled as the
 * place to clear cache because that is the only honest route for it (PRD §0 decision 5).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppDetailsSheet(
    details: AppDetailsUi,
    onDismiss: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onUninstall: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val app = details.app
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Some packages (widget packs, keyboards) have no launcher entry: then there is no "Open".
    val launchIntent by produceState<Intent?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getLaunchIntentForPackage(app.packageName) }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter)
                .padding(bottom = Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gutter),
        ) {
            DetailsHero(details)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                launchIntent?.let { intent ->
                    SecondaryButton(stringResource(R.string.home_details_open), onClick = { context.startSafely(intent) }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                }
                SecondaryButton(
                    text = stringResource(R.string.apps_details_app_info),
                    onClick = {
                        context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))
                    },
                    icon = Icons.Rounded.Info,
                )
                if (app.isFromPlay) {
                    SecondaryButton(stringResource(R.string.apps_details_play), onClick = { context.openPlayListing(app.packageName) }, icon = Icons.Rounded.Shop)
                }
            }
            SpaceSection(details, onRequestUsageAccess = onRequestUsageAccess)
            FactsSection(details)
            Text(
                text = stringResource(R.string.apps_details_cache_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            PrimaryButton(
                text = stringResource(R.string.home_uninstall),
                onClick = onUninstall,
                destructive = true,
                icon = Icons.Rounded.DeleteOutline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Icon, name, source, and the size as the headline, plus the "Large" chip under the shared rule. */
@Composable
private fun DetailsHero(details: AppDetailsUi) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val app = details.app
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(packageName = app.packageName, size = 84.dp)
        Spacer(Modifier.size(14.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val source = stringResource(if (app.isFromPlay) R.string.home_details_from_play else R.string.home_details_from_other)
        val version = app.versionName?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_details_version, it) }
        Text(
            text = listOfNotNull(version, source).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = formatBytes(context, details.displayBytes),
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(if (details.size != null) R.string.apps_details_total_caption else R.string.apps_details_apk_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (details.isLarge) {
            Spacer(Modifier.size(Dimens.space8))
            LargeChip()
        }
    }
}

/**
 * "What's using space": App / Data / Cache bars. Free for everyone; without Usage Access it is
 * the "Allow access" link (the sizes need it), never a paywall.
 */
@Composable
private fun SpaceSection(details: AppDetailsUi, onRequestUsageAccess: () -> Unit) {
    val colors = AppTheme.colors
    val size = details.size
    Section(title = stringResource(R.string.apps_details_whats_using)) {
        when {
            size != null -> SizeBreakdown(size)
            details.hasUsageAccess && details.isMeasuring -> Text(stringResource(R.string.home_details_size_measuring), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            // PRD §6 item 10: an unmounted volume is "Size unavailable", never 0 B.
            details.hasUsageAccess -> Text(stringResource(R.string.home_details_size_unavailable), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            else -> LinkRow(
                text = stringResource(R.string.home_details_allow_access),
                leading = { Icon(Icons.Rounded.Timeline, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp)) },
                onClick = onRequestUsageAccess,
            )
        }
    }
}

@Composable
private fun SizeBreakdown(size: AppSize) {
    val context = LocalContext.current
    val largest = maxOf(size.appBytes, size.dataBytes, size.cacheBytes).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SizeLine(stringResource(R.string.home_details_app_size), formatBytes(context, size.appBytes), size.appBytes.toFloat() / largest)
        SizeLine(stringResource(R.string.home_details_data_size), formatBytes(context, size.dataBytes), size.dataBytes.toFloat() / largest)
        SizeLine(stringResource(R.string.home_details_cache_size), formatBytes(context, size.cacheBytes), size.cacheBytes.toFloat() / largest)
    }
}

@Composable
private fun SizeLine(label: String, value: String, fraction: Float) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
        }
        // Neutral green bars: this is a breakdown of one app, not a warning.
        SizeBar(fraction = fraction, height = 8.dp)
    }
}

/** Dates and usage. Last opened is premium with access, as before. */
@Composable
private fun FactsSection(details: AppDetailsUi) {
    val date = rememberDayMonthYearFormat()
    val monthYear = rememberMonthYearFormat()
    val app = details.app
    Section(title = null) {
        // Preinstalled apps report the epoch: no "Installed 1 Jan 1970".
        if (AppsListLogic.isKnownDate(app.firstInstallTime)) {
            FactRow(stringResource(R.string.home_details_installed), date(app.firstInstallTime))
        }
        if (AppsListLogic.isKnownDate(app.lastUpdateTime)) {
            FactRow(stringResource(R.string.home_details_updated), date(app.lastUpdateTime))
        }
        if (details.isPremium && details.hasUsageAccess) {
            val lastOpened = when {
                details.lastUsedAt != null -> date(details.lastUsedAt)
                // Installed after usage history begins: "not since at least <before install>" is nonsense.
                details.usageWindowStart > 0 && app.firstInstallTime <= details.usageWindowStart -> stringResource(R.string.home_details_not_opened_since, monthYear(details.usageWindowStart))
                else -> stringResource(R.string.home_details_no_usage)
            }
            FactRow(stringResource(R.string.home_details_last_opened), lastOpened)
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Spacer(Modifier.size(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Dimens.gutterSmall))
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
    }
}

@Composable
private fun LinkRow(text: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Dimens.controlRadius))
            .background(colors.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** `market://` first (the Play app); the web listing when no store app handles it. */
private fun Context.openPlayListing(packageName: String) {
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        .setPackage(InstalledApp.PLAY_STORE_PACKAGE)
    if (startSafely(market)) return
    if (startSafely(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))) return
    startSafely(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
}
