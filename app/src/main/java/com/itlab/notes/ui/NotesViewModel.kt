package com.itlab.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.NoteItemUi
import java.util.UUID

class NotesViewModel : NotesViewModelContract {
    override var uiState: NotesUiState by mutableStateOf(
        NotesUiState(
            screen = NotesUiScreen.Directories,
            directories = previewDirectoriesFallback(),
        ),
    )
        private set

    override fun onEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.OpenDirectory -> openDirectory(event.directory)
            NotesUiEvent.BackToDirectories -> backToDirectories()
            is NotesUiEvent.OpenNote -> openNote(event.note)
            NotesUiEvent.CreateNote -> createNote()
            NotesUiEvent.BackToDirectoryNotes -> backToDirectoryNotes()
            is NotesUiEvent.SaveNote -> saveNote(event.note)
        }
    }

    private fun openDirectory(directory: DirectoryItemUi) {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                notes = notesFallbackForDirectory(directory),
            )
    }

    private fun backToDirectories() {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.Directories,
                notes = emptyList(),
            )
    }

    private fun openNote(note: NoteItemUi) {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory
        if (dir != null) {
            uiState =
                uiState.copy(
                    screen =
                        NotesUiScreen.NoteEditor(
                            directory = dir,
                            note = note,
                        ),
                )
        }
    }

    private fun createNote() {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory
        if (dir != null) {
            val newNote =
                NoteItemUi(
                    id = UUID.randomUUID().toString(),
                    title = "",
                    content = "",
                )
            uiState =
                uiState.copy(
                    screen =
                        NotesUiScreen.NoteEditor(
                            directory = dir,
                            note = newNote,
                        ),
                )
        }
    }

    private fun backToDirectoryNotes() {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor != null) {
            uiState =
                uiState.copy(
                    screen = NotesUiScreen.DirectoryNotes(directory = editor.directory),
                )
        }
    }

    private fun saveNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor != null) {
            val updatedNotes = uiState.notes.toMutableList()
            val index = updatedNotes.indexOfFirst { it.id == note.id }
            if (index >= 0) {
                updatedNotes[index] = note
            } else {
                updatedNotes.add(note)
            }

            val updatedDirectory = editor.directory.copy(noteCount = updatedNotes.size)
            uiState =
                uiState.copy(
                    screen = NotesUiScreen.DirectoryNotes(directory = updatedDirectory),
                    notes = updatedNotes,
                )
        }
    }

    private fun notesFallbackForDirectory(directory: DirectoryItemUi): List<NoteItemUi> =
        when (directory.name) {
            "My Study" ->
                listOf(
                    NoteItemUi(
                        id = "my-study-1",
                        title = "Lecture notes",
                        content = "Topic: coroutines\n- suspend\n- scope\n- dispatcher",
                    ),
                    NoteItemUi(
                        id = "my-study-2",
                        title = "Homework",
                        content = "Due Friday.\nChecklist:\n1) ...\n2) ...",
                    ),
                )

            "How to Cook" ->
                listOf(
                    NoteItemUi(
                        id = "cook-1",
                        title = "Cherry pie",
                        content = "Ingredients:\n- Flour 300g\n- Cherries 200g\n- Sugar 120g",
                    ),
                    NoteItemUi(
                        id = "cook-2",
                        title = "Pasta",
                        content = "Sauce: tomatoes + garlic + basil.\nTime: 20 minutes.",
                    ),
                )

            else ->
                listOf(
                    NoteItemUi(
                        id = "other-1",
                        title = "First note",
                        content = "Temporary placeholder while notes load from the data layer.",
                    ),
                    NoteItemUi(
                        id = "other-2",
                        title = "Second note",
                        content = "Connect the data layer and pass the list into UI.",
                    ),
                )
        }

    private fun previewDirectoriesFallback(): List<DirectoryItemUi> =
        listOf(
            DirectoryItemUi(name = "All Notes", noteCount = 0),
            DirectoryItemUi(name = "My Study", noteCount = 0),
            DirectoryItemUi(name = "How to Cook", noteCount = 0),
            DirectoryItemUi(name = "My poems", noteCount = 0),
        )
}
