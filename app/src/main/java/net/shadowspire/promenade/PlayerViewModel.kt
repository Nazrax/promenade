package net.shadowspire.promenade

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class PlayerViewModel : ViewModel() {
    // Available tracks from the folder
    var availableTracks by mutableStateOf<List<TrackData>>(emptyList())
        private set

    // Playlists
    var playlists by mutableStateOf<List<PlaylistData>>(emptyList())
        private set
    var activePlaylist by mutableStateOf<PlaylistData?>(null)
        private set
    var resolvedPlaylistTracks by mutableStateOf<List<TrackData>>(emptyList())
        private set
    var playlistPosition by mutableIntStateOf(-1)
        private set

    // Current track
    var currentTrack by mutableStateOf<TrackData?>(null)
        private set

    // Playback state
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var currentTimeMs by mutableIntStateOf(0)
        private set
    var totalDurationMs by mutableIntStateOf(0)
        private set

    // Repetition tracking
    var currentRepetition by mutableIntStateOf(0)
        private set

    // Calls muting
    var callsMuted by mutableStateOf(false)
    var muteAfterReps by mutableStateOf<Int?>(null)
    var muteWithRepsRemaining by mutableStateOf<Int?>(null)

    // Folder path
    var folderPath by mutableStateOf("")
        private set

    private var musicPlayer: MediaPlayer? = null
    private var callsPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun setFolder(path: String, context: Context) {
        folderPath = path
        context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
            .edit().putString("folder_path", path).apply()
        loadFolder(path)
    }

    fun restoreFolder(context: Context) {
        val saved = context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
            .getString("folder_path", "") ?: ""
        folderPath = saved
        if (saved.isNotEmpty()) {
            loadFolder(saved)
        }
    }

    fun loadFolder(path: String) {
        availableTracks = loadTracksFromFolder(path)
        playlists = loadPlaylistsFromFolder(path)
    }

    fun reloadPlaylists() {
        playlists = loadPlaylistsFromFolder(folderPath)
        // Refresh active playlist if it still exists
        activePlaylist?.let { active ->
            val updated = playlists.find { it.fileName == active.fileName }
            if (updated != null) {
                selectPlaylist(updated)
            } else {
                activePlaylist = null
                resolvedPlaylistTracks = emptyList()
                playlistPosition = -1
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistData?) {
        activePlaylist = playlist
        if (playlist != null) {
            resolvedPlaylistTracks = playlist.entries.mapNotNull { fileName ->
                availableTracks.find { it.jsonFileName == fileName }
            }
            playlistPosition = -1
        } else {
            resolvedPlaylistTracks = emptyList()
            playlistPosition = -1
        }
    }

    fun playFromPlaylistPosition(context: Context, position: Int) {
        if (position < 0 || position >= resolvedPlaylistTracks.size) return
        playlistPosition = position
        loadTrack(context, resolvedPlaylistTracks[position])
    }

    fun loadTrack(context: Context, track: TrackData) {
        stopPlayback()

        currentTrack = track
        currentRepetition = 0
        callsMuted = false

        // Check auto-mute settings
        checkAutoMute()

        try {
            musicPlayer = MediaPlayer().apply {
                setDataSource(context, track.musicUri)
                prepare()
                setOnCompletionListener { onTrackCompleted(context) }
            }

            callsPlayer = MediaPlayer().apply {
                setDataSource(context, track.callsUri)
                prepare()
            }

            totalDurationMs = musicPlayer?.duration ?: 0
            currentTimeMs = 0
            progress = 0f

            startPlayback()
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error loading track: ${e.message}")
        }
    }

    private fun onTrackCompleted(context: Context) {
        isPlaying = false
        stopProgressUpdates()

        // Auto-advance playlist
        if (activePlaylist != null && playlistPosition >= 0) {
            val nextPos = playlistPosition + 1
            if (nextPos < resolvedPlaylistTracks.size) {
                playFromPlaylistPosition(context, nextPos)
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun startPlayback() {
        musicPlayer?.start()
        callsPlayer?.let {
            it.start()
            if (callsMuted) {
                it.setVolume(0f, 0f)
            }
        }
        isPlaying = true
        startProgressUpdates()
    }

    private fun pausePlayback() {
        musicPlayer?.pause()
        callsPlayer?.pause()
        isPlaying = false
        stopProgressUpdates()
    }

    private fun resumePlayback() {
        musicPlayer?.start()
        callsPlayer?.start()
        isPlaying = true
        startProgressUpdates()
    }

    fun stopPlayback() {
        isPlaying = false
        stopProgressUpdates()

        musicPlayer?.apply {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        callsPlayer?.apply {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        musicPlayer = null
        callsPlayer = null

        currentTimeMs = 0
        progress = 0f
        totalDurationMs = 0
    }

    fun seekTo(fraction: Float) {
        val ms = (fraction * totalDurationMs).toInt()
        musicPlayer?.seekTo(ms)
        callsPlayer?.seekTo(ms)
        currentTimeMs = ms
        progress = fraction
        updateRepetitionFromPosition(ms)
    }

    fun toggleCallsMuted() {
        callsMuted = !callsMuted
        applyCallsVolume()
    }

    fun updateCallsMuted(muted: Boolean) {
        callsMuted = muted
        applyCallsVolume()
    }

    private fun applyCallsVolume() {
        callsPlayer?.setVolume(
            if (callsMuted) 0f else 1f,
            if (callsMuted) 0f else 1f
        )
    }

    private fun checkAutoMute() {
        val track = currentTrack ?: return
        val totalReps = track.repetitionCount

        muteAfterReps?.let { limit ->
            if (currentRepetition >= limit) {
                callsMuted = true
                applyCallsVolume()
                return
            }
        }

        muteWithRepsRemaining?.let { remaining ->
            if (totalReps - currentRepetition <= remaining) {
                callsMuted = true
                applyCallsVolume()
                return
            }
        }
    }

    private fun updateRepetitionFromPosition(ms: Int) {
        val track = currentTrack ?: return
        val seconds = ms / 1000.0
        var rep = 0
        for (i in track.repetitions.indices) {
            if (seconds >= track.repetitions[i].start) {
                rep = i + 1
            }
        }
        if (rep != currentRepetition) {
            currentRepetition = rep
            checkAutoMute()
        }
    }

    fun skipToNextTrack(context: Context) {
        if (activePlaylist != null && playlistPosition >= 0) {
            val nextPos = playlistPosition + 1
            if (nextPos < resolvedPlaylistTracks.size) {
                playFromPlaylistPosition(context, nextPos)
            }
        }
    }

    fun skipToPreviousTrack(context: Context) {
        if (activePlaylist != null && playlistPosition >= 0) {
            val prevPos = playlistPosition - 1
            if (prevPos >= 0) {
                playFromPlaylistPosition(context, prevPos)
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                musicPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentTimeMs = player.currentPosition
                        val duration = player.duration
                        progress = if (duration > 0) {
                            currentTimeMs.toFloat() / duration
                        } else 0f
                        totalDurationMs = duration
                        updateRepetitionFromPosition(currentTimeMs)
                    }
                }
                handler.postDelayed(this, 250)
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
