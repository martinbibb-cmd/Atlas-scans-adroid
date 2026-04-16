package com.atlasscans.android.data.repository

import com.atlasscans.android.data.database.CaptureSessionDao
import com.atlasscans.android.data.models.CaptureSessionDraft
import com.atlasscans.android.data.models.SessionCaptureV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Single source of truth for draft session persistence.
 */
class SessionRepository(private val dao: CaptureSessionDao) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** Observe the persisted draft session, decoded from JSON. */
    fun observeDraft(): Flow<SessionCaptureV2?> =
        dao.observeDraft().map { draft ->
            draft?.let { json.decodeFromString(it.sessionJson) }
        }

    /** Persist the current in-memory session to the database. */
    suspend fun saveDraft(session: SessionCaptureV2) {
        dao.saveDraft(
            CaptureSessionDraft(
                sessionJson = json.encodeToString(session),
                lastModified = System.currentTimeMillis(),
            )
        )
    }

    /** Remove the draft after a successful export. */
    suspend fun deleteDraft() {
        dao.deleteDraft()
    }

    /** Serialise a [SessionCaptureV2] to a pretty-printed JSON string for export. */
    fun serialiseForExport(session: SessionCaptureV2): String =
        Json { prettyPrint = true }.encodeToString(session)
}
