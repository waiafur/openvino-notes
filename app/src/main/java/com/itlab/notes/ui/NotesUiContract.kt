package com.itlab.notes.ui

import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi

/**
 * UI contract for the Notes feature.
 * Keeps state & events in one place so screens stay "dumb" (render-only).
 */
sealed interface NotesUiScreen {
    data object Directories : NotesUiScreen

    data class DirectoryNotes(
        val directory: DirectoryItemUi,
    ) : NotesUiScreen

    data class NoteEditor(
        val directory: DirectoryItemUi,
        val note: NoteItemUi,
    ) : NotesUiScreen
}

data class NotesUiState(
    val screen: NotesUiScreen = NotesUiScreen.Directories,
    val directories: List<DirectoryItemUi> = emptyList(),
    val notes: List<NoteItemUi> = emptyList(),
)

sealed interface NotesUiEvent {
    data class OpenDirectory(
        val directory: DirectoryItemUi,
    ) : NotesUiEvent

    data object BackToDirectories : NotesUiEvent

    data class OpenNote(
        val note: NoteItemUi,
    ) : NotesUiEvent

    data object CreateNote : NotesUiEvent

    data object BackToDirectoryNotes : NotesUiEvent

    data class SaveNote(
        val note: NoteItemUi,
    ) : NotesUiEvent
}

interface NotesViewModelContract {
    val uiState: NotesUiState

    fun onEvent(event: NotesUiEvent)
}
