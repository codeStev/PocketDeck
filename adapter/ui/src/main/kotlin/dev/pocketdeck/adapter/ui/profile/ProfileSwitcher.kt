package dev.pocketdeck.adapter.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.profile_account), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { open = true }) { Text(profiles.active.name.value + " ▾") }
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
            AlertDialog(
                onDismissRequest = viewModel::onDialogDismissed,
                title = { Text(stringResource(R.string.profile_delete_title, dialog.profile.name.value)) },
                text = { Text(stringResource(R.string.profile_delete_hint)) },
                confirmButton = {
                    TextButton(onClick = viewModel::onDeleteConfirmed) { Text(stringResource(R.string.profile_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.profile_cancel)) }
                },
            )
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
    AlertDialog(
        onDismissRequest = viewModel::onDialogDismissed,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.profile_name)) },
                isError = problem != null,
                supportingText = problem?.let { { Text(stringResource(it.message())) } },
            )
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onNameSubmitted(name) }) { Text(stringResource(R.string.profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onDialogDismissed) { Text(stringResource(R.string.profile_cancel)) }
        },
    )
}

private fun ProfileNameProblem.message() =
    when (this) {
        ProfileNameProblem.BLANK -> R.string.profile_name_blank
        ProfileNameProblem.TAKEN -> R.string.profile_name_taken
    }
