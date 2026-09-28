package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jedy.appcleaner.uninstaller.R
import com.jedy.appcleaner.uninstaller.core.locale.AppLanguage
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * The language picker used from Settings (PRD §4 Screen 13); onboarding shows the same rows
 * full-screen as its first step (PRD §3).
 *
 * Tapping a row calls [onSelect] straight away. Callers hide the sheet and apply the language in
 * that same click handler, so on the next frame the sheet leaves the composition while
 * [com.jedy.appcleaner.uninstaller.core.locale.LocalizedContent] rebuilds in the new locale —
 * and, for Arabic or Hebrew, the theme flips the layout to RTL. The sheet is never redrawn
 * half-translated. It is its own window, and what keeps it (and every launcher inside the app)
 * working under a switched locale is that LocalizedContent's context wrapper keeps the Activity
 * in the context chain — see that file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.background,
        contentColor = AppTheme.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.gutter)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = Dimens.gutter),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = Dimens.gutter),
            ) {
                items(AppLanguage.entries, key = { it.tag }) { language ->
                    LanguageOptionRow(
                        language = language,
                        isSelected = language == selected,
                        onClick = { onSelect(language) },
                    )
                }
            }
        }
    }
}

/**
 * One language: its own name in its own script (so a reader of that language recognises it
 * whatever the current UI language is), with the English name beneath as a fallback.
 */
@Composable
fun LanguageOptionRow(
    language: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val container by animateColorAsState(
        if (isSelected) colors.accentSurface else colors.background,
        tween(180),
        label = "languageRow",
    )
    val border by animateColorAsState(
        if (isSelected) colors.accent else colors.border,
        tween(180),
        label = "languageBorder",
    )
    val shape = RoundedCornerShape(Dimens.controlRadius)
    Surface(
        shape = shape,
        color = container,
        border = BorderStroke(if (isSelected) 1.5.dp else Dimens.hairline, border),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) colors.accent else colors.textPrimary,
                )
                Text(
                    text = language.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.chipRadius))
                        .background(colors.accent)
                        .padding(3.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
