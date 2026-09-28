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
     * (LOG فقط) فلا تتكدّس الحلقات ولا تنفد الذاكرة على شجرة ضخمة أو عميقة.
     *
     * التنفيذ الافتراضي يجمّع عبر [streamAudioFiles]؛ المصادر الاختبارية
     * الصغيرة تكتفي بتجاوز هذه الدالة وترث البثّ مجانًا.
     */
    @WorkerThread
    fun listAudioFiles(rootUri: Uri): List<ScanFile> {
        val out = mutableListOf<ScanFile>()
        streamAudioFiles(rootUri) { out += it }
        return out
    }

    /**
     * STAGE 1B — بثّ ملفات SAF ملفًا ملفًا بدل بناء القائمة كاملة.
     *
     * يُستدعى [onFile] على كل ملف صوتي فور العثور عليه، فيقرر المستهلك
     * (الفحص) التجميع أو المعالجة أو الإسقاط. [isCancelled] يُفحص في نقاط
     * آمنة أثناء الجوس فيتوقف الجوس مبكرًا عند طلب الإلغاء بدل إكمال
     * شجرة 50 ألف ملف عبثًا.
     *
     * تحذير: الدالتان لهما تنفيذ افتراضي يستدعي الأخرى — أي تنفيذ يجب أن
     * يتجاوز واحدة منهما على الأقل وإلا وقع في تكرار لا نهائي.
     */
    @WorkerThread
    fun streamAudioFiles(
        rootUri: Uri,
        isCancelled: () -> Boolean = { false },
        onFile: (ScanFile) -> Unit
    ) {
        listAudioFiles(rootUri).forEach { file ->
            if (isCancelled()) return
            onFile(file)
        }
    }
}

/** سقوف أمان لت walkers شجرة SAF — تمنع StackOverflow ونمو القائمة بلا حد. */
object ScanLimits {
    /** أقصى عمق تكراري: author/series/vol/…  ثم يتوقّف الجوس. */
    const val MAX_DEPTH = 8

    /** أقصى عدد ملفات يُجمَع في تمريرة واحدة. */
    const val MAX_FILES = 100_000
}

class DocumentTreeFileSource @Inject constructor(private val context: Context) : LibraryFileSource {
    /**
     * STAGE 1B — الجوس الحقيقي متدفق: لا قائمة `result` متراكمة هنا.
     * كل ملف يُسلَّم فورًا عبر [onFile]، فيبقى الحيّ أثناء الجوس
     * بحجم عمق الشجرة لا بحجم المكتبة.
     */
    @WorkerThread
    override fun streamAudioFiles(
        rootUri: Uri,
        isCancelled: () -> Boolean,
        onFile: (ScanFile) -> Unit
    ) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        var depthLimitReached = false
        var fileCapReached = false
        var emitted = 0

        fun visit(folder: DocumentFile, relativeFolder: String, depth: Int): Boolean {
            if (isCancelled()) return false
            if (depth > ScanLimits.MAX_DEPTH) {
                depthLimitReached = true
                Log.w(TAG, "Depth limit ${ScanLimits.MAX_DEPTH} reached at \"$relativeFolder\"; deeper folders skipped")
                return true
            }
            if (emitted >= ScanLimits.MAX_FILES) {
                fileCapReached = true
                return false
            }
            for (child in folder.listFiles().sortedBy { it.name.orEmpty() }) {
                // إعادة الفحص في كل تكرار: الحارس قد يُفعَّل أثناء هذا الجوس.
                if (isCancelled() || emitted >= ScanLimits.MAX_FILES) {
                    fileCapReached = emitted >= ScanLimits.MAX_FILES
                    return false
                }
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    if (!visit(child, joinPath(relativeFolder, name), depth + 1)) return false
                } else if (name.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS) {
                    onFile(
                        ScanFile(
                            uri = child.uri,
                            relativePath = joinPath(relativeFolder, name),
                            folderPath = relativeFolder,
                            fileName = name,
                            size = child.length(),
                            lastModified = child.lastModified()
                        )
                    )
                    emitted++
                }
            }
            return true
        }
        visit(root, "", depth = 1)

        if (fileCapReached) {
            Log.w(TAG, "File cap ${ScanLimits.MAX_FILES} reached; scan truncated. Raise ScanLimits.MAX_FILES to cover this library.")
        }
        if (depthLimitReached) {
            Log.w(TAG, "Some folders beyond depth ${ScanLimits.MAX_DEPTH} were not scanned.")
        }
    }

    private fun joinPath(relativeFolder: String, name: String): String =
        if (relativeFolder.isBlank()) name else "$relativeFolder/$name"

    companion object {
        private const val TAG = "DocumentTreeFileSource"
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "opus", "flac")
    }
}