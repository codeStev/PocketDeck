package dev.pocketdeck.adapter.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.adapter.ui.scan.ConsentAccess
import dev.pocketdeck.application.consent.Consent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WelcomeUiState(
    /** Whether the player has agreed in the first-run tour; null until storage has answered, so no tour flashes up. */
    val agreed: Boolean? = null,
)

/** Remembers that the first-run tour has been read and agreed to, once. */
class WelcomeViewModel(
    private val consent: ConsentAccess,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WelcomeUiState())
    val state: StateFlow<WelcomeUiState> = mutableState

    init {
        viewModelScope.launch {
            consent.observe(Consent.WELCOME).collect { agreed -> mutableState.update { it.copy(agreed = agreed) } }
        }
    }

    fun onAgree() {
        viewModelScope.launch { consent.grant(Consent.WELCOME) }
    }

    class Factory(
        private val consent: ConsentAccess,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WelcomeViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return WelcomeViewModel(consent) as T
        }
    }
}
