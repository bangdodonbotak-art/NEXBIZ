package id.nexbiz.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NexbizPosDarkColorScheme = darkColorScheme(
    primary = ElectricCyan400,
    onPrimary = DeepNavy950,
    primaryContainer = DeepNavy700,
    onPrimaryContainer = ElectricCyan100,
    secondary = ElectricCyan500,
    onSecondary = DeepNavy950,
    secondaryContainer = ElectricCyan900,
    onSecondaryContainer = ElectricCyan100,
    tertiary = SyncEmerald500,
    onTertiary = DeepNavy950,
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = SyncEmerald100,
    background = DeepNavy950,
    onBackground = Color(0xFFE2E8F0),
    surface = DeepNavy900,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DeepNavy800,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = SyncCrimson500,
    onError = Color.White
)

private val NexbizPosLightColorScheme = lightColorScheme(
    primary = DeepNavy900,
    onPrimary = Color.White,
    primaryContainer = DeepNavy100,
    onPrimaryContainer = DeepNavy900,
    secondary = ElectricCyan600,
    onSecondary = Color.White,
    secondaryContainer = ElectricCyan100,
    onSecondaryContainer = ElectricCyan900,
    tertiary = SyncEmerald500,
    onTertiary = Color.White,
    tertiaryContainer = SyncEmerald100,
    onTertiaryContainer = Color(0xFF064E3B),
    background = DeepNavy50,
    onBackground = DeepNavy900,
    surface = Color.White,
    onSurface = DeepNavy900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = SyncCrimson500,
    onError = Color.White
)

val NexbizTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun NexbizPosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NexbizPosDarkColorScheme else NexbizPosLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexbizTypography,
        content = content
    )
}
