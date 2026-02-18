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

    // Balance: 0.0 = all music, 1.0 = all calls, 0.5 = equal
    var balance by mutableFloatStateOf(0.5f)

    // Folder path
    var folderPath by mutableStateOf("")
        private set

    // Warning message for UI to display
    var warningMessage by mutableStateOf<String?>(null)

    private var musicPlayer: MediaPlayer? = null
    private var callsPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun dismissWarning() {
        warningMessage = null
    }

    fun setFolder(path: String, context: Context) {
        folderPath = path
        context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
            .edit().putString("folder_path", path).apply()
        loadFolder(path)

        // Auto-load last playlist
        val lastPlaylistFileName = context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
            .getString("last_playlist", null)
        if (lastPlaylistFileName != null) {
            val playlist = playlists.find { it.fileName == lastPlaylistFileName }
            if (playlist != null) {
                selectPlaylist(playlist, context)
            }
        }
    }

    fun restoreFolder(context: Context) {
        val saved = context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
            .getString("folder_path", DEFAULT_FOLDER_PATH) ?: DEFAULT_FOLDER_PATH
        folderPath = saved
        if (saved.isNotEmpty()) {
            loadFolder(saved)

            // Auto-load last playlist
            val lastPlaylistFileName = context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
                .getString("last_playlist", null)
            if (lastPlaylistFileName != null) {
                val playlist = playlists.find { it.fileName == lastPlaylistFileName }
                if (playlist != null) {
                    selectPlaylist(playlist, context)
                }
            }
        }
    }

    fun loadFolder(path: String) {
        val (tracks, trackWarnings) = loadTracksFromFolder(path)
        availableTracks = tracks
        playlists = loadPlaylistsFromFolder(path)

        if (trackWarnings.isNotEmpty()) {
            warningMessage = trackWarnings.joinToString("\n")
        }
    }

    fun reloadPlaylists() {
        playlists = loadPlaylistsFromFolder(folderPath)
        activePlaylist?.let { active ->
            val updated = playlists.find { it.fileName == active.fileName }
            if (updated != null) {
                resolvePlaylist(updated)
            } else {
                activePlaylist = null
                resolvedPlaylistTracks = emptyList()
                playlistPosition = -1
            }
        }
    }

    fun selectPlaylist(playlist: PlaylistData?, context: Context) {
        if (playlist != null) {
            resolvePlaylist(playlist)
            context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
                .edit().putString("last_playlist", playlist.fileName).apply()

            // Auto-load first track without starting playback
            if (resolvedPlaylistTracks.isNotEmpty()) {
                playlistPosition = 0
                loadTrackWithoutPlaying(context, resolvedPlaylistTracks[0])
            }
        } else {
            activePlaylist = null
            resolvedPlaylistTracks = emptyList()
            playlistPosition = -1
            context.getSharedPreferences("promenade", Context.MODE_PRIVATE)
                .edit().remove("last_playlist").apply()
        }
    }

    private fun resolvePlaylist(playlist: PlaylistData) {
        activePlaylist = playlist
        val resolved = mutableListOf<TrackData>()
        val warnings = mutableListOf<String>()

        for (fileName in playlist.entries) {
            val track = availableTracks.find { it.jsonFileName == fileName }
            if (track != null) {
                resolved.add(track)
            } else {
                warnings.add("Playlist \"${playlist.name}\": track \"$fileName\" not found")
            }
        }

        resolvedPlaylistTracks = resolved

        if (warnings.isNotEmpty()) {
            warningMessage = warnings.joinToString("\n")
        }
    }

    fun playFromPlaylistPosition(context: Context, position: Int) {
        if (position < 0 || position >= resolvedPlaylistTracks.size) return
        playlistPosition = position
        loadTrackWithoutPlaying(context, resolvedPlaylistTracks[position])
    }

    private fun loadTrackWithoutPlaying(context: Context, track: TrackData) {
        stopPlayback()

        currentTrack = track
        currentRepetition = 0
        callsMuted = false

        checkAutoMute()

        try {
            musicPlayer = MediaPlayer().apply {
                setDataSource(context, track.musicUri)
                prepare()
                setOnCompletionListener { onTrackCompleted(context) }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerViewModel", "Error loading music: ${e.message}")
            warningMessage = "Could not load music file for \"${track.name}\": ${e.message}"
            currentTrack = null
            return
        }

        if (track.callsUri != null) {
            try {
                callsPlayer = MediaPlayer().apply {
                    setDataSource(context, track.callsUri)
                    prepare()
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Error loading calls: ${e.message}")
                warningMessage = "Could not load calls file for \"${track.name}\": ${e.message}"
                // Continue without calls - just won't have a calls player
                callsPlayer = null
            }
        } else {
            callsPlayer = null
        }

        totalDurationMs = musicPlayer?.duration ?: 0
        currentTimeMs = 0
        progress = 0f

        applyBalance()
    }

    fun loadTrack(context: Context, track: TrackData) {
        loadTrackWithoutPlaying(context, track)
        if (currentTrack != null) {
            startPlayback()
        }
    }

    private fun onTrackCompleted(context: Context) {
        isPlaying = false
        stopProgressUpdates()

        if (activePlaylist != null && playlistPosition >= 0) {
            val nextPos = playlistPosition + 1
            if (nextPos < resolvedPlaylistTracks.size) {
                playlistPosition = nextPos
                loadTrackWithoutPlaying(context, resolvedPlaylistTracks[nextPos])
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
            applyBalance()
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
            try { stop() } catch (_: Exception) {}
            release()
        }
        callsPlayer?.apply {
            try { stop() } catch (_: Exception) {}
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
        if (callsMuted) {
            callsPlayer?.setVolume(0f, 0f)
        } else {
            applyBalance()
        }
    }

    fun applyBalance() {
        val mp = musicPlayer ?: return

        val musicVolume = Math.cos(balance * Math.PI / 2.0).toFloat()
        val callsVolume = Math.sin(balance * Math.PI / 2.0).toFloat()

        mp.setVolume(musicVolume, musicVolume)
        val cp = callsPlayer
        if (cp != null) {
            if (callsMuted) {
                cp.setVolume(0f, 0f)
            } else {
                cp.setVolume(callsVolume, callsVolume)
            }
        }
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