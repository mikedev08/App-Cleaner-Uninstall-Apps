package com.jedy.appcleaner.uninstaller.feature.uninstall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.BigNumber
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.SecondaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.StorageGauge
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.data.uninstall.BatchSummary
import com.jedy.appcleaner.uninstaller.data.uninstall.FailureReason
import com.jedy.appcleaner.uninstaller.data.uninstall.ItemState
import com.jedy.appcleaner.uninstaller.data.uninstall.NotRemovedItem
import com.jedy.appcleaner.uninstaller.feature.history.SnapshotAppIcon
import kotlinx.coroutines.delay

/**
 * CONTRACT (frozen signature). PRD §4 Screen 10. [onRetry] receives a new batch (the "Try again"
 * apps) to run through the progress screen. No ads in this build.
 */
@Composable
fun UninstallResultScreen(
    batchId: Long,
    onDone: () -> Unit,
    onOpenUnused: () -> Unit,
    onRetry: (newBatchId: Long) -> Unit,
) {
    val viewModel: UninstallResultViewModel = hiltViewModel()
    LaunchedEffect(batchId) { viewModel.bind(batchId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(viewModel) { viewModel.retried.collect { latestOnRetry(it) } }
    BackHandler(onBack = onDone)

    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val summary = state.summary
            if (summary != null) {
                Spacer(Modifier.height(Dimens.space24))
                if (summary.removedCount > 0) {
                    FreedHero(summary, state.storageDrop)
                } else {
                    NothingRemovedHeader()
                }
                Notes(summary)
                val notRemoved = summary.notRemoved
                if (notRemoved.isNotEmpty()) {
                    Spacer(Modifier.height(Dimens.space24))
                    NotRemovedCard(
                        items = notRemoved,
                        retryEnabled = !state.isRetrying,
                        onRetry = viewModel::retry,
                        onOpenSecuritySettings = { openSecuritySettings(context) },
                    )
                }
                if (state.teaser.count > 0) {
                    Spacer(Modifier.height(if (notRemoved.isNotEmpty()) Dimens.cardGap else Dimens.space24))
                    UnusedTeaserCard(state.teaser, onOpenUnused)
                }
                Spacer(Modifier.height(Dimens.gutter))
            }
        }
        PrimaryButton(
            text = stringResource(R.string.action_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.gutterSmall),
        )
    }
}

/**
 * The reward moment, in three beats: the gauge sweeps in to how full the phone *was*, the freed
 * number counts up, then the gauge drops to how full it is *now* with a short burst of confetti.
 * Every number is real ([StorageDrop] is read from the device). It plays once per batch — a
 * rotation or coming back to the screen shows the settled state — and not at all with reduced
 * motion, where the screen simply renders the final values.
 */
@Composable
private fun FreedHero(summary: BatchSummary, drop: StorageDrop?) {
    val colors = AppTheme.colors
    val reducedMotion = rememberReducedMotion()
    val target = summary.freedBytes
    var played by rememberSaveable(summary.batch.batchId) { mutableStateOf(reducedMotion) }
    var dropped by remember { mutableStateOf(played) }
    var burst by remember { mutableStateOf(false) }

    val counter = remember(summary.batch.batchId) { Animatable(if (played) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (played) {
            counter.snapTo(target.toFloat())
        } else {
            counter.animateTo(target.toFloat(), tween(durationMillis = 1_200, easing = FastOutSlowInEasing))
        }
    }
    // The drop waits for the gauge to have swept in to "before" (StorageGauge takes 1.1 s).
    LaunchedEffect(drop != null) {
        if (drop == null || played) return@LaunchedEffect
        delay(1_250)
        dropped = true
        burst = true
        played = true
    }
    val shownBytes = if (played && !burst) target else counter.value.toLong()

    Box(contentAlignment = Alignment.Center) {
        if (drop != null) {
            val fraction = if (dropped) drop.afterFraction else drop.beforeFraction
            // The percentage follows the arc (same 1.1 s curve as StorageGauge), so text and ring agree.
            val pct = remember { Animatable(if (played) fraction else 0f) }
            LaunchedEffect(fraction) {
                if (played && !burst) pct.snapTo(fraction)
                else pct.animateTo(fraction, tween(1_100, easing = FastOutSlowInEasing))
            }
            val shownFraction = pct.value
            StorageGauge(
                usedFraction = fraction,
                severity = SeverityRules.storage(fraction),
                size = 220.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.uninstall_result_percent, ResultMath.percent(shownFraction)),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.uninstall_result_gauge_caption),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                IconBadge(icon = Icons.Rounded.CheckCircle, size = 96.dp)
            }
        }
        if (burst && !reducedMotion) {
            ConfettiBurst(Modifier.matchParentSize())
        }
    }
    Spacer(Modifier.height(Dimens.gutter))
    BigNumber(
        value = stringResource(R.string.uninstall_result_freed, sizeText(shownBytes, summary.freedIsEstimate)),
        caption = null,
        color = colors.positive,
    )
    Spacer(Modifier.height(6.dp))
    val removed = summary.removedCount
    val removedText = pluralStringResource(R.plurals.uninstall_result_removed, removed, removed)
    Text(
        text = if (drop != null) {
            stringResource(
                R.string.uninstall_result_subline, removedText,
                stringResource(R.string.uninstall_result_now_full, drop.afterPercent),
            )
        } else {
            removedText
        },
        style = MaterialTheme.typography.titleMedium,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NothingRemovedHeader() {
    IconBadge(icon = Icons.Rounded.Info, size = 88.dp)
    Spacer(Modifier.height(Dimens.gutter))
    Text(
        text = stringResource(R.string.uninstall_result_none_title),
        style = MaterialTheme.typography.headlineMedium,
        color = AppTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.uninstall_result_none_body),
        style = MaterialTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Notes(summary: BatchSummary) {
    val notAttempted = summary.notRemoved.any { it.state == ItemState.WAITING || it.state == ItemState.IN_PROGRESS }
    if (summary.batch.stoppedEarly && notAttempted) {
        Spacer(Modifier.height(Dimens.gutterSmall))
        Text(
            text = stringResource(R.string.uninstall_result_stopped_note),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    val already = summary.alreadyRemovedCount
    if (already > 0) {
        Spacer(Modifier.height(Dimens.gutterSmall))
        Text(
            text = pluralStringResource(R.plurals.uninstall_result_already_removed, already, already),
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NotRemovedCard(
    items: List<NotRemovedItem>,
    retryEnabled: Boolean,
    onRetry: (List<String>) -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Rounded.ReportProblem, severity = Severity.WARNING)
            Spacer(Modifier.width(12.dp))
            Text(
                text = pluralStringResource(R.plurals.uninstall_result_not_removed, items.size, items.size),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.uninstall_cd_hide_details else R.string.uninstall_cd_show_details,
                ),
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { entry ->
                    NotRemovedRow(
                        entry = entry,
                        retryEnabled = retryEnabled,
                        onRetry = { onRetry(listOf(entry.item.packageName)) },
                        onOpenSecuritySettings = onOpenSecuritySettings,
                    )
                }
                val retryable = items.filter { it.canRetry }
                if (retryable.size >= 2) {
                    SecondaryButton(
                        text = stringResource(R.string.uninstall_retry_all),
                        onClick = { if (retryEnabled) onRetry(retryable.map { it.item.packageName }) },
                        icon = Icons.Rounded.Refresh,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotRemovedRow(
    entry: NotRemovedItem,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnapshotAppIcon(iconPath = entry.item.iconPath, packageName = entry.item.packageName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.item.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.reasonText(), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entry.reason == FailureReason.DEVICE_ADMIN) {
                RowPill(text = stringResource(R.string.action_open_settings), onClick = onOpenSecuritySettings, filled = false)
            }
            // Never for BLOCKED: retrying cannot succeed (PRD §6 item 2).
            if (entry.canRetry) {
                RowPill(text = stringResource(R.string.action_retry), onClick = onRetry, enabled = retryEnabled)
            }
        }
    }
}

/**
 * The next step, from real numbers only: the apps the user still hasn't opened and the space they
 * still hold. A neutral card with the size in green: space you *can* get back is good news, and
 * red is reserved for destructive actions (design review §3.3). Never shown when there is nothing.
 */
@Composable
private fun UnusedTeaserCard(teaser: UnusedTeaser, onOpenUnused: () -> Unit) {
    val colors = AppTheme.colors
    val size = sizeText(teaser.bytes, teaser.isEstimate)
    val sentence = pluralStringResource(R.plurals.uninstall_result_teaser_loss, teaser.count, teaser.count, size)
    val emphasis = MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = colors.positive)
    val text = remember(sentence, size, emphasis) {
        buildAnnotatedString {
            append(sentence)
            val at = sentence.indexOf(size)
            if (at >= 0) addStyle(emphasis, at, at + size.length)
        }
    }
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenUnused) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(icon = Icons.Rounded.HourglassBottom, size = 44.dp)
            Spacer(Modifier.width(Dimens.space12))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.uninstall_result_teaser_days, teaser.thresholdDays, teaser.thresholdDays,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(Dimens.space12))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.uninstall_result_teaser_action),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentText,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.accentText)
        }
    }
}

/** PRD §6 item 1: device admins are turned off from the Security page. */
private fun openSecuritySettings(context: Context) {
    val intents = listOf(Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next, more generic page.
        }
    }
}
