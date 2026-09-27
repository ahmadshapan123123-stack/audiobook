package com.example.audiobook.data.localfilesystem

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.net.URLConnection

data class EmbeddedChapter(val title: String?, val startPositionMs: Long)

data class AudioMetadata(
    val durationMs: Long,
    val mimeType: String,
    val title: String?,
    val narratorName: String?,
    val genre: String?,
    val embeddedChapters: List<EmbeddedChapter>,
    val album: String? = null
)

interface AudioMetadataReader {
    fun read(uri: Uri, fileName: String): AudioMetadata
}

class MediaAudioMetadataReader(private val context: Context) : AudioMetadataReader {
    override fun read(uri: Uri, fileName: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: URLConnection.guessContentTypeFromName(fileName)
                ?: mimeFor(extension)
            AudioMetadata(
                durationMs = duration,
                mimeType = mimeType,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                narratorName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                embeddedChapters = if (extension == "m4b") {
                    M4bChapterParser.parse(context.contentResolver.openInputStream(uri))
                } else {
                    emptyList()
                },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            )
        } finally {
            retriever.release()
        }
    }

    private fun mimeFor(extension: String) = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a", "m4b" -> "audio/mp4"
        "aac" -> "audio/aac"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }
}

/**
 * محلّل فصول M4B بتدفّق (صندوق-بصندوق عبر DataInputStream) بدل قراءة الملف كاملًا
 * في الذاكرة. يقرأ رؤوس الصناديق المتدرّجة، يتخطّى المحتوى بقراءات مجزّأة، ولا
 * يفكّك إلا الصناديق الضرورية (moov/udta/meta/ilst → chpl).
 * حارس الحجم: أي تدفّق يتجاوز [MAX_PARSE_BYTES] لا يُفكّك ويُسجَّل تحذير — يمنع
 * انفجار الذاكرة على ملفات M4B الضخمة أو المكتبات الكبيرة.
 */
private object M4bChapterParser {
    private const val TAG = "M4bChapterParser"
    private const val MAX_PARSE_BYTES = 50_000_000L
    private val containers = setOf("moov", "udta", "meta", "ilst")

    fun parse(input: java.io.InputStream?): List<EmbeddedChapter> {
        if (input == null) return emptyList()
        return try {
            input.use { stream ->
                val buffered = java.io.BufferedInputStream(stream, 64 * 1024)
                if (buffered.available() > MAX_PARSE_BYTES) {
                    Log.w(TAG, "M4B larger than $MAX_PARSE_BYTES bytes; skipping chapter parsing")
                    emptyList()
                } else {
                    val bounded = BoundedBytes(buffered, MAX_PARSE_BYTES)
                    val dis = java.io.DataInputStream(bounded)
                    walk(dis, bounded, MAX_PARSE_BYTES)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse embedded M4B chapters", error)
            emptyList()
        }
    }

    /** يجوس صناديق ضمن [..end] ويدخل الحاويات المعروفة بحثًا عن chpl. */
    private fun walk(dis: java.io.DataInputStream, reader: BoundedBytes, end: Long): List<EmbeddedChapter> {
        while (reader.consumed + 8 <= end) {
            if (reader.consumed + 8 > reader.limit) {
                Log.w(TAG, "M4B exceeded $MAX_PARSE_BYTES bytes while scanning for chapters; giving up")
                return emptyList()
            }
            val size = readUInt32(dis)
            if (size <= 0L) return emptyList()
            val type = readFourCC(dis)
            var content = size - 8L
            if (size == 1L) {
                val large = readUInt64(dis)
                content = large - 16L
            }
            if (content < 0L || reader.consumed + content > end) return emptyList()
            val boxEnd = reader.consumed + content
            when {
                type == "chpl" -> return parseChpl(dis, reader, boxEnd)
                type in containers -> {
                    val nested = walk(dis, reader, boxEnd)
                    if (nested.isNotEmpty()) return nested
                    if (reader.consumed < boxEnd) skipFully(dis, reader, boxEnd - reader.consumed)
                }
                else -> skipFully(dis, reader, content)
            }
        }
        return emptyList()
    }

    /** فكّ فصول chpl: 4 بايت (نسخة+أعلام) ثم 1 بايت العدد ثم سجلات (8 بايت زمن + اسم). */
    private fun parseChpl(dis: java.io.DataInputStream, reader: BoundedBytes, boxEnd: Long): List<EmbeddedChapter> {
        if (reader.consumed + 5 > boxEnd) return emptyList()
        skipFully(dis, reader, 4L)
        val count = readUInt8(dis)
        val result = mutableListOf<EmbeddedChapter>()
        repeat(count) {
            if (reader.consumed + 9 > boxEnd) return@repeat
            val startTicks = readUInt64(dis)
            val titleLength = readUInt8(dis)
            if (reader.consumed + titleLength > boxEnd) return@repeat
            val bytes = ByteArray(titleLength)
            dis.readFully(bytes)
            val title = String(bytes, Charsets.UTF_8)
            result += EmbeddedChapter(title.ifBlank { null }, startTicks / 10_000L)
        }
        return result
    }

    /** يستنفد المطلوب بقراءات مجزأة (DataInputStream قد يقرأ جزئيًا). */
    private fun skipFully(dis: java.io.DataInputStream, reader: BoundedBytes, n: Long) {
        var remaining = n
        val chunk = ByteArray(64 * 1024)
        while (remaining > 0L) {
            val toRead = remaining.coerceAtMost(chunk.size.toLong()).toInt()
            val read = dis.read(chunk, 0, toRead)
            if (read < 0) break
            remaining -= read
        }
    }

    private fun readUInt8(dis: java.io.DataInputStream): Int = dis.readUnsignedByte()

    private fun readUInt32(dis: java.io.DataInputStream): Long {
        val b0 = dis.readUnsignedByte().toLong()
        val b1 = dis.readUnsignedByte().toLong()
        val b2 = dis.readUnsignedByte().toLong()
        val b3 = dis.readUnsignedByte().toLong()
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readUInt64(dis: java.io.DataInputStream): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or dis.readUnsignedByte().toLong() }
        return value
    }

    private fun readFourCC(dis: java.io.DataInputStream): String {
        val bytes = ByteArray(4)
        dis.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    /** تدفّق عدّاد يوقف القراءة عند بلوغ الحد مهما وُصف حجم الصناديق. */
    private class BoundedBytes(
        private val input: java.io.InputStream,
        val limit: Long
    ) : java.io.InputStream() {
        var consumed: Long = 0L; private set

        override fun read(): Int {
            if (consumed >= limit) return -1
            val b = input.read()
            if (b != -1) consumed++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (consumed >= limit) return -1
            val room = (limit - consumed).coerceAtMost(len.toLong()).toInt()
            if (room <= 0) return -1
            val n = input.read(b, off, room)
            if (n > 0) consumed += n
            return n
        }

        override fun skip(n: Long): Long {
            val room = (limit - consumed).coerceAtMost(n)
            if (room <= 0) return 0
            val skipped = input.skip(room)
            if (skipped > 0) consumed += skipped
            return skipped
        }
    }
}