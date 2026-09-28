package com.jedy.appcleaner.uninstaller.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.InstalledApp
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.PremiumCrown
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SectionShape = RoundedCornerShape(Dimens.cardRadius)

/**
 * PRD §4 Screen 7, the App Details sheet, opened from a row on any tab.
 *
 * Free users see the APK size and a crown line to unlock data & cache; premium users see last
 * opened and the StorageStatsManager App / Data / Cache split. "App info" is labelled as the
 * place to clear cache because that is the only honest route for it (PRD §0 decision 5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailsSheet(
    details: AppDetailsUi,
    onDismiss: () -> Unit,
    onUnlockSizes: () -> Unit,
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter)
                .padding(bottom = Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gutter),
        ) {
            DetailsHeader(app)
            FactsSection(details, onUnlockSizes = onUnlockSizes, onRequestUsageAccess = onRequestUsageAccess)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(SectionShape)
                    .border(Dimens.hairline, colors.border, SectionShape),
            ) {
                launchIntent?.let { intent ->
                    ActionRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.home_details_open)) {
                        context.startSafely(intent)
                    }
                    HorizontalDivider(thickness = Dimens.hairline, color = colors.border)
                }
                ActionRow(Icons.Rounded.Info, stringResource(R.string.home_details_app_info)) {
                    context.startSafely(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                    )
                }
                if (app.isFromPlay) {
                    HorizontalDivider(thickness = Dimens.hairline, color = colors.border)
                    ActionRow(Icons.Rounded.Shop, stringResource(R.string.home_details_play_store)) {
                        context.openPlayListing(app.packageName)
                    }
                }
            }
            Button(
                onClick = onUninstall,
                modifier = Modifier.fillMaxWidth().height(Dimens.buttonHeight),
                shape = RoundedCornerShape(Dimens.controlRadius),
                colors = ButtonDefaults.buttonColors(containerColor = colors.removeRed, contentColor = colors.onRemoveRed),
            ) {
                Text(stringResource(R.string.home_uninstall), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailsHeader(app: InstalledApp) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(packageName = app.packageName, size = 56.dp)
        Spacer(Modifier.width(Dimens.gutter))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            app.versionName?.takeIf { it.isNotBlank() }?.let { version ->
                Text(
                    text = stringResource(R.string.home_details_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(if (app.isFromPlay) R.string.home_details_from_play else R.string.home_details_from_other),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

/** Dates, usage and sizes — each gated exactly as Screen 7 describes. */
@Composable
private fun FactsSection(
    details: AppDetailsUi,
    onUnlockSizes: () -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    val date = rememberDayMonthYearFormat()
    val monthYear = rememberMonthYearFormat()
    val app = details.app
    val entitled = details.isPremium && details.hasUsageAccess

    Section {
        FactRow(stringResource(R.string.home_details_installed), date(app.firstInstallTime))
        FactRow(stringResource(R.string.home_details_updated), date(app.lastUpdateTime))
        if (entitled) {
            val lastOpened = when {
                details.lastUsedAt != null -> date(details.lastUsedAt)
                details.usageWindowStart > 0 -> stringResource(R.string.home_details_not_opened_since, monthYear(details.usageWindowStart))
                else -> stringResource(R.string.home_details_no_usage)
            }
            FactRow(stringResource(R.string.home_details_last_opened), lastOpened)
        }
    }

    Section {
        val size = details.size
        when {
            entitled && size != null -> {
                FactRow(stringResource(R.string.home_details_app_size), formatBytes(context, size.appBytes))
                FactRow(stringResource(R.string.home_details_data_size), formatBytes(context, size.dataBytes))
                FactRow(stringResource(R.string.home_details_cache_size), formatBytes(context, size.cacheBytes))
                FactRow(stringResource(R.string.home_details_total_size), formatBytes(context, size.totalBytes), emphasized = true)
            }
            entitled && details.isMeasuring -> {
                FactRow(stringResource(R.string.home_details_apk_size), formatBytes(context, app.apkBytes))
                FactRow(stringResource(R.string.home_details_data_cache_size), stringResource(R.string.home_details_size_measuring))
            }
            entitled -> {
                // PRD §6 item 10: an unmounted volume is "Size unavailable", never 0 B.
                FactRow(stringResource(R.string.home_details_apk_size), formatBytes(context, app.apkBytes))
                FactRow(stringResource(R.string.home_details_data_cache_size), stringResource(R.string.home_details_size_unavailable))
            }
            details.isPremium -> {
                FactRow(stringResource(R.string.home_details_apk_size), formatBytes(context, app.apkBytes))
                LinkRow(
                    text = stringResource(R.string.home_details_allow_access),
                    leading = {
                        Icon(Icons.Rounded.Timeline, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(18.dp))
                    },
                    onClick = onRequestUsageAccess,
                )
            }
            else -> {
                FactRow(stringResource(R.string.home_details_apk_size), formatBytes(context, app.apkBytes))
                LinkRow(
                    text = stringResource(R.string.home_details_unlock_sizes),
                    leading = { PremiumCrown(size = 18.dp) },
                    onClick = onUnlockSizes,
                )
            }
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(colors.surface)
            .border(Dimens.hairline, colors.border, SectionShape)
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun FactRow(label: String, value: String, emphasized: Boolean = false) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = Dimens.gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Dimens.gutterSmall))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun LinkRow(text: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.buttonHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Dimens.gutter))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
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
