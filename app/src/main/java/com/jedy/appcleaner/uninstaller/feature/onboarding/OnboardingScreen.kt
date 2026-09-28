package com.jedy.appcleaner.uninstaller.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguageOptionRow
import com.jedy.appcleaner.uninstaller.core.ui.component.LanguagePickerSheet
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens
import kotlinx.coroutines.launch

private enum class OnboardingStep { LANGUAGE, SLIDES }

private data class OnboardingSlide(
    val titleRes: Int,
    val bodyRes: Int,
    val illustration: @Composable (active: Boolean, modifier: Modifier) -> Unit,
)

private val slides = listOf(
    OnboardingSlide(R.string.onboarding_1_title, R.string.onboarding_1_body) { _, m -> AppGridIllustration(m) },
    OnboardingSlide(R.string.onboarding_2_title, R.string.onboarding_2_body) { active, m -> StorageBarIllustration(active, m) },
    OnboardingSlide(R.string.onboarding_3_title, R.string.onboarding_3_body) { _, m -> ForgottenAppsIllustration(m) },
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
        Spacer(Modifier.height(Dimens.gutterLarge))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.tealSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = AppTheme.colors.teal,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.language_title),
            style = MaterialTheme.typography.headlineMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(top = Dimens.gutter),
        )
        Text(
            text = stringResource(R.string.language_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp, bottom = Dimens.gutter),
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
            modifier = Modifier.padding(vertical = Dimens.gutter),
        )
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
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start corner: top-left in LTR, top-right in RTL.
            LanguageButton(onClick = { languageSheetVisible = true })
            AnimatedVisibility(visible = !isLastPage, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = skip) {
                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.gutterLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                slide.illustration(
                    pagerState.currentPage == page,
                    // Shrinks on short screens so the copy is never pushed off.
                    Modifier
                        .fillMaxWidth(0.8f)
                        .weight(1f, fill = false)
                        .height(280.dp),
                )
                Text(
                    text = stringResource(slide.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimens.gutterLarge),
                )
                Text(
                    text = stringResource(slide.bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimens.gutterSmall),
                )
            }
        }

        val indicatorDescription = stringResource(
            R.string.onboarding_page_indicator,
            pagerState.currentPage + 1,
            slides.size,
        )
        PageIndicator(
            pageCount = slides.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Dimens.gutter)
                .semantics { contentDescription = indicatorDescription },
        )

        AnimatedContent(
            targetState = isLastPage,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.96f))
            },
            label = "onboardingAction",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutter)
                .padding(bottom = Dimens.gutter),
        ) { last ->
            Box(Modifier.fillMaxWidth()) {
                if (last) {
                    PrimaryButton(
                        text = stringResource(R.string.onboarding_get_started),
                        onClick = getStarted,
                    )
                } else {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(48.dp),
                        shape = RoundedCornerShape(Dimens.controlRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppTheme.colors.teal,
                            contentColor = AppTheme.colors.onTeal,
                        ),
                        contentPadding = PaddingValues(horizontal = 28.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_next),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonHeight + 2.dp),
        shape = RoundedCornerShape(Dimens.controlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.teal,
            contentColor = AppTheme.colors.onTeal,
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LanguageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AppTheme.colors.tealSurface,
        modifier = modifier
            .padding(4.dp)
            .size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = stringResource(R.string.language_change),
                tint = AppTheme.colors.teal,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val width by animateDpAsState(if (isActive) 22.dp else 8.dp, tween(240), label = "pageDot")
            val color by animateColorAsState(
                if (isActive) AppTheme.colors.teal else AppTheme.colors.border,
                tween(240),
                label = "pageDotColor",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
