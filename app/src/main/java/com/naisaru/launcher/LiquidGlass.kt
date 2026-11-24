package com.naisaru.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val WhiteGlass = Color(0xFFF3FFF8)
internal val DeepGreen = Color(0xFF163727)

@Composable
internal fun LiquidPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.shadow(16.dp, shape, ambientColor = Color(0x77361E45), spotColor = Color(0x5528BFA5))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0.0f to Color(0xB86D8C77),
                    0.20f to Color(0x99496256),
                    0.55f to Color(0xB62B4B39),
                    0.83f to Color(0x99688474),
                    1.0f to Color(0xAA293F40)
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFFF6FFF0), Color(0xAAE9FFE8),
                        Color(0x5589D9D9), Color(0xD8F2C5FA),
                        Color(0xEEFDFFE9)
                    )
                ),
                shape
            ),
        content = content
    )
}

internal fun glyph(name: String): ImageVector = when (name) {
    "phone" -> Icons.Default.Phone
    "people" -> Icons.Default.People
    "chat" -> Icons.Default.Chat
    "play" -> Icons.Default.PlayCircle
    "game" -> Icons.Default.SportsEsports
    "tools" -> Icons.Default.Build
    "photo" -> Icons.Default.Image
    "heart" -> Icons.Default.Favorite
    "star" -> Icons.Default.Star
    "folder" -> Icons.Default.Folder
    "widgets" -> Icons.Default.Widgets
    "edit" -> Icons.Default.Edit
    "wallpaper" -> Icons.Default.Wallpaper
    "palette" -> Icons.Default.Palette
    "settings" -> Icons.Default.Settings
    else -> Icons.Default.Apps
}
