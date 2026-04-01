package com.itlab.domain.aiusecase

import com.itlab.domain.repository.NotesRepository

class ApplyTagsUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        newTags: Set<String>,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found")

        val updated =
            note.copy(
                tags = newTags,
                updatedAt =
                    kotlinx.datetime.Clock.System
                        .now(),
            )

        repo.updateNote(updated)
    }
}
