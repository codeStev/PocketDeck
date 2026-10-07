package dev.pocketdeck.adapter.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pocketdeck.application.Outcome
import dev.pocketdeck.application.profile.CreateProfile
import dev.pocketdeck.application.profile.DeleteProfile
import dev.pocketdeck.application.profile.ObserveProfiles
import dev.pocketdeck.application.profile.ProfileNameProblem
import dev.pocketdeck.application.profile.RenameProfile
import dev.pocketdeck.application.profile.SwitchProfile
import dev.pocketdeck.domain.profile.Profile
import dev.pocketdeck.domain.profile.ProfileId
import dev.pocketdeck.domain.profile.Profiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The dialog the player has open for managing accounts; at most one at a time. */
sealed interface ProfileDialog {
    data class Add(
        val problem: ProfileNameProblem? = null,
    ) : ProfileDialog

    data class Rename(
        val profile: Profile,
        val problem: ProfileNameProblem? = null,
    ) : ProfileDialog

    data class ConfirmDelete(
        val profile: Profile,
    ) : ProfileDialog
}

data class ProfilesUiState(
    /** Null until the first profiles arrive from storage. */
    val profiles: Profiles? = null,
    val dialog: ProfileDialog? = null,
)

/** Drives the account switcher: which accounts exist, which is active, and adding, renaming and deleting them. */
class ProfilesViewModel(
    observeProfiles: ObserveProfiles,
    private val createProfile: CreateProfile,
    private val renameProfile: RenameProfile,
    private val switchProfile: SwitchProfile,
    private val deleteProfile: DeleteProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = mutableState

    init {
        viewModelScope.launch {
            observeProfiles().collect { profiles -> mutableState.update { it.copy(profiles = profiles) } }
        }
    }

    fun onSwitch(id: ProfileId) {
        viewModelScope.launch { switchProfile(id) }
    }

    fun onAddRequested() = openDialog(ProfileDialog.Add())

    fun onRenameRequested(profile: Profile) = openDialog(ProfileDialog.Rename(profile))

    fun onDeleteRequested(profile: Profile) = openDialog(ProfileDialog.ConfirmDelete(profile))

    fun onDialogDismissed() = openDialog(null)

    fun onNameSubmitted(name: String) {
        val dialog = mutableState.value.dialog
        viewModelScope.launch {
            val problem =
                when (dialog) {
                    is ProfileDialog.Add -> (createProfile(name) as? Outcome.Failure)?.reason
                    is ProfileDialog.Rename -> (renameProfile(dialog.profile.id, name) as? Outcome.Failure)?.reason
                    else -> return@launch
                }
            mutableState.update { it.copy(dialog = if (problem == null) null else dialog.withProblem(problem)) }
        }
    }

    fun onDeleteConfirmed() {
        val dialog = mutableState.value.dialog as? ProfileDialog.ConfirmDelete ?: return
        viewModelScope.launch {
            deleteProfile(dialog.profile.id)
            openDialog(null)
        }
    }

    private fun openDialog(dialog: ProfileDialog?) = mutableState.update { it.copy(dialog = dialog) }

    private fun ProfileDialog?.withProblem(problem: ProfileNameProblem): ProfileDialog? =
        when (this) {
            is ProfileDialog.Add -> copy(problem = problem)
            is ProfileDialog.Rename -> copy(problem = problem)
            else -> this
        }

    class Factory(
        private val observeProfiles: ObserveProfiles,
        private val createProfile: CreateProfile,
        private val renameProfile: RenameProfile,
        private val switchProfile: SwitchProfile,
        private val deleteProfile: DeleteProfile,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfilesViewModel::class.java)) { "Unknown ViewModel $modelClass" }
            return ProfilesViewModel(observeProfiles, createProfile, renameProfile, switchProfile, deleteProfile) as T
        }
    }
}
