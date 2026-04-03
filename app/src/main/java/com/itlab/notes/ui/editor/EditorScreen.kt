package com.itlab.notes.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itlab.notes.ui.notes.NoteItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun editorScreen(
    directoryName: String,
    note: NoteItemUi,
    onBack: () -> Unit,
    onSave: (NoteItemUi) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val editorVm = remember(note.id) { EditorViewModel(initialNote = note) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            editorTopBar(
                directoryName = directoryName,
                title = editorVm.title,
                onBack = onBack,
            )
        },
        floatingActionButton = {
            editorFab(
                onClick = { onSave(editorVm.buildUpdatedNote()) },
            )
        },
    ) { paddingValues ->
        editorContent(
            title = editorVm.title,
            content = editorVm.content,
            onTitleChange = editorVm::onTitleChange,
            onContentChange = editorVm::onContentChange,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun editorTopBar(
    directoryName: String,
    title: String,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (title.isBlank()) directoryName else title,
                color = colors.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.onSurface,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified,
            ),
    )
}

@Composable
private fun editorFab(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onClick,
        containerColor = colors.primary,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = colors.onPrimary,
        )
    }
}

@Composable
private fun editorContent(
    title: String,
    content: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        editorTitleField(
            value = title,
            onValueChange = onTitleChange,
        )

        editorContentField(
            value = content,
            onValueChange = onContentChange,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun editorTitleField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Title") },
        singleLine = true,
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                focusedContainerColor = colors.background,
                unfocusedContainerColor = colors.background,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun editorContentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Input") },
        minLines = 12,
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedPlaceholderColor = colors.onSurfaceVariant,
                unfocusedPlaceholderColor = colors.onSurfaceVariant,
                focusedContainerColor = colors.background,
                unfocusedContainerColor = colors.background,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
    )
}
