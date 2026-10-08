package dev.pocketdeck.adapter.ui.theme

import androidx.compose.runtime.Composable
import dev.codestev.pocketdesign.brand.SignalBrand
import dev.codestev.pocketdesign.theme.PocketDesignTheme

/** The app's look: the design system's Signal brand, light or dark with the phone. No dynamic colour, so the brand stays. */
@Composable
fun PocketDeckTheme(content: @Composable () -> Unit) {
    PocketDesignTheme(brand = SignalBrand.brand, content = content)
}
