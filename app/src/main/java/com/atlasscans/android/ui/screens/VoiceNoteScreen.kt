package com.atlasscans.android.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atlasscans.android.data.models.VoiceNoteV2
import com.atlasscans.android.viewmodel.SessionViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/**
 * Voice note screen.
 *
 * - [MediaRecorder] records audio to a temporary .m4a file.
 * - [SpeechRecognizer] runs concurrently to build a real-time transcript.
 * - On stop, only the **transcript** is saved to [SessionViewModel]; the audio
 *   file is deleted so it is never included in the export bundle.
 */
@Composable
fun VoiceNoteScreen(viewModel: SessionViewModel) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()

    var isRecording by remember { mutableStateOf(false) }
    var liveTranscript by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var tempAudioFile by remember { mutableStateOf<File?>(null) }

    // Pulse animation for recording indicator
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Elapsed-time ticker
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val start = System.currentTimeMillis()
            while (isRecording) {
                elapsedSeconds = (System.currentTimeMillis() - start) / 1_000f
                delay(500)
            }
        }
    }

    // Cleanup speech recognizer on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            recorder?.runCatching { stop(); release() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        // ── Recording indicator + timer ───────────────────────────────────────
        Text(
            text = if (isRecording) "%.1f s".format(elapsedSeconds) else "Ready",
            style = MaterialTheme.typography.displaySmall,
        )

        if (isRecording && liveTranscript.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = liveTranscript,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // ── Record / Stop button ──────────────────────────────────────────────
        FloatingActionButton(
            onClick = {
                if (!isRecording) {
                    val (rec, sr, file) = startRecording(context) { partial ->
                        liveTranscript = partial
                    }
                    recorder = rec
                    speechRecognizer = sr
                    tempAudioFile = file
                    isRecording = true
                } else {
                    val finalTranscript = liveTranscript
                    val duration = elapsedSeconds
                    stopRecording(
                        recorder = recorder,
                        speechRecognizer = speechRecognizer,
                        tempAudioFile = tempAudioFile,
                    )
                    recorder = null
                    speechRecognizer = null
                    tempAudioFile = null
                    isRecording = false
                    if (finalTranscript.isNotBlank() || duration > 1f) {
                        viewModel.addVoiceNote(
                            VoiceNoteV2(
                                id = UUID.randomUUID().toString(),
                                transcript = finalTranscript,
                                timestamp = System.currentTimeMillis(),
                                durationSeconds = duration,
                            )
                        )
                    }
                    liveTranscript = ""
                    elapsedSeconds = 0f
                }
            },
            shape = CircleShape,
            containerColor = if (isRecording)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(80.dp)
                .then(if (isRecording) Modifier.scale(pulseScale) else Modifier),
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = if (isRecording) "Tap to stop" else "Tap to record",
            style = MaterialTheme.typography.labelLarge,
        )

        HorizontalDivider()

        // ── Saved voice notes ─────────────────────────────────────────────────
        Text(
            text = "Saved notes (${session.voiceNotes.size})",
            style = MaterialTheme.typography.titleMedium,
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.voiceNotes, key = { it.id }) { note ->
                VoiceNoteCard(note = note, onDelete = { viewModel.removeVoiceNote(it) })
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VoiceNoteCard(note: VoiceNoteV2, onDelete: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.transcript.ifBlank { "(no transcript)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%.1f s".format(note.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(note.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete note")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaRecorder + SpeechRecognizer helpers
// ─────────────────────────────────────────────────────────────────────────────

private data class RecordingHandles(
    val recorder: MediaRecorder,
    val speechRecognizer: SpeechRecognizer,
    val audioFile: File,
)

private operator fun RecordingHandles.component1() = recorder
private operator fun RecordingHandles.component2() = speechRecognizer
private operator fun RecordingHandles.component3() = audioFile

/**
 * Start audio recording and concurrent speech recognition.
 *
 * @param onPartialResult Called on every partial transcript update from the STT engine.
 */
private fun startRecording(
    context: Context,
    onPartialResult: (String) -> Unit,
): RecordingHandles {
    // Temporary audio file – will be deleted after the session, per spec.
    val audioFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")

    @Suppress("DEPRECATION")
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }

    recorder.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44_100)
        setAudioEncodingBitRate(128_000)
        setOutputFile(audioFile.absolutePath)
        prepare()
        start()
    }

    // Launch SpeechRecognizer alongside MediaRecorder for real-time transcription
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        private val accumulated = StringBuilder()

        override fun onResults(results: android.os.Bundle?) {
            val words = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            if (accumulated.isNotEmpty()) accumulated.append(' ')
            accumulated.append(words)
            onPartialResult(accumulated.toString())
            // Restart for continuous recognition
            recognizer.startListening(buildRecognitionIntent())
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: return
            val base = if (accumulated.isNotEmpty()) "$accumulated " else ""
            onPartialResult("$base$partial")
        }

        override fun onError(error: Int) {
            // Restart on recoverable errors
            if (error != SpeechRecognizer.ERROR_CLIENT) {
                runCatching { recognizer.startListening(buildRecognitionIntent()) }
            }
        }

        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
    })
    recognizer.startListening(buildRecognitionIntent())

    return RecordingHandles(recorder, recognizer, audioFile)
}

private fun buildRecognitionIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

/**
 * Stop recording and delete the temporary audio file.
 * Only the transcript (already surfaced via [onPartialResult]) is retained.
 */
private fun stopRecording(
    recorder: MediaRecorder?,
    speechRecognizer: SpeechRecognizer?,
    tempAudioFile: File?,
) {
    speechRecognizer?.apply { stopListening(); destroy() }
    recorder?.runCatching { stop(); release() }
    tempAudioFile?.delete() // Audio file intentionally excluded from export
}
