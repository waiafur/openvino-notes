package com.itlab.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.itlab.notes.ui.editor.editorScreen
import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi
import com.itlab.notes.ui.notes.directoriesScreen
import com.itlab.notes.ui.notes.notesListScreen

@Composable
fun notesApp() {
    val viewModel = remember { NotesViewModel() }
    val state = viewModel.uiState

    when (val screen = state.screen) {
        NotesUiScreen.Directories -> {
            directoriesScreen(
                directories = state.directories,
                onDirectoryClick = { directory ->
                    viewModel.onEvent(NotesUiEvent.OpenDirectory(directory))
                },
            )
        }

        is NotesUiScreen.DirectoryNotes -> {
            val directory: DirectoryItemUi = screen.directory
            notesListScreen(
                directoryName = directory.name,
                notes = state.notes,
                onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectories) },
                onAddNoteClick = { viewModel.onEvent(NotesUiEvent.CreateNote) },
                onNoteClick = { note: NoteItemUi ->
                    viewModel.onEvent(NotesUiEvent.OpenNote(note))
                },
            )
        }

        is NotesUiScreen.NoteEditor -> {
            editorScreen(
                directoryName = screen.directory.name,
                note = screen.note,
                onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectoryNotes) },
                onSave = { updated -> viewModel.onEvent(NotesUiEvent.SaveNote(updated)) },
            )
        }
    }
}
