package com.itlab.domain.usecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow

class ObserveNotesUseCase(
    private val repo: NotesRepository,
) {
    operator fun invoke(): Flow<List<Note>> = repo.observeNotes()
}
