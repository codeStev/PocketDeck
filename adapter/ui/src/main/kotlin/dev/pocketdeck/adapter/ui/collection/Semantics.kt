package dev.pocketdeck.adapter.ui.collection

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal fun Modifier.semanticsLabel(label: String): Modifier = semantics { contentDescription = label }
