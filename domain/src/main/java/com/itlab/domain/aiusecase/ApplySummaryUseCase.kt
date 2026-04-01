package com.itlab.domain.aiusecase

import com.itlab.domain.repository.NotesRepository

class ApplySummaryUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        newSummary: String,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found")

        val updated =
            note.copy(
                summary = newSummary,
                updatedAt =
                    kotlinx.datetime.Clock.System
                        .now(),
            )

        repo.updateNote(updated)
    }
}
