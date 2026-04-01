package com.itlab.domain.usecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlinx.datetime.Clock

class CreateNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(note: Note): String {
        val note = note.copy(createdAt = Clock.System.now())
        return repo.createNote(note)
    }
}
