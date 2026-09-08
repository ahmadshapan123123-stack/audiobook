package com.example.audiobook.domain.usecases

object ArabicSearchNormalizer {
    private val diacritics = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    fun normalize(value: String): String = value
        .lowercase()
        .replace(diacritics, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ٱ', 'ا')
        .replace('ة', 'ه')
        .replace('ى', 'ي')
        .replace('ؤ', 'و')
        .replace('ئ', 'ي')
        .filterNot(Char::isWhitespace)
        .trim()

    fun matches(query: String, vararg fields: String?): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return fields.filterNotNull().any { normalize(it).contains(normalizedQuery) }
    }
}