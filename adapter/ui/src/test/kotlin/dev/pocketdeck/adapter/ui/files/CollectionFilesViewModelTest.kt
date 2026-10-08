package dev.pocketdeck.adapter.ui.files

import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.exchange.ExportedFile
import dev.pocketdeck.application.exchange.FileFormat
import dev.pocketdeck.application.exchange.ImportFailure
import dev.pocketdeck.application.exchange.ImportPreview
import dev.pocketdeck.domain.collection.CardCollection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionFilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun installMainDispatcher() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun removeMainDispatcher() = Dispatchers.resetMain()

    private val preview =
        ImportPreview("Main", CardCollection.EMPTY, cards = 5, copies = 9, unknownCards = 1, currentCards = 3)
    private var replaced: ImportPreview? = null

    private fun viewModel(read: Outcome<ImportPreview, ImportFailure> = Outcome.Success(preview)) =
        CollectionFilesViewModel(
            { format -> ExportedFile("pocketdeck-main.${format.extension}", format, "text") },
            { read },
            { replaced = it },
        )

    @Test
    fun `exporting makes a file ready for the picker`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onExport(FileFormat.CSV)
            advanceUntilIdle()

            viewModel.state.value.exportReady shouldBe ExportedFile("pocketdeck-main.csv", FileFormat.CSV, "text")
        }

    @Test
    fun `a saved file is reported by name, a failed one as a failure, a cancelled one not at all`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onExport(FileFormat.JSON)
            advanceUntilIdle()

            viewModel.onExportHandled(true)
            viewModel.state.value.message shouldBe FilesMessage.Exported("pocketdeck-main.json")
            viewModel.state.value.exportReady shouldBe null

            viewModel.onExport(FileFormat.JSON)
            advanceUntilIdle()
            viewModel.onExportHandled(false)
            viewModel.state.value.message shouldBe FilesMessage.ExportFailed

            viewModel.onExport(FileFormat.JSON)
            advanceUntilIdle()
            viewModel.onExportHandled(null)
            viewModel.state.value.message shouldBe null
        }

    @Test
    fun `a file that was read waits for confirmation and replaces nothing yet`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.onFileRead("text")
            advanceUntilIdle()

            viewModel.state.value.preview shouldBe preview
            replaced shouldBe null
        }

    @Test
    fun `confirming replaces the collection and says how many cards`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onFileRead("text")
            advanceUntilIdle()

            viewModel.onImportConfirmed()
            advanceUntilIdle()

            replaced shouldBe preview
            viewModel.state.value.preview shouldBe null
            viewModel.state.value.message shouldBe FilesMessage.Imported(5)
        }

    @Test
    fun `cancelling keeps the collection`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onFileRead("text")
            advanceUntilIdle()

            viewModel.onImportCancelled()
            advanceUntilIdle()

            viewModel.state.value.preview shouldBe null
            replaced shouldBe null
        }

    @Test
    fun `a refused or unreadable file is explained`() =
        runTest(dispatcher) {
            val refusing = viewModel(Outcome.Failure(ImportFailure.NothingRecognised))
            refusing.onFileRead("text")
            advanceUntilIdle()
            refusing.state.value.message shouldBe FilesMessage.Refused(ImportFailure.NothingRecognised)

            val unreadable = viewModel()
            unreadable.onFileRead(null)
            unreadable.state.value.message shouldBe FilesMessage.ReadFailed
        }
}
