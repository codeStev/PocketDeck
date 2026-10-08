package dev.codestev.pocketdesign.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icon set: rounded outline glyphs on a 24 grid with a 2.2 stroke, drawn in black and tinted by the component that
 * shows them (`Icon(tint = ...)`). A selected navigation tab fills its capsule instead of changing the glyph.
 */
object PdIcons {
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val ChevronRight by lazy { icon("ChevronRight", "M9 6l6 6-6 6") }
    val ChevronDown by lazy { icon("ChevronDown", "M7 10l5 5 5-5") }
    val Back by lazy { icon("Back", "M15 6l-6 6 6 6") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("Minus", "M5 12h14") }
    val Search by lazy { icon("Search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0-12 0M16 16l4 4") }
    val Bell by lazy { icon("Bell", "M6 17h12l-1.5-2V11a4.5 4.5 0 0 0-9 0v4L6 17zM10 20a2 2 0 0 0 4 0") }
    val Copy by lazy {
        icon("Copy", "M11 9h6a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM5 15V6a2 2 0 0 1 2-2h8")
    }
    val Refresh by lazy { icon("Refresh", "M20 12a8 8 0 1 1-2.6-5.9M20 4v5h-5") }
    val Info by lazy { icon("Info", "M12 11v6M12 7.5v.01") }

    /** Rings around a dot: aim at something specific. */
    val Target by lazy {
        icon("Target", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0M7.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0-9 0M12 12v.01")
    }
    val Alert by lazy { icon("Alert", "M12 7v6M12 17v.01") }

    /** Two cards, one behind the other. */
    val Collection by lazy {
        icon(
            "Collection",
            "M6.5 6h6a2.5 2.5 0 0 1 2.5 2.5v9a2.5 2.5 0 0 1-2.5 2.5h-6A2.5 2.5 0 0 1 4 17.5v-9A2.5 2.5 0 0 1 6.5 6zM9 4h8a3 3 0 0 1 3 3v9",
        )
    }

    /** Three stacked layers. */
    val Decks by lazy {
        icon(
            "Decks",
            "M12 4l8.5 4.5L12 13 3.5 8.5 12 4zM3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5",
        )
    }

    /** Corner brackets around a dot. */
    val Scan by lazy {
        icon(
            "Scan",
            "M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2M15 12a3 3 0 1 0-6 0a3 3 0 1 0 6 0",
        )
    }

    /** Two sliders. */
    val Settings by lazy {
        icon(
            "Settings",
            "M4 7h9M17 7h3M4 17h3M11 17h9M17 7a2 2 0 1 0-4 0a2 2 0 1 0 4 0M11 17a2 2 0 1 0-4 0a2 2 0 1 0 4 0",
        )
    }

    /** Every icon of the set, for the catalog and for tests. */
    val all: List<ImageVector> by lazy {
        listOf(
            Check,
            ChevronRight,
            ChevronDown,
            Back,
            Close,
            Plus,
            Minus,
            Search,
            Bell,
            Copy,
            Refresh,
            Info,
            Alert,
            Target,
            Collection,
            Decks,
            Scan,
            Settings,
        )
    }

    private fun icon(
        name: String,
        pathData: String,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}
