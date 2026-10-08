package dev.codestev.pocketdesign.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.codestev.pocketdesign.brand.SignalBrand

private val LocalPdColors =
    staticCompositionLocalOf<PdColors> { error("Wrap your content in PocketDesignTheme") }
private val LocalPdTypography = staticCompositionLocalOf { PdTypography() }
private val LocalPdShapes = staticCompositionLocalOf { PdShapes() }

/** Where components read the active tokens: `PocketDesign.colors.accent`, `PocketDesign.typography.titleMd`, and so on. */
object PocketDesign {
    val colors: PdColors
        @Composable @ReadOnlyComposable
        get() = LocalPdColors.current

    val typography: PdTypography
        @Composable @ReadOnlyComposable
        get() = LocalPdTypography.current

    val shapes: PdShapes
        @Composable @ReadOnlyComposable
        get() = LocalPdShapes.current

    val spacing: PdSpacing get() = PdSpacing
}

/**
 * Applies a [brand] in light or dark and also configures [MaterialTheme] from it, so Material pieces used underneath
 * (text input handles, dialogs, accessibility) match. Dynamic colour is deliberately not used: it would replace the brand.
 *
 * ```
 * PocketDesignTheme(brand = SignalBrand.brand) { PdButton("Save", onClick = {}) }
 * ```
 */
@Composable
fun PocketDesignTheme(
    brand: PdBrand = SignalBrand.brand,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = brand.colors(darkTheme)
    val typography = remember { PdTypography() }
    val shapes = remember { PdShapes() }
    CompositionLocalProvider(
        LocalPdColors provides colors,
        LocalPdTypography provides typography,
        LocalPdShapes provides shapes,
    ) {
        MaterialTheme(
            colorScheme = materialColors(colors),
            typography = materialTypography(typography),
            shapes =
                Shapes(
                    extraSmall = shapes.xs,
                    small = shapes.sm,
                    medium = shapes.md,
                    large = shapes.lg,
                    extraLarge = shapes.xl,
                ),
            content = content,
        )
    }
}

private fun materialColors(c: PdColors): ColorScheme {
    val base =
        if (c.isLight) {
            lightColorScheme(
                primary = c.accent,
                onPrimary = c.onAccent,
                primaryContainer = c.accentSoft,
                onPrimaryContainer = c.onAccentSoft,
            )
        } else {
            darkColorScheme(
                primary = c.accent,
                onPrimary = c.onAccent,
                primaryContainer = c.accentSoft,
                onPrimaryContainer = c.onAccentSoft,
            )
        }
    return base.copy(
        background = c.surfacePage,
        onBackground = c.ink,
        surface = c.surfaceCard,
        onSurface = c.ink,
        surfaceVariant = c.surfaceSunken,
        onSurfaceVariant = c.inkMuted,
        outline = c.lineStrong,
        outlineVariant = c.line,
        error = c.danger,
        errorContainer = c.dangerSoft,
        onErrorContainer = c.danger,
    )
}

private fun materialTypography(t: PdTypography) =
    Typography(
        displaySmall = t.displayXl,
        headlineSmall = t.titleLg,
        titleLarge = t.titleMd,
        titleMedium = t.titleSm,
        bodyLarge = t.bodyLg,
        bodyMedium = t.body,
        bodySmall = t.bodySm,
        labelLarge = t.titleSm,
        labelMedium = t.label,
        labelSmall = t.label,
    )
