package dev.codestev.pocketdesign.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.brand.SignalBrand
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.theme.PdColors
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

@Composable
internal fun CatalogSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        PdSectionHeader(title)
        content()
    }
}

@Composable
internal fun TokenSections() {
    CatalogSection("Colour") { ColourSwatches() }
    CatalogSection("Type") { TypeSamples() }
    CatalogSection("Spacing and shapes") { SpacingAndShapes() }
}

private fun namedColours(c: PdColors): List<Pair<String, Color>> =
    listOf(
        "surfacePage" to c.surfacePage,
        "surfaceCard" to c.surfaceCard,
        "surfaceRaised" to c.surfaceRaised,
        "surfaceSunken" to c.surfaceSunken,
        "line" to c.line,
        "lineStrong" to c.lineStrong,
        "ink" to c.ink,
        "inkMuted" to c.inkMuted,
        "accent" to c.accent,
        "onAccent" to c.onAccent,
        "accentSoft" to c.accentSoft,
        "onAccentSoft" to c.onAccentSoft,
        "focus" to c.focus,
        "success" to c.success,
        "successSoft" to c.successSoft,
        "warning" to c.warning,
        "warningSoft" to c.warningSoft,
        "danger" to c.danger,
        "dangerSoft" to c.dangerSoft,
        "info" to c.info,
        "infoSoft" to c.infoSoft,
    )

@Composable
private fun ColourSwatches() {
    val colors = PocketDesign.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        namedColours(colors).forEach { (name, colour) -> Swatch(name, colour) }
    }
    Text(
        "Tints (brand layer): strong on top, soft below",
        style = PocketDesign.typography.bodySm,
        color = colors.inkMuted,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        listOf(
            SignalBrand.GREEN,
            SignalBrand.ORANGE,
            SignalBrand.BLUE,
            SignalBrand.YELLOW,
            SignalBrand.PURPLE,
            SignalBrand.BROWN,
            SignalBrand.SLATE,
            SignalBrand.STEEL,
            SignalBrand.TEAL,
            SignalBrand.GRAY,
        ).forEach { key ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Swatch("", colors.tint(key).strong, size = 36.dp)
                Swatch(key, colors.tint(key).soft, size = 36.dp)
            }
        }
    }
}

@Composable
private fun Swatch(
    name: String,
    colour: Color,
    size: Dp = 56.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space1),
    ) {
        Box(
            Modifier
                .size(
                    size,
                ).background(colour, PocketDesign.shapes.sm)
                .border(1.dp, PocketDesign.colors.line, PocketDesign.shapes.sm),
        )
        if (name.isNotEmpty()) Text(name, style = PocketDesign.typography.bodySm, color = PocketDesign.colors.inkMuted)
    }
}

@Composable
private fun TypeSamples() {
    val t = PocketDesign.typography
    val ink = PocketDesign.colors.ink
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        Text("displayXl 92%", style = t.displayXl, color = ink)
        Text("titleLg Decks and cards", style = t.titleLg, color = ink)
        Text("titleMd Charizard ex deck", style = t.titleMd, color = ink)
        Text("titleSm Import code", style = t.titleSm, color = ink)
        Text("bodyLg You can build this deck with what you own.", style = t.bodyLg, color = ink)
        Text("body Tap Save and it is kept here, for this account.", style = t.body, color = ink)
        Text("bodySm Opening odds are simulated.", style = t.bodySm, color = PocketDesign.colors.inkMuted)
        Text("LABEL TURN 1 2 3 4", style = t.label, color = PocketDesign.colors.inkMuted)
        Text("numeric 0123456789", style = t.numeric, color = ink)
    }
}

@Composable
private fun SpacingAndShapes() {
    val colors = PocketDesign.colors
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        listOf(
            "space1" to PdSpacing.space1,
            "space2" to PdSpacing.space2,
            "space3" to PdSpacing.space3,
            "space4" to PdSpacing.space4,
            "space5" to PdSpacing.space5,
            "space6" to PdSpacing.space6,
            "space7" to PdSpacing.space7,
        ).forEach { (name, size) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            ) {
                Box(Modifier.width(size).height(16.dp).background(colors.accent))
                Text("$name  $size", style = PocketDesign.typography.bodySm, color = colors.inkMuted)
            }
        }
        val shapes = PocketDesign.shapes
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
            listOf(
                "xs" to shapes.xs,
                "sm" to shapes.sm,
                "md" to shapes.md,
                "lg" to shapes.lg,
                "xl" to shapes.xl,
                "pill" to shapes.pill,
            ).forEach { (name, shape) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).background(colors.accentSoft, shape).border(2.dp, colors.accent, shape))
                    Text(name, style = PocketDesign.typography.bodySm, color = colors.inkMuted)
                }
            }
        }
    }
}
