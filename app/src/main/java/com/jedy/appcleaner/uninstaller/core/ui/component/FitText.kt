package com.jedy.appcleaner.uninstaller.core.ui.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Text that always shows its full words and never an "…" (owner rule: "full words, not the
 * letter then …"). For single-line slots such as a top-bar title or a trailing value: the text
 * first shrinks on one line, down to [minFontSize]; only if even that is too wide does it wrap,
 * up to [maxLines] lines. Use plain wrapping [Text] instead wherever the slot can simply grow.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 12.sp,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val oneLine = remember(text, style, minFontSize, maxWidth) {
            maxWidth == Constraints.Infinity || measurer.measure(
                text = text,
                style = style.copy(fontSize = minFontSize),
                maxLines = 1,
                softWrap = false,
            ).size.width <= maxWidth
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (oneLine) 1 else maxLines,
            autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
            textAlign = textAlign,
        )
    }
}
