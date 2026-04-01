package com.itlab.domain.usecase

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import kotlinx.datetime.Clock

class CreateFolderUseCase(
    private val repo: NoteFolderRepository,
) {
    suspend operator fun invoke(folder: NoteFolder): String {
        // discuss
        val folder = folder.copy(createdAt = Clock.System.now())
        return repo.createFolder(folder)
    }
}
