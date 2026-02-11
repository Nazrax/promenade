package net.shadowspire.promenade

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class PlayerViewModel : ViewModel() {

    // ---- Public observable state ----
    // The UI reads these. "mutableStateOf" makes Compose recompose
    // whenever they change. The ViewModel owns them; the UI just looks.
    var fileName by mutableStateOf("No file selected")
        private set  // only this class can write; outside code can read

    var isPlaying by mutableStateOf(false)
        private set

    var duration by mutableFloatStateOf(0f)
        private set

    var position by mutableFloatStateOf(0f)
        private set

    var isSeeking by mutableStateOf(false)
        // public set — the UI needs to set this when dragging starts/stops

    // ---- Private internals ----
    private val mediaPlayer = MediaPlayer()
    private val handler = Handler(Looper.getMainLooper())

    private val updatePosition = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying && !isSeeking) {
                position = mediaPlayer.currentPosition.toFloat()
            }
            if (mediaPlayer.isPlaying) {
                handler.postDelayed(this, 200L)
            }
        }
    }

    // ---- Public actions (called by the UI) ----

    fun loadFile(context: Context, uri: Uri) {
        // Persist permission so the URI survives app restarts
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        fileName = uri.lastPathSegment ?: "Unknown"

        mediaPlayer.reset()
        mediaPlayer.setDataSource(context, uri)
        mediaPlayer.prepare()

        duration = mediaPlayer.duration.toFloat()
        position = 0f
        isPlaying = false
    }

    fun togglePlayPause() {
        if (duration <= 0f) return  // no file loaded

        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            handler.removeCallbacks(updatePosition)
        } else {
            mediaPlayer.start()
            isPlaying = true
            handler.post(updatePosition)
        }
    }

    fun onSeekStart(fraction: Float) {
        isSeeking = true
        position = fraction * duration
    }

    fun onSeekEnd() {
        mediaPlayer.seekTo(position.toInt())
        isSeeking = false
    }

    // ---- Cleanup ----
    // ViewModel.onCleared() is called when the Activity is permanently
    // destroyed (not on rotation — ViewModels survive rotation)
    override fun onCleared() {
        handler.removeCallbacks(updatePosition)
        mediaPlayer.release()
    }
}
