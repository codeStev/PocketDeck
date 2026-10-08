package dev.pocketdeck.adapter.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codestev.pocketdesign.components.PdDialog
import dev.codestev.pocketdesign.components.PdTextField
import dev.codestev.pocketdesign.components.PdTopBar
import dev.codestev.pocketdesign.icons.PdIcons
import dev.codestev.pocketdesign.theme.PocketDesign
import dev.pocketdeck.adapter.ui.R
import dev.pocketdeck.application.profile.ProfileNameProblem
import dev.pocketdeck.domain.profile.Profiles

/**
 * Names the account the app works with and lets the player switch to another one. It sits above every tab so a scan
 * can never go to the wrong account unnoticed.
 */
@Composable
fun ProfileSwitcher(viewModel: ProfilesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profiles = state.profiles ?: return
    ProfileBar(profiles, viewModel)
    state.dialog?.let { ProfileDialogs(it, viewModel) }
}

@Composable
private fun ProfileBar(
    profiles: Profiles,
    viewModel: ProfilesViewModel,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.statusBarsPadding()) {
        PdTopBar(
            tag = profiles.active.name.value,
            onTagClick = { open = true },
            tagTrailingIcon = PdIcons.ChevronDown,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profiles.all.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name.value + if (profile == profiles.active) " ✓" else "") },
                    onClick = {
                        open = false
                        viewModel.onSwitch(profile.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_add)) },
                onClick = {
                    open = false
                    viewModel.onAddRequested()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.profile_rename)) },
                onClick = {
                    open = false
                    viewModel.onRenameRequested(profiles.active)
                },
            )
            if (profiles.all.size > 1) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_delete)) },
                    onClick = {
                        open = false
                        viewModel.onDeleteRequested(profiles.active)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileDialogs(
    dialog: ProfileDialog,
    viewModel: ProfilesViewModel,
) {
    when (dialog) {
        is ProfileDialog.Add -> {
            NameDialog(R.string.profile_add, "", dialog.problem, viewModel)
        }

        is ProfileDialog.Rename -> {
            NameDialog(R.string.profile_rename, dialog.profile.name.value, dialog.problem, viewModel)
        }

        is ProfileDialog.ConfirmDelete -> {
            PdDialog(
                title = stringResource(R.string.profile_delete_title, dialog.profile.name.value),
                onDismissRequest = viewModel::onDialogDismissed,
                confirmText = stringResource(R.string.profile_delete),
                onConfirm = viewModel::onDeleteConfirmed,
                dismissText = stringResource(R.string.profile_cancel),
                destructive = true,
            ) {
                Text(
                    stringResource(R.string.profile_delete_hint),
                    style = PocketDesign.typography.body,
                    color = PocketDesign.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: Int,
    initial: String,
    problem: ProfileNameProblem?,
    viewModel: ProfilesViewModel,
) {
    var name by remember { mutableStateOf(initial) }
    PdDialog(
        title = stringResource(title),
        onDismissRequest = viewModel::onDialogDismissed,
        confirmText = stringResource(R.string.profile_save),
        onConfirm = { viewModel.onNameSubmitted(name) },
        dismissText = stringResource(R.string.profile_cancel),
    ) {
        PdTextField(
            name,
            { name = it },
            Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.profile_name),
            isError = problem != null,
            supportingText = problem?.let { stringResource(it.message()) },
        )
    }
}

private fun ProfileNameProblem.message() =
    when (this) {
        ProfileNameProblem.BLANK -> R.string.profile_name_blank
        ProfileNameProblem.TAKEN -> R.string.profile_name_taken
    }
