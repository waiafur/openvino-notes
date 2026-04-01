package com.itlab.domain.repository

import com.itlab.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>

    fun observeNotesByFolder(folderId: String): Flow<List<Note>>

    suspend fun getNoteById(id: String): Note?

    suspend fun createNote(note: Note): String

    suspend fun updateNote(note: Note)

    suspend fun deleteNote(id: String)
}
