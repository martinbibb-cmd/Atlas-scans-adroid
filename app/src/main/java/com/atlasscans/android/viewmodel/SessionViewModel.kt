package com.atlasscans.android.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atlasscans.android.data.models.CapturedPhotoV2
import com.atlasscans.android.data.models.CapturedRoomScanV2
import com.atlasscans.android.data.models.SessionCaptureV2
import com.atlasscans.android.data.models.VoiceNoteV2
import com.atlasscans.android.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Activity-scoped ViewModel that owns the single-session state.
 *
 * Survives configuration changes (screen rotation, swipe between pages).
 * Persists the draft to Room after every mutation so the user never loses
 * work-in-progress even after a process death.
 */
class SessionViewModel(
    private val repository: SessionRepository,
) : ViewModel() {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _session = MutableStateFlow(createNewSession())
    /** Current in-memory capture session. */
    val session: StateFlow<SessionCaptureV2> = _session.asStateFlow()

    init {
        // Restore any persisted draft from the database on first launch.
        viewModelScope.launch {
            repository.observeDraft().collect { draft ->
                if (draft != null && draft.id == _session.value.id) {
                    _session.value = draft
                } else if (draft != null) {
                    // Restore a previously interrupted session.
                    _session.value = draft
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Room Scan
    // -------------------------------------------------------------------------

    /** Attach the completed room scan to the current session and persist. */
    fun setRoomScan(scan: CapturedRoomScanV2) {
        _session.update { it.copy(roomScan = scan) }
        persistDraft()
    }

    // -------------------------------------------------------------------------
    // Photos
    // -------------------------------------------------------------------------

    /** Add a captured photo (with optional pin metadata) to the session. */
    fun addPhoto(photo: CapturedPhotoV2) {
        _session.update { it.copy(photos = it.photos + photo) }
        persistDraft()
    }

    /** Remove a photo by id (e.g. if the user discards it from the summary). */
    fun removePhoto(photoId: String) {
        _session.update { it.copy(photos = it.photos.filter { p -> p.id != photoId }) }
        persistDraft()
    }

    // -------------------------------------------------------------------------
    // Voice Notes
    // -------------------------------------------------------------------------

    /** Add a completed voice note to the session. */
    fun addVoiceNote(note: VoiceNoteV2) {
        _session.update { it.copy(voiceNotes = it.voiceNotes + note) }
        persistDraft()
    }

    /** Remove a voice note by id. */
    fun removeVoiceNote(noteId: String) {
        _session.update { it.copy(voiceNotes = it.voiceNotes.filter { n -> n.id != noteId }) }
        persistDraft()
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    /**
     * Serialise the current session to a JSON file in the app's cache directory
     * and return a share [Intent] so the user can send it to Atlas Mind or any
     * other recipient.
     *
     * The export intentionally excludes raw audio/video files; only the
     * transcript strings are carried in the JSON payload.
     */
    fun buildExportIntent(context: Context): Intent {
        val jsonString = repository.serialiseForExport(_session.value)
        val exportFile = File(context.cacheDir, "atlas_session_${_session.value.id}.json")
        exportFile.writeText(jsonString)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile,
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Atlas Scans Session – ${_session.value.id}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /**
     * Discard the current session and start a fresh one.
     * Also clears the persisted draft from the database.
     */
    fun newSession() {
        viewModelScope.launch {
            repository.deleteDraft()
            _session.value = createNewSession()
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun persistDraft() {
        viewModelScope.launch { repository.saveDraft(_session.value) }
    }

    companion object {
        fun createNewSession() = SessionCaptureV2(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            roomScan = null,
            photos = emptyList(),
            voiceNotes = emptyList(),
        )
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SessionViewModel(repository) as T
    }
}
