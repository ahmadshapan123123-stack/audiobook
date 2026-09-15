package com.example.audiobook.data.demo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.DATABASE_MIGRATIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Dev-only ContentProvider that makes the seeded demo library actually playable.
 *
 * The seeder (DatabaseSeeder) writes audio files with `content://com.example.audiobook.demo/...`
 * URIs but no provider existed, so ExoPlayer could never open a source and playback was impossible.
 * This provider answers those URIs with a real, seekable WAV stream:
 *  - duration = the exact `durationMs` of the matching `audio_files` row (queried from Room), so
 *    timeline math, chapter marks and scrubbing stay true to the seeded data.
 *  - a soft sine whose frequency ramps over time AND starts at a different base per file index,
 *    so a jump to another position/file is audibly distinguishable — the "real audio" acceptance test.
 *  - MISSING files return null so the controller's missing-file handling is exercised.
 * Audio is generated once per URI and cached under cacheDir.
 *
 * Not part of the production path: real books use real files/documents via SAF.
 */
class DemoAudioProvider : ContentProvider() {

    private var db: AppDatabase? = null
    private var rootDir: File? = null

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "#", AUDIO)
        addURI(AUTHORITY, "*", AUDIO)
        addURI(AUTHORITY, "*/*", AUDIO)
        addURI(AUTHORITY, "*/*/*", AUDIO)
        addURI(AUTHORITY, "*/*/*/*", AUDIO)
    }

    companion object {
        const val AUTHORITY = "com.example.audiobook.demo"
        private const val AUDIO = 1

        private const val SAMPLE_RATE = 8000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 8

        fun isDemoUri(uri: Uri): Boolean = uri.scheme == "content" && uri.authority == AUTHORITY
    }

    override fun attachInfo(context: android.content.Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        rootDir = File(context.cacheDir, "demo-audio")
        rootDir?.mkdirs()
    }

    override fun onCreate(): Boolean {
        db = Room.databaseBuilder(context!!, AppDatabase::class.java, "audiobook.db")
            .addMigrations(*DATABASE_MIGRATIONS)
            .build()
        return true
    }

    private fun isAudioUri(uri: Uri): Boolean =
        matcher.match(uri) == AUDIO && uri.lastPathSegment.orEmpty().contains('.')

    override fun getType(uri: Uri): String? {
        if (!isDemoUri(uri)) return null
        return if (isAudioUri(uri)) "audio/wav" else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        android.util.Log.d("DemoAudio", "openFile called: $uri")
        if (!isAudioUri(uri)) {
            android.util.Log.d("DemoAudio", "NOT audio uri, match=${matcher.match(uri)}, last=${uri.lastPathSegment}")
            return null
        }
        val dao = db?.audioFileDao() ?: return null

        // الصف يُصدر Durations الحقيقية من قاعدة البيانات المحقونة.
        val entity = runBlocking(Dispatchers.IO) { dao.getByUri(uri.toString()) }
        android.util.Log.d("DemoAudio", "lookup $uri -> ${entity?.fileName} status=${entity?.fileStatus}")
        if (entity == null || entity.fileStatus == com.example.audiobook.data.room.entity.FileStatus.MISSING) return null

        val durationMs = entity.durationMs.coerceAtLeast(1L)
        val file = cachedFile(uri, durationMs, entity.orderIndex)
        android.util.Log.d("DemoAudio", "serving ${file.length()} bytes")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun cachedFile(uri: Uri, durationMs: Long, fileIndex: Int): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val file = File(rootDir, "$hash.wav")
        if (file.exists() && file.length() > 0L) return file
        writeWav(file, durationMs, fileIndex)
        return file
    }

    /** يكتب WAV أحادي 8-bit: نغمة لينة يتغيّر ترددها ببطء مع الوقت ويزداد أساسه مع رقم الملف. */
    private fun writeWav(file: File, durationMs: Long, fileIndex: Int) {
        val numSamples = (durationMs * SAMPLE_RATE / 1000L).toInt()
        val dataSize = numSamples * CHANNELS * BITS_PER_SAMPLE / 8
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        val out = ByteArrayOutputStream(dataSize + 44)
        // RIFF header
        out.write("RIFF".toByteArray())
        writeLeInt(out, 36 + dataSize)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeLeInt(out, 16)
        writeLeShort(out, 1) // PCM
        writeLeShort(out, CHANNELS.toShort())
        writeLeInt(out, SAMPLE_RATE)
        writeLeInt(out, byteRate)
        writeLeShort(out, blockAlign.toShort())
        writeLeShort(out, BITS_PER_SAMPLE.toShort())
        out.write("data".toByteArray())
        writeLeInt(out, dataSize)

        val baseFreq = 220.0 + (fileIndex % 5) * 55.0 // تمييز سمعي بين الملفات
        val rampHzPerSec = 0.35
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = baseFreq + rampHzPerSec * t
            val phase = 2.0 * Math.PI * (baseFreq * t + 0.5 * rampHzPerSec * t * t)
            val value = (128 + 90 * Math.sin(phase)).toInt().coerceIn(0, 255)
            out.write(value)
        }

        BufferedOutputStream(FileOutputStream(file)).use { fos -> out.writeTo(fos) }
    }

    private fun writeLeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun writeLeShort(out: ByteArrayOutputStream, value: Short) {
        out.write(value.toInt() and 0xFF)
        out.write((value.toInt() shr 8) and 0xFF)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}