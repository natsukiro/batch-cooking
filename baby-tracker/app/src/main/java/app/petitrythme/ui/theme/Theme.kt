package app.petitrythme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.petitrythme.data.Category
import app.petitrythme.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFFB4507E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E5),
    onPrimaryContainer = Color(0xFF3E0021),
    secondary = Color(0xFF6A5FC1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF221558),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A1D),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A1D),
    surfaceVariant = Color(0xFFF3E4E8),
    onSurfaceVariant = Color(0xFF6B5B61),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EE),
    surfaceContainer = Color(0xFFFBEBE8),
    surfaceContainerHigh = Color(0xFFF6E5E3),
    surfaceContainerHighest = Color(0xFFF0DFDE),
    outline = Color(0xFFA08F95),
    outlineVariant = Color(0xFFE2D0D5),
    error = Color(0xFFBA1A1A),
)

// Sombre et peu lumineux : pensé pour les tétées de nuit.
private val Dark = darkColorScheme(
    primary = Color(0xFFF2A7C6),
    onPrimary = Color(0xFF5E1139),
    primaryContainer = Color(0xFF7A2A51),
    onPrimaryContainer = Color(0xFFFFD9E5),
    secondary = Color(0xFFC7BEFF),
    onSecondary = Color(0xFF332879),
    secondaryContainer = Color(0xFF4A4091),
    onSecondaryContainer = Color(0xFFE5DEFF),
    background = Color(0xFF151216),
    onBackground = Color(0xFFEBDFE3),
    surface = Color(0xFF151216),
    onSurface = Color(0xFFEBDFE3),
    surfaceVariant = Color(0xFF3A3237),
    onSurfaceVariant = Color(0xFFC9B9BF),
    surfaceContainerLowest = Color(0xFF100D11),
    surfaceContainerLow = Color(0xFF1D191E),
    surfaceContainer = Color(0xFF221D22),
    surfaceContainerHigh = Color(0xFF2C262C),
    surfaceContainerHighest = Color(0xFF373037),
    outline = Color(0xFF94848A),
    outlineVariant = Color(0xFF4B4146),
)

private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val ChronoStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)

@Composable
fun PetitRythmeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}

/** Couleur d'accent d'une catégorie, adaptée au thème clair/sombre. */
@Composable
@ReadOnlyComposable
fun Category.color(): Color {
    val dark = MaterialTheme.colorScheme.background.lum() < 0.5f
    return when (this) {
        Category.FEED -> if (dark) Color(0xFFD96A93) else Color(0xFFD2557F)
        Category.DIAPER -> if (dark) Color(0xFFB98A22) else Color(0xFFA87807)
        Category.SLEEP -> if (dark) Color(0xFF8689E6) else Color(0xFF5B5FCF)
        Category.CARE -> if (dark) Color(0xFF7CCBE6) else Color(0xFF2387AA)
        Category.HEALTH -> if (dark) Color(0xFFF39686) else Color(0xFFCB4B39)
        Category.GROWTH -> if (dark) Color(0xFF82D0A2) else Color(0xFF2F8A55)
        Category.NOTE -> if (dark) Color(0xFFB5AEB8) else Color(0xFF746A78)
    }
}

/** Fond doux teinté par la couleur de catégorie. */
@Composable
@ReadOnlyComposable
fun Category.container(): Color {
    val dark = MaterialTheme.colorScheme.background.lum() < 0.5f
    return color().copy(alpha = if (dark) 0.20f else 0.13f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
}

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
