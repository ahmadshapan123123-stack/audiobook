package com.example.audiobook.data.room

import androidx.room.TypeConverter
import java.util.UUID
import com.example.audiobook.data.room.entity.*

class RoomConverters {
    @TypeConverter
    fun fromUuid(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter fun fromScanStatus(value: ScanStatus?) = value?.name
    @TypeConverter fun toScanStatus(value: String?) = value?.let(ScanStatus::valueOf)
    @TypeConverter fun fromCoverSource(value: CoverSource?) = value?.name
    @TypeConverter fun toCoverSource(value: String?) = value?.let(CoverSource::valueOf)
    @TypeConverter fun fromSyncStatus(value: SyncStatus?) = value?.name
    @TypeConverter fun toSyncStatus(value: String?) = value?.let(SyncStatus::valueOf)
    @TypeConverter fun fromFileStatus(value: FileStatus?) = value?.name
    @TypeConverter fun toFileStatus(value: String?) = value?.let(FileStatus::valueOf)
    @TypeConverter fun fromChapterCreatedFrom(value: ChapterCreatedFrom?) = value?.name
    @TypeConverter fun toChapterCreatedFrom(value: String?) = value?.let(ChapterCreatedFrom::valueOf)
    @TypeConverter fun fromBookmarkType(value: BookmarkType?) = value?.name
    @TypeConverter fun toBookmarkType(value: String?) = value?.let(BookmarkType::valueOf)
    @TypeConverter fun fromProgressStatus(value: ProgressStatus?) = value?.name
    @TypeConverter fun toProgressStatus(value: String?) = value?.let(ProgressStatus::valueOf)
    @TypeConverter fun fromSessionEndReason(value: SessionEndReason?) = value?.name
    @TypeConverter fun toSessionEndReason(value: String?) = value?.let(SessionEndReason::valueOf)
    @TypeConverter fun fromSessionState(value: SessionState?) = value?.name
    @TypeConverter fun toSessionState(value: String?) = value?.let(SessionState::valueOf)
    @TypeConverter fun fromUserDecision(value: UserDecision?) = value?.name
    @TypeConverter fun toUserDecision(value: String?) = value?.let(UserDecision::valueOf)
}