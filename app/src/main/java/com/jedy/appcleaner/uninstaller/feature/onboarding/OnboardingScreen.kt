package com.jedy.appcleaner.uninstaller.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguageOptionRow
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguagePickerSheet
import com.jedy.appcleaner.uninstaller.core.ui.component.PrimaryButton
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OnboardingStep { LANGUAGE, SLIDES }

private data class OnboardingSlide(
    val titleRes: Int,
    val bodyRes: Int,
    val illustration: @Composable (modifier: Modifier) -> Unit,
)

private val slides = listOf(
    OnboardingSlide(R.string.onboarding_1_title, R.string.onboarding_1_body) { AppGridIllustration(it) },
    OnboardingSlide(R.string.onboarding_2_title, R.string.onboarding_2_body) { StorageGaugeIllustration(it) },
    OnboardingSlide(R.string.onboarding_3_title, R.string.onboarding_3_body) { ForgottenAppsIllustration(it) },
)

/**
 * CONTRACT (frozen signature). PRD §3: full-screen language selector -> 3-slide carousel ->
 * notification prompt on "Get Started" -> [onFinished]. The navigation layer then shows the
 * dismissible onboarding paywall once.
 *
 * The language step defaults to the device locale (MainViewModel resolves it) and applies each
 * tap immediately through [onLanguageSelected], so Arabic or Hebrew flips the layout to RTL
 * under the user's finger — the carousel that follows is already in their language.
 */
@Composable
fun OnboardingScreen(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFinished: (skipped: Boolean) -> Unit,
) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    var step by rememberSaveable { mutableStateOf(OnboardingStep.LANGUAGE) }

    Surface(modifier = Modifier.fillMaxSize(), color = AppTheme.colors.background) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "onboardingStep",
        ) { current ->
            when (current) {
                OnboardingStep.LANGUAGE -> LanguageStep(
                    language = language,
                    onLanguageSelected = onLanguageSelected,
                    onContinue = {
                        // PRD §3 step 1 would show the UMP consent dialog here; this build has no ads, so no consent SDK.
                        step = OnboardingStep.SLIDES
                    },
                )
                OnboardingStep.SLIDES -> SlidesStep(
                    language = language,
                    onLanguageSelected = onLanguageSelected,
                    onStepViewed = { viewModel.onStepViewed(it, language) },
                    onBackToLanguage = { step = OnboardingStep.LANGUAGE },
                    onFinished = onFinished,
                )
            }
        }
    }
}

@Composable
private fun LanguageStep(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Dimens.gutter),
    ) {
        Spacer(Modifier.height(Dimens.space24))
        GlobeBadge()
        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.displaySmall,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Dimens.gutter),
        )
        Text(
            text = stringResource(R.string.language_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = Dimens.gutter),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(AppLanguage.entries, key = { it.tag }) { option ->
                LanguageOptionRow(
                    language = option,
                    isSelected = option == language,
                    onClick = { onLanguageSelected(option) },
                )
            }
        }
        PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.gutter),
        )
    }
}

/** The globe on two stacked green tints: the same layered-depth language as the slides. */
@Composable
private fun GlobeBadge() {
    val colors = AppTheme.colors
    Box(Modifier.size(72.dp)) {
        Box(
            Modifier
                .offset(x = 8.dp, y = 8.dp)
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.accent.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Language, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun SlidesStep(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onStepViewed: (Int) -> Unit,
    onBackToLanguage: () -> Unit,
    onFinished: (skipped: Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = slides::size)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isLastPage = pagerState.currentPage == slides.lastIndex
    var languageSheetVisible by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { onStepViewed(pagerState.currentPage + 1) }

    // PRD §3 step 2: the prompt comes on "Get Started"; onboarding completes whatever the answer.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onFinished(false)
    }
    val getStarted: () -> Unit = {
        if (!finishing) {
            finishing = true
            val needsPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPrompt) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinished(false)
        }
    }
    val nextPage: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }
    val skip: () -> Unit = {
        if (!finishing) {
            finishing = true
            onFinished(true)
        }
    }

    // Back walks the carousel backwards, then returns to the language step.
    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onBackToLanguage()
        }
    }

    if (languageSheetVisible) {
        LanguagePickerSheet(
            selected = language,
            onSelect = {
                languageSheetVisible = false
                onLanguageSelected(it)
            },
            onDismiss = { languageSheetVisible = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.topBarHeight)
                // The language circle sits on the 20dp gutter; "Skip" has 12dp of its own padding,
                // so its text lands on the gutter too.
                .padding(start = Dimens.gutter, end = Dimens.gutter - SkipPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start corner: top-left in LTR, top-right in RTL.
            LanguageButton(onClick = { languageSheetVisible = true })
            AnimatedVisibility(visible = !isLastPage, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = skip)
                        .heightIn(min = Dimens.minTouchTarget)
                        .wrapContentHeight()
                        .padding(horizontal = SkipPadding),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            SlidePage(slides[page])
        }

        val indicatorDescription = stringResource(
            R.string.onboarding_page_indicator,
            pagerState.currentPage + 1,
            slides.size,
        )
        PillIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Dimens.gutter)
                .semantics { contentDescription = indicatorDescription },
        )

        // "Next" is a pill at the end edge; on the last slide it stretches into the full-width
        // "Get Started", so the one primary action morphs instead of being swapped out.
        val widthFraction by animateFloatAsState(
            if (isLastPage) 1f else 0.45f,
            spring(dampingRatio = 0.8f, stiffness = 400f),
            label = "ctaWidth",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutter)
                .padding(bottom = Dimens.gutter),
            contentAlignment = Alignment.CenterEnd,
        ) {
            PrimaryButton(
                text = stringResource(if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next),
                onClick = if (isLastPage) getStarted else nextPage,
                modifier = Modifier.fillMaxWidth(widthFraction),
            )
        }
    }
}

/**
 * One slide. The copy rises in just after the illustration starts, so the eye lands on the
 * picture first. Composed fresh each time the page scrolls into view, which replays it.
 */
@Composable
private fun SlidePage(slide: OnboardingSlide) {
    val copy = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(180)
        copy.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        slide.illustration(
            // Shrinks on short screens so the copy is never pushed off.
            Modifier
                .fillMaxWidth(0.9f)
                .weight(1f, fill = false)
                .height(300.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = copy.value
                translationY = (1f - copy.value) * 24.dp.toPx()
            },
        ) {
            Text(
                text = stringResource(slide.titleRes),
                style = MaterialTheme.typography.headlineLarge,
                color = AppTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.space24),
            )
            Text(
                text = stringResource(slide.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.space12),
            )
        }
    }
}

private val SkipPadding = 12.dp

/** 48dp, the minimum touch target (design review §4). */
@Composable
private fun LanguageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.minTouchTarget)
            .clip(CircleShape)
            .background(AppTheme.colors.accentSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = stringResource(R.string.language_change),
            tint = AppTheme.colors.accentText,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The pager indicator as a pill that stretches toward the page being dragged in. It tracks the
 * live scroll offset rather than the settled page, so it moves with the finger.
 */
@Composable
private fun PillIndicator(pagerState: PagerState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val inactive = colors.textMuted.copy(alpha = 0.35f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pagerState.pageCount) { index ->
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val nearness = 1f - abs(position - index).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(width = lerp(8.dp, 30.dp, nearness), height = 8.dp)
                    .clip(CircleShape)
                    .background(lerp(inactive, colors.accent, nearness)),
            )
        }
    }
}
