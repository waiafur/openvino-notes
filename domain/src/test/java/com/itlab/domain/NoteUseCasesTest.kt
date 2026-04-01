package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.CreateNoteUseCase
import com.itlab.domain.usecase.DeleteNoteUseCase
import com.itlab.domain.usecase.GetNoteUseCase
import com.itlab.domain.usecase.MoveNoteToFolderUseCase
import com.itlab.domain.usecase.ObserveNotesUseCase
import com.itlab.domain.usecase.UpdateNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteUseCasesTest {
    private class FakeNotesRepo : NotesRepository {
        private val store = mutableMapOf<String, Note>()
        private val flow = MutableStateFlow<List<Note>>(emptyList())

        override fun observeNotes() = flow

        override fun observeNotesByFolder(folderId: String) = flow

        override suspend fun getNoteById(id: String): Note? = store[id]

        override suspend fun createNote(note: Note): String {
            store[note.id] = note
            flow.value = store.values.toList()
            return note.id
        }

        override suspend fun updateNote(note: Note) {
            store[note.id] = note
            flow.value = store.values.toList()
        }

        override suspend fun deleteNote(id: String) {
            store.remove(id)
            flow.value = store.values.toList()
        }
    }

    private class FakeFolderRepo : NoteFolderRepository {
        private val store = mutableMapOf<String, NoteFolder>()

        override fun observeFolders() = MutableStateFlow(emptyList<NoteFolder>())

        override suspend fun createFolder(folder: NoteFolder): String {
            store[folder.id] = folder
            return folder.id
        }

        override suspend fun renameFolder(
            id: String,
            name: String,
        ) = Unit

        override suspend fun deleteFolder(id: String) = Unit

        override suspend fun getFolderById(id: String): NoteFolder? = store[id]

        override suspend fun updateFolder(folder: NoteFolder) = Unit
    }

    @Test
    fun create_update_delete_note() =
        runBlocking {
            val repo = FakeNotesRepo()

            val create = CreateNoteUseCase(repo)
            val update = UpdateNoteUseCase(repo)
            val delete = DeleteNoteUseCase(repo)
            val get = GetNoteUseCase(repo)

            val note = Note(id = "n1", title = "A")

            create(note)

            val updated = note.copy(title = "B")
            update(updated)

            val result = get("n1")
            assertEquals("B", result?.title)

            delete("n1")

            val result2 = get("n1")
            assertNull(result2)
        }

    @Test
    fun moveNoteToFolder_works() =
        runBlocking {
            val notesRepo = FakeNotesRepo()
            val folderRepo = FakeFolderRepo()

            val move = MoveNoteToFolderUseCase(notesRepo)
            val createNote = CreateNoteUseCase(notesRepo)

            val folder = NoteFolder(id = "f1", name = "Folder")
            folderRepo.createFolder(folder)

            val note = Note(id = "n1", title = "Note")
            createNote(note)

            move("f1", "n1")

            val updated = notesRepo.getNoteById("n1")

            assertEquals("f1", updated?.folderId)
        }

    @Test
    fun observeNotes_returnsData() =
        runBlocking {
            val repo = FakeNotesRepo()
            val observe = ObserveNotesUseCase(repo)
            val create = CreateNoteUseCase(repo)

            create(Note(id = "n1", title = "Test"))

            val list = observe().first()

            assertEquals(1, list.size)
        }
}
