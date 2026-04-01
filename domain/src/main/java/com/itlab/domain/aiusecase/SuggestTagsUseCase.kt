package com.itlab.domain.aiusecase

import com.itlab.domain.ai.NoteAiService
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.ImageSource
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class SuggestTagsUseCase(
    private val ai: NoteAiService,
    private val repo: NotesRepository,
) {
    private fun extractText(note: Note): String =
        note.contentItems
            .filterIsInstance<ContentItem.Text>()
            .joinToString("\n") { it.text }

    private fun extractImages(note: Note): List<String> =
        note.contentItems
            .filterIsInstance<ContentItem.Image>()
            .map { image ->
                when (val source = image.source) {
                    is ImageSource.Local -> source.path
                    is ImageSource.Remote -> source.url
                }
            }

    suspend operator fun invoke(noteId: String): Set<String> {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val text = extractText(note)
        val imageUrls = extractImages(note)

        return ai.tagTXT(text) + ai.tagIMGs(imageUrls)
    }
}
