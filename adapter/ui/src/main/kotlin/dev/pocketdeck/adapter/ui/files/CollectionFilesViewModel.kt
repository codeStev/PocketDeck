package dev.pocketdeck.adapter.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.exchange.ExportCollection
import dev.pocketdeck.application.exchange.ExportedFile
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.ImportFailure
import dev.pocketdeck.application.exchange.ImportPreview
import dev.pocketdeck.application.exchange.ReadCollectionFile
import dev.pocketdeck.application.exchange.ReplaceCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the player is told after saving or loading a collection file. */
sealed interface FilesMessage {
    data class Exported(
        val fileName: String,
    ) : FilesMessage

    data object ExportFailed : FilesMessage

    data class Imported(
        val cards: Int,
    ) : FilesMessage

    /** The file could not be opened or read at all. */
    data object ReadFailed : FilesMessage

    data class Refused(
        val failure: ImportFailure,
    ) : FilesMessage
}

data class CollectionFilesUiState(
    /** A file waiting for the player to choose where to save it; the screen opens the file picker for it. */
    val exportReady: ExportedFile? = null,
    /** A file that was read and waits for the player to confirm replacing the collection. */
    val preview: ImportPreview? = null,
    val message: FilesMessage? = null,
)

/** Saves the active account's collection to a file and replaces it from one, through the application's ports only. */
class CollectionFilesViewModel(
    private val exportCollection: ExportCollection,
    private val readFile: ReadCollectionFile,
    private val replaceCollection: ReplaceCollection,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectionFilesUiState())
    val state: StateFlow<CollectionFilesUiState> = mutableState

    fun onExport(format: FileFormat) {
        viewModelScope.launch {
            val file = exportCollection(format)
            mutableState.update { it.copy(exportReady = file, message = null) }
        }
    }

    /** The picker answered: [saved] is true once the file is written, false if writing failed, null if the player backed out. */
    fun onExportHandled(saved: Boolean?) {
        mutableState.update { state ->
            val name = state.exportReady?.fileName
            val message =
                when {
                    saved == true && name != null -> FilesMessage.Exported(name)
                    saved == false -> FilesMessage.ExportFailed
                    else -> null
                }
            state.copy(exportReady = null, message = message)
        }
    }

    /** The picker returned the file's text, or null when it could not be read. */
    fun onFileRead(text: String?) {
        if (text == null) {
            mutableState.update { it.copy(message = FilesMessage.ReadFailed) }
            return
        }
        viewModelScope.launch {
            when (val outcome = readFile(text)) {
                is Outcome.Success -> mutableState.update { it.copy(preview = outcome.value, message = null) }
                is Outcome.Failure -> mutableState.update { it.copy(message = FilesMessage.Refused(outcome.reason)) }
            }
        }
    }

    fun onImportConfirmed() {
        val preview = mutableState.value.preview ?: return
        viewModelScope.launch {
            replaceCollection(preview)
            mutableState.update { it.copy(preview = null, message = FilesMessage.Imported(preview.cards)) }
        }
    }

    fun onImportCancelled() = mutableState.update { it.copy(preview = null) }

    fun onMessageShown() = mutableState.update { it.copy(message = null) }

    class Factory(
        private val exportCollection: ExportCollection,
        private val readFile: ReadCollectionFile,
        private val replaceCollection: ReplaceCollection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(
                modelClass.isAssignableFrom(CollectionFilesViewModel::class.java),
            ) { "Unknown ViewModel $modelClass" }
            return CollectionFilesViewModel(exportCollection, readFile, replaceCollection) as T
        }
    }
}
