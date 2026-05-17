package com.orotrain.oro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.ui.components.ProgrammeCard

@Composable
fun ProgrammeListScreen(
    state: OroUiState,
    onCreateProgramme: (String) -> Unit,
    onEditProgramme: (String) -> Unit,
    onLoadProgramme: (String) -> Unit,
    onDuplicateProgramme: (String) -> Unit,
    onDeleteProgramme: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProgrammeName by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.programmes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No programmes yet",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Create a programme to define the zones your crew will train through.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(text = "  Create Programme")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text(text = "  New Programme")
                    }
                }

                items(state.programmes, key = { it.id }) { programme ->
                    ProgrammeCard(
                        programme = programme,
                        isActive = state.activeProgramme?.id == programme.id,
                        onEdit = { onEditProgramme(programme.id) },
                        onLoad = { onLoadProgramme(programme.id) },
                        onDuplicate = { onDuplicateProgramme(programme.id) },
                        onDelete = { onDeleteProgramme(programme.id) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newProgrammeName = ""
            },
            title = { Text("New Programme") },
            text = {
                OutlinedTextField(
                    value = newProgrammeName,
                    onValueChange = { newProgrammeName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProgrammeName.isNotBlank()) {
                            onCreateProgramme(newProgrammeName)
                            showCreateDialog = false
                            newProgrammeName = ""
                        }
                    },
                    enabled = newProgrammeName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newProgrammeName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
