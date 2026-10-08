package dev.pocketdeck.adapter.ui.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.codestev.pocketdesign.components.PdSectionHeader
import dev.codestev.pocketdesign.components.PdSegmentedTabs
import dev.codestev.pocketdesign.theme.PdSpacing
import dev.pocketdeck.adapter.ui.R

/**
 * Lets the player choose the app's language. Android stores the choice per app (from Android 13 on, where it also shows
 * in the system's own per-app language settings); on older versions the app follows the phone's language.
 */
@Composable
internal fun LanguageSection() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AppLanguagePicker()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AppLanguagePicker() {
    val manager = LocalContext.current.getSystemService(LocaleManager::class.java)
    var selected by remember {
        mutableStateOf(
            AppLanguageChoice.of(
                manager.applicationLocales
                    .takeIf { !it.isEmpty }
                    ?.get(0)
                    ?.language,
            ),
        )
    }
    val choices = AppLanguageChoice.entries
    Column(verticalArrangement = Arrangement.spacedBy(PdSpacing.space3)) {
        PdSectionHeader(stringResource(R.string.settings_language_title))
        PdSegmentedTabs(
            labels = choices.map { stringResource(it.label()) },
            selectedIndex = choices.indexOf(selected),
            onSelect = { index ->
                val choice = choices[index]
                selected = choice
                manager.applicationLocales =
                    choice.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AppLanguageChoice.label() =
    when (this) {
        AppLanguageChoice.SYSTEM -> R.string.settings_language_system
        AppLanguageChoice.ENGLISH -> R.string.settings_language_english
        AppLanguageChoice.GERMAN -> R.string.settings_language_german
    }
