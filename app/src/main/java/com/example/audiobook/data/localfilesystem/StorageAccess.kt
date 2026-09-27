package com.example.audiobook.data.localfilesystem

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

object StorageAccess {
    const val REQUEST_CODE_OPEN_LIBRARY_ROOT = 1001

    /**
     * الاسم المعروض لجذر SAF: الجزء بعد آخر ':' من `lastPathSegment` (يتخلّص من بادئة
     * المجلد `primary:`)، وإلا المسار كاملًا. مصدر واحد لاسم الجذر في كل الشاشات —
     * الإعداد ومعاينة الإعدادات وجذور المكتبة — فلا يختلف العرض بينها.
     */
    fun displayNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() } ?: uri.toString()

    fun createLibraryRootIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }

    fun persistReadWritePermission(contentResolver: ContentResolver, uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    /**
     * تحقق من استمرار إذن SAF بعد إعادة التثبيت/مسح البيانات: هل uri ما زال
     * ضمن [ContentResolver.persistedUriPermissions]. مقارنة بتسامح المسار
     * (اللاحقات/التطبيع) لتفادي الرفض الزائف على بعض مزوّدي التخزين.
     */
    fun hasPersistedPermission(contentResolver: ContentResolver, uri: Uri): Boolean {
        val target = uri.toString()
        if (target.isBlank()) return false
        return contentResolver.persistedUriPermissions.any { permission ->
            val granted = permission.uri.toString()
            granted == target || target.startsWith("$granted/") || granted.startsWith("$target/")
        }
    }
}