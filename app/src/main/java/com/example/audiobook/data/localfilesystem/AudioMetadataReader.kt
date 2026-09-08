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
    val embeddedChapters: List<EmbeddedChapter>
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
                }
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

private object M4bChapterParser {
    private const val TAG = "M4bChapterParser"
    private val containers = setOf("moov", "udta", "meta", "ilst")

    fun parse(input: java.io.InputStream?): List<EmbeddedChapter> {
        if (input == null) return emptyList()
        return try {
            val bytes = input.use { it.readBytes() }
            findChpl(bytes, 0, bytes.size)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse embedded M4B chapters", error)
            emptyList()
        }
    }

    private fun findChpl(bytes: ByteArray, start: Int, end: Int): List<EmbeddedChapter> {
        var offset = start
        while (offset + 8 <= end) {
            val size = readUInt32(bytes, offset).toLong()
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val boxEnd = when {
                size == 0L -> end.toLong()
                size == 1L && offset + 16 <= end -> readUInt64(bytes, offset + 8) + offset
                else -> offset + size
            }.coerceAtMost(end.toLong()).toInt()
            val header = if (size == 1L) 16 else 8
            if (type == "chpl") return parseChpl(bytes, offset + header, boxEnd)
            if (type in containers && offset + header < boxEnd) {
                val nested = findChpl(bytes, offset + header, boxEnd)
                if (nested.isNotEmpty()) return nested
            }
            if (boxEnd <= offset) break
            offset = boxEnd
        }
        return emptyList()
    }

    private fun parseChpl(bytes: ByteArray, start: Int, end: Int): List<EmbeddedChapter> {
        if (start + 5 > end) return emptyList()
        val count = bytes[start + 4].toInt() and 0xff
        var offset = start + 5
        val result = mutableListOf<EmbeddedChapter>()
        repeat(count) {
            if (offset + 9 > end) return@repeat
            val startTicks = readUInt64(bytes, offset)
            offset += 8
            val titleLength = bytes[offset].toInt() and 0xff
            offset++
            if (offset + titleLength > end) return@repeat
            val title = String(bytes, offset, titleLength, Charsets.UTF_8)
            offset += titleLength
            result += EmbeddedChapter(title.ifBlank { null }, startTicks / 10_000L)
        }
        return result
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readUInt64(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(8) { value = (value shl 8) or (bytes[offset + it].toLong() and 0xff) }
        return value
    }
}