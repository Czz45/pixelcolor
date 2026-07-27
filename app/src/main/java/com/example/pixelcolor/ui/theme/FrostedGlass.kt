package com.example.pixelcolor.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted glass background — apply to a simple container (no children that need to stay sharp).
 * For containers with text, use [FrostedGlassBox] instead.
 */
@Stable
fun Modifier.glassBg(
    baseColor: Color,
    alpha: Float = 0.85f
): Modifier {
    return this.background(baseColor.copy(alpha = alpha))
}

/**
 * Frosted glass container. Background is blurred, content stays sharp.
 * Uses a two-layer architecture:
 *   Layer 1: blurred tint (frosted effect)
 *   Layer 2: sharp content (text/icons unaffected)
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Black,
    blurRadius: Dp = 16.dp,
    alpha: Float = 0.7f,
    cornerAlpha: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .blur(blurRadius)
                .background(tintColor.copy(alpha = alpha))
        )
        if (cornerAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = cornerAlpha),
                            1f to Color.Transparent
                        )
                    )
            )
        }
        content()
    }
}
