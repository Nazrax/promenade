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

import java.io.File

class PlayerViewModel : ViewModel() {

    // ---- Track list state ----
    var tracks by mutableStateOf<List<TrackData>>(emptyList())
        private set

    var currentTrack by mutableStateOf<TrackData?>(null)
        private set

    // ---- Playback state ----
    var isPlaying by mutableStateOf(false)
        private set

    var duration by mutableFloatStateOf(0f)
        private set

    var position by mutableFloatStateOf(0f)
        private set

    var isSeeking by mutableStateOf(false)

    /** Balance: 0.0 = all music, 1.0 = all calls, 0.5 = equal */
    var balance by mutableFloatStateOf(0.5f)

    // ---- Private internals ----
    private var musicPlayer: MediaPlayer? = null
    private var callsPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val updatePosition = object : Runnable {
        override fun run() {
            val mp = musicPlayer ?: return
            if (mp.isPlaying && !isSeeking) {
                position = mp.currentPosition.toFloat()
            }
            if (mp.isPlaying) {
                handler.postDelayed(this, 200L)
            }
        }
    }

    // ---- Folder loading ----
    fun loadFolder(folderPath: String) {
        tracks = loadTracksFromFolder(folderPath)
    }

    // ---- Track loading ----
    fun loadTrack(context: Context, track: TrackData) {
        stop()

        musicPlayer = MediaPlayer().apply {
            setDataSource(context, track.musicUri)
            prepare()
        }
        callsPlayer = MediaPlayer().apply {
            setDataSource(context, track.callsUri)
            prepare()
        }

        currentTrack = track
        duration = musicPlayer!!.duration.toFloat()
        position = 0f
        isPlaying = false

        applyBalance()

        // When music ends, stop everything
        musicPlayer!!.setOnCompletionListener {
            stop()
        }
    }

    // ---- Playback controls ----
    fun togglePlayPause() {
        val mp = musicPlayer ?: return
        val cp = callsPlayer ?: return

        if (mp.isPlaying) {
            mp.pause()
            cp.pause()
            isPlaying = false
            handler.removeCallbacks(updatePosition)
        } else {
            mp.start()
            cp.start()
            isPlaying = true
            handler.post(updatePosition)
        }
    }

    fun stop() {
        handler.removeCallbacks(updatePosition)
        musicPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        callsPlayer?.run {
            if (isPlaying) stop()
            release()
        }
        musicPlayer = null
        callsPlayer = null
        isPlaying = false
        position = 0f
        duration = 0f
    }

    fun onSeekMove(fraction: Float) {
        isSeeking = true
        position = fraction * duration
    }

    fun onSeekEnd() {
        musicPlayer?.seekTo(position.toInt())
        callsPlayer?.seekTo(position.toInt())
        isSeeking = false
    }

    // ---- Balance ----
    /**
     * Applies volume to both players based on the balance value.
     *
     * At balance = 0.5 (center), both play at 0.707 volume each.
     * This is an equal-power crossfade: the perceived total loudness
     * stays constant across the full range of the slider, and the two
     * tracks at center won't clip even if each MP3 is normalized to
     * full scale.
     *
     * At balance = 0.0, music is 1.0 and calls is 0.0.
     * At balance = 1.0, music is 0.0 and calls is 1.0.
     */
    fun applyBalance() {
        val mp = musicPlayer ?: return
        val cp = callsPlayer ?: return

        val musicVolume = Math.cos(balance * Math.PI / 2.0).toFloat()
        val callsVolume = Math.sin(balance * Math.PI / 2.0).toFloat()

        mp.setVolume(musicVolume, musicVolume)
        cp.setVolume(callsVolume, callsVolume)
    }

    // ---- Cleanup ----
    override fun onCleared() {
        stop()
    }
}
