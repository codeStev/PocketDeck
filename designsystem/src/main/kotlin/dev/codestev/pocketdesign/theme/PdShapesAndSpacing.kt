package dev.codestev.pocketdesign.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** The corner scale: art thumbnails and badges [xs], fields and banners [sm], buttons [md], cards [lg], sheets [xl]. */
@Immutable
class PdShapes(
    val xs: RoundedCornerShape = RoundedCornerShape(6.dp),
    val sm: RoundedCornerShape = RoundedCornerShape(10.dp),
    val md: RoundedCornerShape = RoundedCornerShape(14.dp),
    val lg: RoundedCornerShape = RoundedCornerShape(20.dp),
    val xl: RoundedCornerShape = RoundedCornerShape(28.dp),
    /** Fully rounded: buttons, chips, the floating navigation, the header tag. */
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** The spacing scale. A screen gutter and card padding are [space4]; sections on art-led screens are [space7] apart. */
object PdSpacing {
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 24.dp
    val space6 = 32.dp
    val space7 = 48.dp
    val space8 = 64.dp

    /** The smallest touch target of any control. */
    val minTouchTarget = 48.dp
}

/** Durations and press feedback shared by every interactive component. */
object PdMotion {
    const val PRESS_MILLIS = 120
    const val STATE_MILLIS = 200

    /** Something appears (a dialog, a bar filling): a little slower than a state change so the eye can follow it. */
    const val ENTER_MILLIS = 240

    /** One screen replaces another. */
    const val SCREEN_MILLIS = 160
    const val PRESSED_SCALE = 0.97f

    /** How small a dialog is when it starts to grow in. */
    const val ENTER_SCALE = 0.94f
}
