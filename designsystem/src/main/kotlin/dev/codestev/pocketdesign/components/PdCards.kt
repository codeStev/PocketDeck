package dev.codestev.pocketdesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PdTint
import dev.codestev.pocketdesign.theme.PocketDesign

private val CardElevation = 3.dp
private val ArtWellHeight = 148.dp
private val UnownedAlpha = 0.5f
private const val CARD_ASPECT_RATIO = 0.7194f
private val SilhouetteStroke = 3.dp

/** The soft shadow of a card on the page; in dark theme the surface step carries the depth, so there is none. */
internal fun Modifier.pdCardShadow(shape: Shape): Modifier =
    composed { if (PocketDesign.colors.isLight) shadow(CardElevation, shape) else this }

/** A rounded surface for grouped content. Pass [containerColor] for a quiet tint; keep [contentPadding] at the default. */
@Composable
fun PdCard(
    modifier: Modifier = Modifier,
    shape: Shape = PocketDesign.shapes.lg,
    containerColor: Color = PocketDesign.colors.surfaceCard,
    contentPadding: Dp = PdSpacing.space4,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.pdCardShadow(shape).background(containerColor, shape).padding(contentPadding), content = content)
}

/**
 * A hero card: a [tint]ed art well on top (put the art in [art], aligned inside the box, and any overlay such as an
 * origin chip) and the facts below in [content]. Order [content] as: name, status chip, a meter, one primary button,
 * then quiet actions. The tint is a graphic ground, never text colour; use the soft value only.
 */
@Composable
fun PdTintedCard(
    tint: PdTint,
    modifier: Modifier = Modifier,
    art: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = PocketDesign.shapes.xl
    Column(modifier.pdCardShadow(shape).clip(shape).background(PocketDesign.colors.surfaceCard)) {
        Box(Modifier.fillMaxWidth().height(ArtWellHeight).background(tint.soft), content = art)
        Column(
            modifier = Modifier.fillMaxWidth().padding(PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space3),
            content = content,
        )
    }
}

/**
 * The unit of a grid of art: the art ([art]) as the hero on a ground tinted by [tint], with a round badge for the number
 * of [copies] (hidden at 0). Not-[owned] tiles stay in the grid at half opacity. The tile keeps a card's real aspect
 * ratio, so the grid column sets the width and every tile is the same size; put the title below the tile, not inside.
 * Give [contentDescription] the name and count, and [onClick] to open a detail view.
 */
@Composable
fun PdArtTile(
    tint: PdTint,
    contentDescription: String,
    modifier: Modifier = Modifier,
    copies: Int = 0,
    owned: Boolean = true,
    onClick: (() -> Unit)? = null,
    art: @Composable BoxScope.() -> Unit,
) {
    val shape = PocketDesign.shapes.lg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT_RATIO)
                .alpha(if (owned) 1f else UnownedAlpha)
                .then(if (onClick != null) Modifier.pdClickable(shape, onClick) else Modifier.clip(shape))
                .background(tint.soft, shape)
                .semantics { this.contentDescription = contentDescription },
        content = {
            art()
            if (copies > 0) CopyBadge(copies, Modifier.align(Alignment.TopEnd).padding(PdSpacing.space2))
        },
    )
}

@Composable
private fun CopyBadge(
    copies: Int,
    modifier: Modifier,
) {
    val colors = PocketDesign.colors
    Text(
        text = "×$copies",
        modifier =
            modifier
                .background(
                    colors.accent,
                    PocketDesign.shapes.pill,
                ).padding(horizontal = PdSpacing.space2, vertical = 2.dp),
        style =
            PocketDesign.typography.titleSm.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFeatureSettings = "tnum",
            ),
        color = colors.onAccent,
    )
}

/** An abstract single card face in [tint], a stand-in for card art in previews and empty slots. */
@Composable
fun BoxScope.PdCardFacePlaceholder(tint: PdTint) {
    Box(
        Modifier
            .align(Alignment.Center)
            .fillMaxWidth(FACE_WIDTH_SHARE)
            .fillMaxHeight(FACE_HEIGHT_SHARE)
            .background(PocketDesign.colors.surfaceCard, PocketDesign.shapes.sm)
            .border(SilhouetteStroke, tint.strong, PocketDesign.shapes.sm),
    )
}

private const val FACE_WIDTH_SHARE = 0.68f
private const val FACE_HEIGHT_SHARE = 0.74f

/** Three abstract cards fanned out at the bottom of an art well, a stand-in for the key cards of a deck. */
@Composable
fun BoxScope.PdCardFanPlaceholder(tint: PdTint) {
    PdCardFan(tint, List(FAN_MAX) { {} })
}

/**
 * Up to three cards fanned out at the bottom of an art well, the first card in the middle and the larger, the next two
 * to its sides, tilted and cropped by the well's edge. Each entry of [cards] draws one card's picture, filling the
 * card (put a card image there; an empty slot stays a plain card face with a [tint] outline). Pass one to three.
 */
@Composable
fun BoxScope.PdCardFan(
    tint: PdTint,
    cards: List<@Composable BoxScope.() -> Unit>,
) {
    val layout = fanLayout(cards.take(FAN_MAX))
    Row(
        Modifier.align(Alignment.BottomCenter).offset(y = FanDrop),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2),
        verticalAlignment = Alignment.Bottom,
    ) {
        layout.forEach { Silhouette(tint, it) }
    }
}

/** Slot order is importance; on screen the most important card stands in the middle. */
private fun fanLayout(shown: List<@Composable BoxScope.() -> Unit>): List<FanSlot> {
    val middle = FanSlot(shown[0], MIDDLE_HEIGHT, 0f, FanLift)

    fun side(
        index: Int,
        tilt: Float,
    ) = FanSlot(shown[index], SIDE_HEIGHT, tilt, 0.dp)
    return when (shown.size) {
        1 -> listOf(middle)
        2 -> listOf(side(1, -FAN_TILT), FanSlot(shown[0], MIDDLE_HEIGHT, FAN_TILT, FanLift))
        else -> listOf(side(1, -FAN_TILT), middle, side(2, FAN_TILT))
    }
}

private class FanSlot(
    val art: @Composable BoxScope.() -> Unit,
    val height: Dp,
    val tilt: Float,
    val lift: Dp,
)

private val FanDrop = 14.dp
private val FanLift = 6.dp
private val CardWidth = 74.dp
private val SIDE_HEIGHT = 100.dp
private val MIDDLE_HEIGHT = 112.dp
private const val FAN_MAX = 3
private const val FAN_TILT = 7f

@Composable
private fun Silhouette(
    tint: PdTint,
    slot: FanSlot,
) {
    val shape = PocketDesign.shapes.sm
    Box(
        Modifier
            .offset(y = -slot.lift)
            .size(width = CardWidth, height = slot.height)
            .graphicsLayer { rotationZ = slot.tilt }
            .background(PocketDesign.colors.surfaceCard, shape)
            .border(SilhouetteStroke, tint.strong, shape)
            .padding(SilhouetteStroke),
    ) {
        Box(Modifier.fillMaxSize().clip(shape), content = slot.art)
    }
}
