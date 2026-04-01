package com.itlab.domain

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.usecase.CreateFolderUseCase
import com.itlab.domain.usecase.DeleteFolderUseCase
import com.itlab.domain.usecase.GetFolderUseCase
import com.itlab.domain.usecase.ObserveFoldersUseCase
import com.itlab.domain.usecase.UpdateFolderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderUseCasesTest {
    private class FakeFolderRepo : NoteFolderRepository {
        private val store = mutableMapOf<String, NoteFolder>()
        private val flow = MutableStateFlow<List<NoteFolder>>(emptyList())

        override fun observeFolders() = flow

        override suspend fun createFolder(folder: NoteFolder): String {
            store[folder.id] = folder
            flow.value = store.values.toList()
            return folder.id
        }

        override suspend fun renameFolder(
            id: String,
            name: String,
        ) {
            val folder = store[id] ?: return
            val updated = folder.copy(name = name)
            store[id] = updated
            flow.value = store.values.toList()
        }

        override suspend fun deleteFolder(id: String) {
            store.remove(id)
            flow.value = store.values.toList()
        }

        override suspend fun getFolderById(id: String): NoteFolder? = store[id]

        override suspend fun updateFolder(folder: NoteFolder) {
            store[folder.id] = folder
            flow.value = store.values.toList()
        }
    }

    @Test
    fun createFolder_and_getFolder() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(id = "1", name = "Test")

            create(folder)

            val result = get("1")

            assertEquals("Test", result?.name)
        }

    @Test
    fun updateFolder_works() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val update = UpdateFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(id = "1", name = "Old")
            create(folder)

            val updated = folder.copy(name = "New")
            update(updated)

            val result = get("1")

            assertEquals("New", result?.name)
        }

    @Test
    fun deleteFolder_works() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val delete = DeleteFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(id = "1", name = "Test")
            create(folder)

            delete("1")

            val result = get("1")

            assertNull(result)
        }

    @Test
    fun observeFolders_emitsData() =
        runBlocking {
            val repo = FakeFolderRepo()
            val create = CreateFolderUseCase(repo)
            val observe = ObserveFoldersUseCase(repo)

            create(NoteFolder(id = "1", name = "A"))

            val list = observe().first()

            assertEquals(1, list.size)
        }
}
