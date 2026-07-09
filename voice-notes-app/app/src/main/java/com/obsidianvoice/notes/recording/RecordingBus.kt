package com.obsidianvoice.notes.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, process-wide transcription state. The [RecordingService] is the only
 * writer; the UI observes it read-only. Kept as a singleton so the running
 * service and a freshly recreated Activity always see the same live transcript.
 */
object RecordingBus {

    data class State(
        val isRecording: Boolean = false,
        val finalizedSegments: List<String> = emptyList(),
        val partial: String = "",
        val error: String? = null,
    ) {
        /** Full transcript so far: finalized segments plus the in-flight partial. */
        val fullText: String
            get() = buildString {
                append(finalizedSegments.joinToString(" ").trim())
                if (partial.isNotBlank()) {
                    if (isNotEmpty()) append(' ')
                    append(partial.trim())
                }
            }

        val hasContent: Boolean
            get() = finalizedSegments.any { it.isNotBlank() } || partial.isNotBlank()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    internal fun setRecording(recording: Boolean) {
        _state.value = _state.value.copy(isRecording = recording, error = null)
    }

    internal fun appendFinalized(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            finalizedSegments = _state.value.finalizedSegments + trimmed,
            partial = "",
        )
    }

    internal fun setPartial(text: String) {
        _state.value = _state.value.copy(partial = text.trim())
    }

    internal fun setError(message: String) {
        _state.value = _state.value.copy(isRecording = false, error = message)
    }

    /** Clears the transcript, e.g. after the note has been saved. */
    fun reset() {
        _state.value = State()
    }
}
