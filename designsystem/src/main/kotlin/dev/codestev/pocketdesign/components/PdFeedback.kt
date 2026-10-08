package dev.codestev.pocketdesign.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign

enum class PdBannerKind { Info, Success, Warning, Danger }

private val MarkSize = 22.dp
private val MarkIconSize = 16.dp

/**
 * An inline notice: a coloured mark and one sentence on a soft background. Warning and danger share a mark on purpose;
 * the words carry the difference, so colour is never the only cue. Say what happened and what to do; show one banner at
 * a time. [actionLabel] adds a text button under the sentence, at the end ("Try again").
 */
@Composable
fun PdBanner(
    message: String,
    modifier: Modifier = Modifier,
    kind: PdBannerKind = PdBannerKind.Info,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = PocketDesign.colors
    val (container, content, icon) =
        when (kind) {
            PdBannerKind.Info -> Triple(colors.infoSoft, colors.info, PdIcons.Info)
            PdBannerKind.Success -> Triple(colors.successSoft, colors.success, PdIcons.Check)
            PdBannerKind.Warning -> Triple(colors.warningSoft, colors.warning, PdIcons.Alert)
            PdBannerKind.Danger -> Triple(colors.dangerSoft, colors.danger, PdIcons.Alert)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(container, PocketDesign.shapes.sm)
                .padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space1),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(MarkSize).background(content, PocketDesign.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(MarkIconSize), tint = colors.surfaceCard)
            }
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = PocketDesign.typography.bodySm.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = content,
            )
        }
        // Under the sentence, not beside it: a long message would be squeezed into a narrow column next to the button.
        if (actionLabel != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                PdButton(actionLabel, onAction, style = PdButtonStyle.Text, size = PdButtonSize.Small)
            }
        }
    }
}

/** A labelled action of [PdEmptyState]. */
@Immutable
class PdAction(
    val label: String,
    val onClick: () -> Unit,
)

private val IllustrationWidth = 168.dp
private val IllustrationHeight = 120.dp

/**
 * What a list shows when it has nothing to show: a small flat illustration, a title, one sentence and one primary
 * action. Say what will appear and how to get the first item; do not show it while data is still loading. Pass your own
 * [illustration] (a vector drawable) or keep the default of three cards.
 */
@Composable
fun PdEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { PdCardsIllustration() },
    primaryAction: PdAction? = null,
    secondaryAction: PdAction? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = PdSpacing.space4, vertical = PdSpacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
    ) {
        illustration()
        Text(
            title,
            style = PocketDesign.typography.titleMd,
            color = PocketDesign.colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PdSpacing.space1))
        if (primaryAction != null) PdButton(primaryAction.label, primaryAction.onClick)
        if (secondaryAction !=
            null
        ) {
            PdButton(secondaryAction.label, secondaryAction.onClick, style = PdButtonStyle.Text)
        }
    }
}

/** A default empty-state illustration: three cards, the last with a check, drawn from the active theme's colours. */
@Composable
fun PdCardsIllustration(modifier: Modifier = Modifier) {
    val colors = PocketDesign.colors
    val blue = colors.tint("blue")
    Canvas(modifier.size(IllustrationWidth, IllustrationHeight)) {
        val unit = size.width / VIEWBOX_WIDTH

        fun card(
            x: Float,
            y: Float,
            fill: Color,
            stroke: Color?,
        ) {
            val topLeft = Offset(x * unit, y * unit)
            val cardSize = Size(CARD_WIDTH * unit, CARD_HEIGHT * unit)
            val radius = CornerRadius(CARD_RADIUS * unit)
            drawRoundRect(fill, topLeft, cardSize, radius)
            if (stroke != null) drawRoundRect(stroke, topLeft, cardSize, radius, style = Stroke(STROKE * unit))
        }
        card(22f, 30f, colors.surfaceSunken, null)
        card(52f, 14f, blue.soft, blue.strong)
        card(84f, 30f, colors.accentSoft, colors.accent)
        drawCircle(colors.accent, radius = 14f * unit, center = Offset(115f * unit, 72f * unit))
        val check =
            Path().apply {
                moveTo(109f * unit, 72f * unit)
                lineTo(113.5f * unit, 76.5f * unit)
                lineTo(122f * unit, 67f * unit)
            }
        drawPath(check, colors.onAccent, style = Stroke(3.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private const val VIEWBOX_WIDTH = 168f
private const val CARD_WIDTH = 62f
private const val CARD_HEIGHT = 84f
private const val CARD_RADIUS = 10f
private const val STROKE = 3f

/**
 * A rounded bar on the sunken surface filled to [value] (0 to 1) with [tint]. The number is always written next to it
 * ([label] and [valueText]): the bar is a second encoding, never the only one. Not for indeterminate loading.
 */
@Composable
fun PdProgressMeter(
    value: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueText: String? = null,
    tint: Color = PocketDesign.colors.accent,
    trackHeight: Dp = 8.dp,
) {
    val fraction = value.coerceIn(0f, 1f)
    Column(
        modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
    ) {
        if (label != null || valueText != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = PdSpacing.space1),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    label.orEmpty(),
                    style = PocketDesign.typography.titleSm.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = PocketDesign.colors.ink,
                )
                Text(
                    valueText.orEmpty(),
                    style =
                        PocketDesign.typography.titleSm.copy(
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontFeatureSettings = "tnum",
                        ),
                    color = PocketDesign.colors.ink,
                )
            }
        }
        MeterTrack(fraction, tint, trackHeight)
    }
}

@Composable
private fun MeterTrack(
    fraction: Float,
    tint: Color,
    height: Dp,
) {
    val shape = PocketDesign.shapes.pill
    Box(Modifier.fillMaxWidth().height(height).background(PocketDesign.colors.surfaceSunken, shape)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(height).background(tint, shape))
    }
}

/** One cell of a [PdMeterTable]: its [text], the [fraction] its bar is filled to and whether it is [emphasized]. */
@Immutable
class PdMeterCell(
    val text: String,
    val fraction: Float,
    val emphasized: Boolean = false,
)

/** One row of a [PdMeterTable]: a [name] and one cell per column header. */
@Immutable
class PdMeterRow(
    val name: String,
    val cells: List<PdMeterCell>,
)

private const val NAME_WEIGHT = 2f
private val CellGap = 8.dp
private val CellBar = 4.dp

/**
 * A table of numbers with a thin bar under each, for comparing chances at a glance (the opening odds are one). Cells
 * with [PdMeterCell.emphasized] are bold. Keep the numbers tabular and the headers short.
 */
@Composable
fun PdMeterTable(
    headers: List<String>,
    rows: List<PdMeterRow>,
    modifier: Modifier = Modifier,
    tint: Color = PocketDesign.colors.accent,
) {
    val colors = PocketDesign.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
            Spacer(Modifier.weight(NAME_WEIGHT))
            headers.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = PocketDesign.typography.label,
                    color = colors.inkMuted,
                    textAlign = TextAlign.End,
                )
            }
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CellGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    Modifier.weight(NAME_WEIGHT),
                    style = PocketDesign.typography.titleSm.copy(fontSize = 15.sp),
                    color = colors.ink,
                    maxLines = 1,
                )
                row.cells.forEach { cell ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            cell.text,
                            style =
                                PocketDesign.typography.body.copy(
                                    fontSize = 14.sp,
                                    fontFeatureSettings = "tnum",
                                    fontWeight = if (cell.emphasized) FontWeight.ExtraBold else FontWeight.Bold,
                                ),
                            color = colors.ink,
                        )
                        MeterTrack(cell.fraction.coerceIn(0f, 1f), tint, CellBar)
                    }
                }
            }
        }
    }
}
