package dev.codestev.pocketdesign.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.codestev.pocketdesign.components.PdSwitch
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.codestev.pocketdesign.theme.PocketDesignTheme

/**
 * The living documentation of the design system: tokens first, then every component with its variants and states.
 * The switch at the top flips the theme, so light and dark are checked on the same screen.
 */
@Composable
fun CatalogScreen() {
    var dark by rememberSaveable { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    var followed by rememberSaveable { mutableStateOf(false) }
    if (!followed) {
        dark = systemDark
        followed = true
    }
    PocketDesignTheme(darkTheme = dark) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PocketDesign.colors.surfacePage)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(PdSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(PdSpacing.space7),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space2)) {
                Text("Design catalog", style = PocketDesign.typography.titleLg, color = PocketDesign.colors.ink)
                Text(
                    "PocketDesign, brand Signal",
                    style = PocketDesign.typography.bodySm,
                    color = PocketDesign.colors.inkMuted,
                )
                PdSwitch(
                    checked = dark,
                    onCheckedChange = { dark = it },
                    label = "Dark theme",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TokenSections()
            ActionSections()
            NavigationSections()
            SurfaceSections()
            FeedbackSections()
            InputSections()
        }
    }
}
