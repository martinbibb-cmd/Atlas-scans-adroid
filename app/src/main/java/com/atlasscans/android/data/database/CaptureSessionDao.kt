package com.atlasscans.android.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasscans.android.data.models.CaptureSessionDraft
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureSessionDao {

    /** Observe the current draft (emits null when no draft exists). */
    @Query("SELECT * FROM capture_session_draft WHERE id = :draftId LIMIT 1")
    fun observeDraft(draftId: String = CaptureSessionDraft.DRAFT_ID): Flow<CaptureSessionDraft?>

    /** Insert or replace the current draft. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: CaptureSessionDraft)

    /** Delete the current draft (called after a successful export). */
    @Query("DELETE FROM capture_session_draft WHERE id = :draftId")
    suspend fun deleteDraft(draftId: String = CaptureSessionDraft.DRAFT_ID)
}
