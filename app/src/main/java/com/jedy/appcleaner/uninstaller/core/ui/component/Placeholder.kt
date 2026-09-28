package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.jedy.appcleaner.uninstaller.core.ui.theme.AppTheme
import com.jedy.appcleaner.uninstaller.core.ui.theme.Dimens

/**
 * A still, tonal loading block (design review §4: "loading placeholders at the same height as
 * loaded content"). Size it with [modifier] to the **final** height of what it stands in for, so
 * nothing jumps when data arrives: e.g. `Placeholder(Modifier.fillMaxWidth().height(heroHeight))`.
 * Reserve space the same way for anything that arrives late (a banner) instead of pushing content.
 */
@Composable
fun Placeholder(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(Dimens.cardRadius)) {
    Box(modifier.clip(shape).background(AppTheme.colors.surfaceMuted))
}

/**
 * A one-line text placeholder exactly one [style] line tall, for a number or label that is still
 * loading ("—" is shorter than the loaded text and makes the layout jump).
 */
@Composable
fun PlaceholderLine(style: TextStyle, modifier: Modifier = Modifier, widthFraction: Float = 0.5f) {
    val height = with(LocalDensity.current) { style.lineHeight.toDp() }
    Placeholder(
        modifier = modifier.fillMaxWidth(widthFraction).height(height),
        shape = RoundedCornerShape(Dimens.space8),
    )
}
