package com.example.audiobook.domain.usecases

import org.json.JSONObject

/**
 * تشفير وفك تشفير لقطة الإشارات (signalsSnapshot) المحفوظة في EditionMatchDecision.
 * تُخزَّن القيم الفعلية وقت اتخاذ القرار + مفتاح نمط الزوج لاستخدامه لاحقًا
 * في تعديل أوزان المطابقة الخاصة بهذا المستخدم.
 */
object EditionSignalsCodec {

    fun toJson(subject: EditionSignals, candidate: EditionSignals?): String {
        val root = JSONObject()
        root.put("patternKey", EditionIntelligence.pairPatternKey(subject, candidate ?: subject))
        root.put("subject", signalsToJson(subject))
        if (candidate != null) root.put("candidate", signalsToJson(candidate))
        return root.toString()
    }

    fun parsePairPatternKey(json: String): String? = runCatching {
        JSONObject(json).optString("patternKey").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun parseSignals(json: String): EditionSignals = runCatching {
        val signals = JSONObject(json).optJSONObject("subject")
        if (signals == null) EditionSignals() else signalsFromJson(signals)
    }.getOrElse { EditionSignals() }

    private fun signalsToJson(signals: EditionSignals): JSONObject = JSONObject().apply {
        put("primaryFileName", signals.primaryFileName)
        put("folderName", signals.folderName)
        put("authorFolderName", signals.authorFolderName.orEmpty())
        put("seriesPattern", signals.seriesPart?.pattern.orEmpty())
        put("seriesPartNumber", signals.seriesPart?.partNumber?.toString() ?: JSONObject.NULL)
        put("embeddedTitle", signals.embeddedTags?.title.orEmpty())
        put("embeddedNarrator", signals.embeddedTags?.narrator.orEmpty())
        put("embeddedGenre", signals.embeddedTags?.genre.orEmpty())
        put("totalDurationMs", signals.totalDurationMs)
        put("fileCount", signals.fileCount)
        put("filesOrdered", signals.filesOrdered)
        put("narrator", signals.narrator.orEmpty())
        put("format", signals.format.orEmpty())
    }

    private fun signalsFromJson(json: JSONObject): EditionSignals {
        val seriesPattern = json.optString("seriesPattern")
        val seriesPart = if (seriesPattern.isBlank()) null else SeriesPart(
            pattern = seriesPattern,
            partNumber = json.optString("seriesPartNumber").takeIf { it.isNotBlank() }?.toIntOrNull()
        )
        val embedded = EmbeddedTags(
            title = json.optString("embeddedTitle").takeIf { it.isNotBlank() },
            narrator = json.optString("embeddedNarrator").takeIf { it.isNotBlank() },
            genre = json.optString("embeddedGenre").takeIf { it.isNotBlank() }
        )
        return EditionSignals(
            primaryFileName = json.optString("primaryFileName"),
            folderName = json.optString("folderName"),
            authorFolderName = json.optString("authorFolderName").takeIf { it.isNotBlank() },
            seriesPart = seriesPart,
            embeddedTags = embedded
                .let { if (it.title == null && it.narrator == null && it.genre == null) null else it },
            totalDurationMs = json.optLong("totalDurationMs"),
            fileCount = json.optInt("fileCount"),
            filesOrdered = json.optBoolean("filesOrdered"),
            narrator = json.optString("narrator").takeIf { it.isNotBlank() },
            format = json.optString("format").takeIf { it.isNotBlank() }
        )
    }
}