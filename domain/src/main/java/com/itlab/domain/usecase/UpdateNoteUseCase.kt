package com.itlab.domain.usecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlinx.datetime.Clock

class UpdateNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(note: Note) {
        val note = note.copy(updatedAt = Clock.System.now())
        repo.updateNote(note)
    }
}
