package com.pocketmind.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = White,
    primaryContainer = BrandPrimarySoft,
    onPrimaryContainer = BrandPrimaryStrong,
    secondary = BrandSecondary,
    onSecondary = TextPrimaryLight,
    secondaryContainer = BrandSecondarySoft,
    onSecondaryContainer = TextPrimaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = ErrorLight,
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BackgroundDark,
    primaryContainer = BrandPrimarySoftDark,
    onPrimaryContainer = BrandPrimaryStrongDark,
    secondary = BrandSecondaryDark,
    onSecondary = BackgroundDark,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = ErrorDark,
    onError = BackgroundDark,
)

private val PocketMindShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Immutable
data class PocketFinancialColors(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val info: Color,
    val muted: Color,
)

private val LocalPocketFinancialColors = staticCompositionLocalOf {
    PocketFinancialColors(
        income = IncomeLight,
        expense = ExpenseLight,
        warning = WarningLight,
        info = InfoLight,
        muted = TextMutedLight,
    )
}

private val LocalPocketMindDarkTheme = staticCompositionLocalOf { false }

object PocketMindTheme {
    val financialColors: PocketFinancialColors
        @Composable get() = LocalPocketFinancialColors.current

    val isDarkTheme: Boolean
        @Composable get() = LocalPocketMindDarkTheme.current
}

@Composable
fun PocketMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val financialColors = if (darkTheme) {
        PocketFinancialColors(IncomeDark, ExpenseDark, WarningDark, InfoDark, TextMutedDark)
    } else {
        PocketFinancialColors(IncomeLight, ExpenseLight, WarningLight, InfoLight, TextMutedLight)
    }
    CompositionLocalProvider(
        LocalPocketFinancialColors provides financialColors,
        LocalPocketMindDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = PocketMindTypography,
            shapes = PocketMindShapes,
            content = content,
        )
    }
}
