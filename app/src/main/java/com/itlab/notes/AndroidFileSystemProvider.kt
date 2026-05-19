package com.itlab.notes

import android.app.ActivityManager
import android.content.Context
import com.itlab.domain.app.FileSystemProvider
import java.io.File
import java.io.InputStream

class AndroidFileSystemProvider(
    private val context: Context,
) : FileSystemProvider {
    override fun openAsset(fileName: String): InputStream = context.assets.open(fileName)

    override fun listAssets(path: String): Array<String>? = context.assets.list(path)

    override fun getFilesDir(): File = context.filesDir

    override fun getTotalRamMB(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }
}
