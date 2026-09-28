package com.jedy.appcleaner.uninstaller.feature.insights

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.format.formatBytes
import com.jedy.appcleaner.uninstaller.core.model.AppSize
import com.jedy.appcleaner.uninstaller.core.model.LargeApps
import com.jedy.appcleaner.uninstaller.core.ui.component.AppCard
import com.jedy.appcleaner.uninstaller.core.ui.component.AppIcon
import com.jedy.appcleaner.uninstaller.core.ui.component.AppRowDefaults
import com.jedy.appcleaner.uninstaller.core.ui.component.IconBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.component.ProBadge
import com.jedy.appcleaner.uninstaller.core.ui.component.RoundCheck
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import com.jedy.appcleaner.uninstaller.core.ui.theme.Severity
import com.jedy.appcleaner.uninstaller.core.ui.theme.SeverityRules
import com.jedy.appcleaner.uninstaller.core.ui.theme.color
import com.jedy.appcleaner.uninstaller.core.ui.theme.surface
import java.text.NumberFormat

/*
 * Insights building blocks on design system v2. Everything here is internal to the two premium
 * tabs; anything the whole app needs belongs in core/ui/component instead.
 */

/** "14 apps · 3.4 GB" — digits and units follow the in-app locale. */
@Composable
internal fun countAndSize(count: Int, bytes: Long): String = stringResource(
    R.string.insights_count_and_size,
    pluralStringResource(R.plurals.insights_apps_count, count, count),
    formatBytes(LocalContext.current, bytes),
)

/**
 * Counts from the previous value (0 on first show) up to [target]. The motion draws the eye to
 * the one number that matters; it animates a 0..1 progress rather than the Long itself, so byte
 * counts in the tens of GB never lose precision to a Float.
 */
@Composable
internal fun rememberCountUp(target: Long): Long {
    val progress = remember { Animatable(0f) }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(target) }
    val current = from + ((to - from) * progress.value.toDouble()).toLong()
    val latest by rememberUpdatedState(current)
    LaunchedEffect(target) {
        from = latest
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_UP_MILLIS, easing = FastOutSlowInEasing))
    }
    return current
}

/** "15%" of the phone, with one decimal under 10% so a real 0.4% never reads as "0%". */
@Composable
internal fun percentOf(part: Long, whole: Long): String? {
    if (whole <= 0 || part <= 0) return null
    val locale = LocalConfiguration.current.locales[0]
    val fraction = part.toDouble() / whole
    return remember(locale, fraction) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = if (fraction < 0.1) 1 else 0 }.format(fraction)
    }
}

/**
 * State (a): no Usage Access. Sells the value first (what you will see), then the reassurance
 * (it stays on your phone), then one clear action. Opens the disclosure (Screen 11), never
 * Settings directly — Play requires the prominent disclosure first.
 */
@Composable
internal fun AccessCard(
    icon: ImageVector,
    title: String,
    body: String,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        contentPadding = PaddingValues(24.dp),
    ) {
        IconBadge(icon = icon, size = 64.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        PrivacyNote()
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = stringResource(R.string.insights_allow_access),
            onClick = onRequestAccess,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(AppTheme.colors.accentSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = AppTheme.colors.accentText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.insights_private_note), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.accentText)
    }
}

/**
 * State (b), where free users decide to pay. The real finding in clear: the big number in
 * textPrimary, what it is, what it costs ("1.3 GB sitting idle"), then the green CTA — above the
 * preview so it is on screen on every phone height. A neutral outlined card with one quiet PRO
 * marker (design review §3.3: red is for destructive actions only, gold is quiet).
 *
 * @param number already count-up animated by the caller, e.g. "9 apps · 1.3 GB".
 * @param loss the cost framing; null when there is no honest figure to show.
 */
@Composable
internal fun LockedTeaser(
    number: String,
    caption: String,
    loss: String?,
    body: String,
    preview: List<PreviewRow>,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.space8)) {
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Dimens.space24)) {
            ProBadge()
            Spacer(Modifier.height(Dimens.space16))
            Text(
                text = number,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AppTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(Dimens.space4))
            Text(caption, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            if (loss != null) {
                Spacer(Modifier.height(Dimens.space16))
                Text(loss, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            }
            Spacer(Modifier.height(Dimens.space4))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
            Spacer(Modifier.height(Dimens.space24))
            PrimaryButton(
                text = stringResource(R.string.insights_see_which_apps),
                onClick = onUnlock,
                icon = Icons.Rounded.LockOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Dimens.space12))
        RedactedPreview(
            rows = preview,
            description = stringResource(R.string.insights_locked_rows_description),
        )
    }
}

/**
 * The redacted rows: clearly the user's real list, never readable. Labels and sizes are drawn
 * as bars, so no text exists on any API level or for a screen reader; each row's size bar has
 * its real length and the Large colour when it is Large. On API 31+ the rows (with real, unrecognisable icons) are
 * also blurred; below 31, where blur is a no-op, icons become solid placeholders instead.
 */
@Composable
private fun RedactedPreview(rows: List<PreviewRow>, description: String) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val largest = rows.maxOfOrNull { it.bytes }?.takeIf { it > 0 } ?: 1L
    val count = rows.size.coerceIn(MIN_PREVIEW_ROWS, MAX_PREVIEW_ROWS)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Column(
            modifier = Modifier.then(if (canBlur) Modifier.blur(8.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(count) { index ->
                val row = rows.getOrNull(index)
                RedactedRow(
                    packageName = row?.packageName?.takeIf { canBlur },
                    fraction = row?.let { it.bytes.toFloat() / largest } ?: PLACEHOLDER_FRACTIONS[index % PLACEHOLDER_FRACTIONS.size],
                    large = row?.let { LargeApps.isLarge(it.bytes) } ?: false,
                    index = index,
                )
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to AppTheme.colors.background)),
        )
    }
}

@Composable
private fun RedactedRow(packageName: String?, fraction: Float, large: Boolean, index: Int) {
    val ink = AppTheme.colors.textSecondary
    val barColor = if (large) AppTheme.colors.sizeBarLarge else AppTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppRowDefaults.Shape)
            .background(AppTheme.colors.surface)
            .border(Dimens.hairline, AppTheme.colors.border, AppRowDefaults.Shape)
            .height(Dimens.appRowHeight)
            .padding(horizontal = Dimens.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (packageName != null) {
            AppIcon(packageName)
        } else {
            Box(
                Modifier
                    .size(Dimens.appIconSize)
                    .clip(RoundedCornerShape(Dimens.appIconSize / 4))
                    .background(AppTheme.colors.surfaceMuted),
            )
        }
        Spacer(Modifier.width(Dimens.space12))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
            Bar(widthFraction = LABEL_WIDTHS[index % LABEL_WIDTHS.size], height = 12, color = ink.copy(alpha = 0.35f))
            Bar(widthFraction = fraction.coerceIn(0.05f, 1f), height = 6, color = barColor)
        }
        Spacer(Modifier.width(Dimens.space12))
        Bar(widthFraction = null, height = 12, color = ink.copy(alpha = 0.35f))
        Spacer(Modifier.width(Dimens.space12))
    }
}

@Composable
private fun Bar(widthFraction: Float?, height: Int, color: Color) {
    Box(
        Modifier
            .then(if (widthFraction != null) Modifier.fillMaxWidth(widthFraction) else Modifier.width(48.dp))
            .height(height.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * The premium summary at the top of a list: the big number (textPrimary), what it means, optional
 * [extra] content and the "Select all 9" pill. Selection covers the rows currently visible
 * under the search filter; nothing hidden is ever added silently.
 */
@Composable
internal fun SummaryCard(
    number: String,
    caption: String,
    visiblePackages: List<String>,
    selected: Set<String>,
    onSelectAll: (packages: List<String>, select: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = Dimens.space8),
        contentPadding = PaddingValues(Dimens.gutter),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AppTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textSecondary)
        extra?.invoke(this)
        if (visiblePackages.isNotEmpty()) {
            Spacer(Modifier.height(Dimens.space16))
            SelectAllPill(visiblePackages, selected, onSelectAll)
        }
    }
}

@Composable
private fun SelectAllPill(
    visiblePackages: List<String>,
    selected: Set<String>,
    onSelectAll: (packages: List<String>, select: Boolean) -> Unit,
) {
    val allSelected = visiblePackages.all { it in selected }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .background(AppTheme.colors.surfaceMuted)
            .toggleable(value = allSelected, role = Role.Checkbox, onValueChange = { onSelectAll(visiblePackages, !allSelected) })
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundCheck(checked = allSelected, onToggle = null, size = 22.dp)
        Text(
            text = pluralStringResource(R.plurals.insights_select_all, visiblePackages.size, visiblePackages.size),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textPrimary,
        )
    }
}

/**
 * The three size segments' colours, shared by every bar and the legend. Indicator fills only;
 * no red (design review §3.3: red is for destructive actions).
 */
private data class SegmentColors(val app: Color, val data: Color, val cache: Color)

@Composable
private fun segmentColors() = SegmentColors(
    app = AppTheme.colors.accent,
    data = AppTheme.colors.storageOther,
    cache = AppTheme.colors.warning,
)

/**
 * PRD §4 Screen 6: the stacked app / data / cache bar. [lengthFraction] is the app's share of
 * the largest app, so the ranking reads at a glance; a Row with weights mirrors itself in RTL.
 */
@Composable
internal fun StackedSizeBar(size: AppSize, lengthFraction: Float, modifier: Modifier = Modifier) {
    val total = size.totalBytes
    val colors = segmentColors()
    val length by animateFloatAsState(lengthFraction.coerceIn(0.03f, 1f), tween(700), label = "stackedBar")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.gaugeTrack),
    ) {
        Row(Modifier.fillMaxWidth(length).fillMaxHeight().clip(CircleShape)) {
            listOf(size.appBytes to colors.app, size.dataBytes to colors.data, size.cacheBytes to colors.cache)
                .filter { it.first > 0 && total > 0 }
                .forEach { (bytes, color) ->
                    Box(Modifier.weight(bytes.toFloat() / total).fillMaxHeight().background(color))
                }
        }
    }
}

/** Legend with the amounts: "● App 9.1 GB  ● Data 8.9 GB  ● Cache 640 MB". */
@Composable
internal fun BreakdownLegend(breakdown: AppSize, modifier: Modifier = Modifier) {
    val colors = segmentColors()
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(colors.app, stringResource(R.string.insights_legend_app), formatBytes(context, breakdown.appBytes), Modifier.weight(1f))
        LegendItem(colors.data, stringResource(R.string.insights_legend_data), formatBytes(context, breakdown.dataBytes), Modifier.weight(1f))
        LegendItem(colors.cache, stringResource(R.string.insights_legend_cache), formatBytes(context, breakdown.cacheBytes), Modifier.weight(1f))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, amount: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            // Full words: "Temp files" wraps in its third of the row rather than showing "…".
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.textPrimary,
        )
    }
}

/**
 * Where cache can be cleared. Cache is the one part of an app's size a user can reclaim without
 * uninstalling, so the Cache filter's summary says where (App info).
 */
@Composable
internal fun CacheHint(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = Dimens.space12), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.CleaningServices,
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Dimens.space8))
        Text(
            text = stringResource(R.string.insights_cache_callout_body),
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** Empty premium result: a win, so it celebrates — a check that pops in on a green card. */
@Composable
internal fun CelebrationCard(title: String, body: String, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    AppCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        color = AppTheme.colors.accentSurface,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(88.dp)
                .scale(pop.value)
                .clip(CircleShape)
                .background(AppTheme.colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(AppTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = AppTheme.colors.onAccent, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Loading: pulsing skeleton cards in the shape of the rows to come, so the layout never jumps. */
@Composable
internal fun InsightsLoading(text: String, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
        repeat(SKELETON_ROWS) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(pulse)
                    .clip(AppRowDefaults.Shape)
                    .background(AppTheme.colors.surface)
                    .height(Dimens.appRowHeight)
                    .padding(horizontal = Dimens.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Dimens.appIconSize).clip(RoundedCornerShape(Dimens.appIconSize / 4)).background(AppTheme.colors.surfaceMuted))
                Spacer(Modifier.width(Dimens.space12))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.space8)) {
                    Bar(LABEL_WIDTHS[index % LABEL_WIDTHS.size], 12, AppTheme.colors.surfaceMuted)
                    Bar(PLACEHOLDER_FRACTIONS[index % PLACEHOLDER_FRACTIONS.size], 6, AppTheme.colors.surfaceMuted)
                }
            }
        }
    }
}

private const val COUNT_UP_MILLIS = 900
private const val MIN_PREVIEW_ROWS = 3
private const val MAX_PREVIEW_ROWS = 5
private const val SKELETON_ROWS = 4
private val LABEL_WIDTHS = floatArrayOf(0.62f, 0.45f, 0.72f, 0.5f, 0.58f)
private val PLACEHOLDER_FRACTIONS = floatArrayOf(0.9f, 0.7f, 0.55f, 0.4f, 0.3f)
