package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = AppTheme.colors.background,
        contentColor = AppTheme.colors.textPrimary,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.gutter)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.language_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = Dimens.gutter),
            )
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
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

/** A short pill instead of Material's default handle, matching the v2 pill vocabulary. */
@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 16.dp)
            .size(width = 40.dp, height = 5.dp)
            .clip(CircleShape)
            .background(AppTheme.colors.textMuted.copy(alpha = 0.4f)),
    )
}

/**
 * One language as a soft v2 card: its own name in its own script (so a reader of that language
 * recognises it whatever the current UI language is), with the English name beneath as a
 * fallback. Selection tints the card accentSurface and fills the kit's [RoundCheck] — the same
 * "picked" signal as the app list, so the first screen already teaches the app's vocabulary.
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
        if (isSelected) colors.accentSurface else colors.surface,
        tween(200),
        label = "languageRow",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(Dimens.controlRadius))
            .background(container)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(start = Dimens.gutter, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.nativeName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = language.englishName,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) colors.accentText else colors.textSecondary,
            )
        }
        // The row owns the click and the radio semantics; the check is only the visual.
        RoundCheck(checked = isSelected, onToggle = null)
    }
}
