package com.obsidianvoice.notes.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.obsidianvoice.notes.MainActivity
import com.obsidianvoice.notes.R
import com.obsidianvoice.notes.VoiceNotesApplication
import com.obsidianvoice.notes.stt.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Foreground service that owns the microphone and the Vosk recognizer so
 * transcription continues while the screen is off or the app is backgrounded —
 * essential for full-length meetings and lectures.
 */
class RecordingService : Service(), RecognitionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var model: Model? = null
    private var speechService: SpeechService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_STICKY
    }

    private fun start() {
        startForegroundCompat()
        RecordingBus.setRecording(true)

        scope.launch {
            try {
                if (!ModelManager.isReady(this@RecordingService)) {
                    error("Speech model is not ready yet")
                }
                val loaded = withContext(Dispatchers.IO) {
                    Model(ModelManager.modelDir(this@RecordingService).absolutePath)
                }
                model = loaded
                val recognizer = Recognizer(loaded, SAMPLE_RATE)
                withContext(Dispatchers.Main) {
                    speechService = SpeechService(recognizer, SAMPLE_RATE).also {
                        it.startListening(this@RecordingService)
                    }
                }
            } catch (t: Throwable) {
                RecordingBus.setError(t.message ?: "Failed to start recognition")
                stop()
            }
        }
    }

    private fun stop() {
        speechService?.apply {
            stop()
            shutdown()
        }
        speechService = null
        model?.close()
        model = null
        RecordingBus.setRecording(false)
        stopForegroundCompat()
        stopSelf()
    }

    // --- RecognitionListener (Vosk delivers JSON strings) ---------------------

    override fun onPartialResult(hypothesis: String?) {
        hypothesis ?: return
        val partial = runCatching { JSONObject(hypothesis).optString("partial") }.getOrDefault("")
        RecordingBus.setPartial(partial)
    }

    override fun onResult(hypothesis: String?) {
        hypothesis ?: return
        val text = runCatching { JSONObject(hypothesis).optString("text") }.getOrDefault("")
        RecordingBus.appendFinalized(text)
    }

    override fun onFinalResult(hypothesis: String?) {
        hypothesis ?: return
        val text = runCatching { JSONObject(hypothesis).optString("text") }.getOrDefault("")
        RecordingBus.appendFinalized(text)
    }

    override fun onError(exception: Exception?) {
        RecordingBus.setError(exception?.message ?: "Recognition error")
    }

    override fun onTimeout() {
        // Silence timeout — Vosk keeps listening; nothing to do.
    }

    override fun onDestroy() {
        speechService?.shutdown()
        model?.close()
        scope.cancel()
        super.onDestroy()
    }

    // --- Foreground notification ---------------------------------------------

    private fun startForegroundCompat() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification =
            NotificationCompat.Builder(this, VoiceNotesApplication.RECORDING_CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_recording_title))
                .setContentText(getString(R.string.notif_recording_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000f
        private const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.obsidianvoice.notes.START"
        const val ACTION_STOP = "com.obsidianvoice.notes.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
