package dev.pocketdeck.adapter.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.codestev.pocketdesign.components.PdButton
import dev.codestev.pocketdesign.components.PdButtonStyle
import dev.codestev.pocketdesign.components.PdCardsIllustration
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R

private const val LAST_PAGE = 2
private val DotSize = 8.dp

/**
 * The first-run tour: what the app does, how to scan, and what leaves the phone. The first two pages can be skipped to the
 * last, which needs an explicit "I understand" ([firstRun]); reopened from About it ends with a plain "Done".
 */
@Composable
fun WelcomeFlow(
    firstRun: Boolean,
    onFinished: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .background(PocketDesign.colors.surfacePage)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = PdSpacing.space4),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space4),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = PdSpacing.space6), contentAlignment = Alignment.Center) {
                PdCardsIllustration()
            }
            PageContent(page)
        }
        Dots(page)
        Buttons(page, firstRun, onNext = { page++ }, onSkip = { page = LAST_PAGE }, onFinished = onFinished)
    }
}

@Composable
private fun PageContent(page: Int) {
    when (page) {
        0 -> {
            Page(R.string.welcome_title_1, R.string.welcome_body_1)
            Text(
                stringResource(R.string.welcome_unofficial),
                style = PocketDesign.typography.bodySm,
                color = PocketDesign.colors.inkMuted,
            )
        }

        1 -> {
            Page(R.string.welcome_title_2, R.string.welcome_body_2)
        }

        else -> {
            Page(R.string.welcome_title_3, null)
            Point(R.string.welcome_point_local)
            Point(R.string.welcome_point_download)
            Point(R.string.welcome_point_ai)
        }
    }
}

@Composable
private fun Page(
    title: Int,
    body: Int?,
) {
    Text(stringResource(title), style = PocketDesign.typography.titleLg, color = PocketDesign.colors.ink)
    if (body != null) {
        Text(stringResource(body), style = PocketDesign.typography.bodyLg, color = PocketDesign.colors.ink)
    }
}

@Composable
private fun Point(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        Box(
            Modifier
                .padding(top = PdSpacing.space2)
                .size(DotSize)
                .background(PocketDesign.colors.accent, PocketDesign.shapes.pill),
        )
        Text(
            stringResource(text),
            style = PocketDesign.typography.body,
            color = PocketDesign.colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Dots(page: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = PdSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(PdSpacing.space2, Alignment.CenterHorizontally),
    ) {
        repeat(LAST_PAGE + 1) { index ->
            val color = if (index == page) PocketDesign.colors.accent else PocketDesign.colors.lineStrong
            Box(Modifier.size(DotSize).background(color, PocketDesign.shapes.pill))
        }
    }
}

@Composable
private fun Buttons(
    page: Int,
    firstRun: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinished: () -> Unit,
) {
    Column(Modifier.padding(bottom = PdSpacing.space4), verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
        if (page < LAST_PAGE) {
            PdButton(stringResource(R.string.welcome_next), onNext, Modifier.fillMaxWidth())
            PdButton(stringResource(R.string.welcome_skip), onSkip, Modifier.fillMaxWidth(), style = PdButtonStyle.Text)
        } else {
            val label = if (firstRun) R.string.welcome_agree else R.string.welcome_done
            PdButton(stringResource(label), onFinished, Modifier.fillMaxWidth())
        }
    }
}
