package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.dao.ListeningSessionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import javax.inject.Inject

class RecoverInterruptedSession @Inject constructor(
    private val sessionDao: ListeningSessionDao,
    private val progressDao: ProgressDao
) {
    suspend operator fun invoke() {
        sessionDao.getActiveSessions().forEach { activeSession ->
            val lastKnownPositionTime = progressDao.getByParent(activeSession.editionId)?.lastPlayedAt
            sessionDao.update(
                activeSession.copy(
                    endedAt = activeSession.endedAt ?: lastKnownPositionTime ?: activeSession.startedAt,
                    endReason = SessionEndReason.INTERRUPTED,
                    sessionState = SessionState.INTERRUPTED
                )
            )
        }
    }
}