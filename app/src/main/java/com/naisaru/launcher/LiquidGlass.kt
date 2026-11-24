package com.naisaru.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val DeepGreen = Color(0xFF163727)
internal val GreenAccent = Color(0xFF76CE8B)
internal val NaisaruColors = darkColorScheme(
    primary = GreenAccent,
    onPrimary = Color(0xFF08170E),
    secondary = Color(0xFF9DDCAC),
    onSecondary = Color(0xFF0C1C12),
    background = Color(0xFF0C100D),
    onBackground = Color(0xFFF5FFF4),
    surface = Color(0xFF151B16),
    onSurface = Color(0xFFF5FFF4),
    surfaceVariant = Color(0xFF25382B),
    onSurfaceVariant = Color(0xFFE2F1E4),
    outline = Color(0xFF477655)
)

// Kept under the original component name for existing calls.
// The appearance is now the simple black-and-green Smart-style panel.
@Composable
internal fun LiquidPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    opacity: Float = .93f,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = .42f))
            .clip(shape)
            .background(Color(0xFF151C17).copy(alpha=opacity.coerceIn(.15f,1f)))
            .border(1.dp, Color(0xC3659B71), shape),
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
