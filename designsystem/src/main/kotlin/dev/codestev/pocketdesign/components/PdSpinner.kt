package dev.codestev.pocketdesign.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.theme.PocketDesign

private const val SECONDS_PER_TURN = 0.9f
private const val FULL_TURN = 360f
private const val ARC_SWEEP = 100f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val TRACK_ALPHA = 0.2f

/**
 * An indeterminate spinner for work of unknown length: a bright arc running round a faint ring. It is driven by the
 * frame clock, not by an animation, so it keeps turning when the phone has "remove animations" or battery saving on
 * (Material's own spinner freezes then and looks like a hang). That is deliberate: a spinner is essential motion, it
 * is the only sign that the app is working. Always put a word next to it where there is room.
 */
@Composable
fun PdSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = PocketDesign.colors.accent,
    strokeWidth: Dp = 3.dp,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last !=
                    0L
                ) {
                    angle = (angle + (now - last) / NANOS_PER_SECOND * FULL_TURN / SECONDS_PER_TURN) % FULL_TURN
                }
                last = now
            }
        }
    }
    Canvas(modifier.size(size).semantics { }) {
        val stroke = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset = strokeWidth.toPx() / 2
        val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
        drawArc(color.copy(alpha = TRACK_ALPHA), 0f, FULL_TURN, false, Offset(inset, inset), arcSize, style = stroke)
        drawArc(color, angle, ARC_SWEEP, false, Offset(inset, inset), arcSize, style = stroke)
    }
}
