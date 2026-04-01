package com.itlab.domain.usecase

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import kotlinx.datetime.Clock

class UpdateFolderUseCase(
    private val repo: NoteFolderRepository,
) {
    suspend operator fun invoke(folder: NoteFolder) {
        val folder = folder.copy(updatedAt = Clock.System.now())
        repo.updateFolder(folder)
    }
}
