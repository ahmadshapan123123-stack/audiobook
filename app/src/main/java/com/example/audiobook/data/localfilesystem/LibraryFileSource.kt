package com.example.audiobook.data.localfilesystem

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.documentfile.provider.DocumentFile
import android.util.Log
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
    /**
     * يتنقّل في شجرة SAF كاملة عبر ContentProvider ويبني القائمة في الذاكرة —
     * عملية I/O حاجبة يجب أن تُستدعى على خيط خلفي (Dispatchers.IO) حصرًا.
     *
     * التنفيذات تلتزم بسقفي [ScanLimits.MAX_DEPTH] و[ScanLimits.MAX_FILES]
     *(LOG فقط) فلا تتكدّس الحلقات ولا تنفد الذاكرة على شجرة ضخمة أو عميقة.
     */
    @WorkerThread
    fun listAudioFiles(rootUri: Uri): List<ScanFile>
}

/** سقوف أمان لت walkers شجرة SAF — تمنع StackOverflow ونمو القائمة بلا حد. */
object ScanLimits {
    /** أقصى عمق تكراري: author/series/vol/…  ثم يتوقّف الجوس. */
    const val MAX_DEPTH = 8

    /** أقصى عدد ملفات يُجمَع في تمريرة واحدة. */
    const val MAX_FILES = 100_000
}

class DocumentTreeFileSource @Inject constructor(private val context: Context) : LibraryFileSource {
    @WorkerThread
    override fun listAudioFiles(rootUri: Uri): List<ScanFile> {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val result = mutableListOf<ScanFile>()
        var depthLimitReached = false
        var fileCapReached = false

        fun visit(folder: DocumentFile, relativeFolder: String, depth: Int) {
            if (depth > ScanLimits.MAX_DEPTH) {
                depthLimitReached = true
                Log.w(TAG, "Depth limit ${ScanLimits.MAX_DEPTH} reached at \"$relativeFolder\"; deeper folders skipped")
                return
            }
            if (result.size >= ScanLimits.MAX_FILES) {
                fileCapReached = true
                return
            }
            for (child in folder.listFiles().sortedBy { it.name.orEmpty() }) {
                // إعادة الفحص في كل تكرار: الحارس قد يُفعَّل أثناء هذا الجوس.
                if (result.size >= ScanLimits.MAX_FILES) {
                    fileCapReached = true
                    break
                }
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    visit(child, joinPath(relativeFolder, name), depth + 1)
                } else if (name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS) {
                    result += ScanFile(
                        uri = child.uri,
                        relativePath = joinPath(relativeFolder, name),
                        folderPath = relativeFolder,
                        fileName = name,
                        size = child.length(),
                        lastModified = child.lastModified()
                    )
                }
            }
        }
        visit(root, "", depth = 1)

        if (fileCapReached) {
            Log.w(TAG, "File cap ${ScanLimits.MAX_FILES} reached; scan truncated. Raise ScanLimits.MAX_FILES to cover this library.")
        }
        if (depthLimitReached) {
            Log.w(TAG, "Some folders beyond depth ${ScanLimits.MAX_DEPTH} were not scanned.")
        }
        return result
    }

    private fun joinPath(relativeFolder: String, name: String): String =
        if (relativeFolder.isBlank()) name else "$relativeFolder/$name"

    companion object {
        private const val TAG = "DocumentTreeFileSource"
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "opus", "flac")
    }
}