package com.itlab.domain.app

import java.io.File
import java.io.InputStream

interface FileSystemProvider {
    fun openAsset(fileName: String): InputStream

    fun listAssets(path: String): Array<String>?

    fun getFilesDir(): File

    fun getTotalRamMB(): Long
}
