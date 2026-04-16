package com.atlasscans.android.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database entity that persists an in-progress capture session as a
 * JSON blob.  A single row (id = DRAFT_ID) is maintained so that the user
 * can recover work-in-progress after a process death or screen rotation.
 */
@Entity(tableName = "capture_session_draft")
data class CaptureSessionDraft(
    @PrimaryKey
    val id: String = DRAFT_ID,
    /** Serialised [SessionCaptureV2] JSON. */
    val sessionJson: String,
    /** Unix epoch ms of the last modification. */
    val lastModified: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DRAFT_ID = "current_draft"
    }
}
