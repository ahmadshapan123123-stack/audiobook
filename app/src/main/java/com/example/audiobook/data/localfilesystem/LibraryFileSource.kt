package com.example.audiobook.data.localfilesystem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import javax.inject.Inject

data class ScanFile(
    val uri: Uri,
    val relativePath: String,
    val folderPath: String,
    val fileName: String,
    val size: Long,
    val lastModified: Long
)

interface LibraryFileSource {
    fun listAudioFiles(rootUri: Uri): List<ScanFile>
}

class DocumentTreeFileSource @Inject constructor(private val context: Context) : LibraryFileSource {
    override fun listAudioFiles(rootUri: Uri): List<ScanFile> {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val result = mutableListOf<ScanFile>()
        fun visit(folder: DocumentFile, relativeFolder: String) {
            folder.listFiles().sortedBy { it.name.orEmpty() }.forEach { child ->
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    visit(child, listOf(relativeFolder, name).filter(String::isNotBlank).joinToString("/"))
                } else if (name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS) {
                    result += ScanFile(
                        uri = child.uri,
                        relativePath = listOf(relativeFolder, name).filter(String::isNotBlank).joinToString("/"),
                        folderPath = relativeFolder,
                        fileName = name,
                        size = child.length(),
                        lastModified = child.lastModified()
                    )
                }
            }
        }
        visit(root, "")
        return result
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "opus", "flac")
    }
}