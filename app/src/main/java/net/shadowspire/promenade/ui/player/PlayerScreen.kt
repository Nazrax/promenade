package net.shadowspire.promenade.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shadowspire.promenade.PlayerViewModel
import net.shadowspire.promenade.TrackData
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri


@Composable
fun PlayerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Top half: track list and folder picker
        TrackListSection(
            tracks = viewModel.tracks,
            viewModel = viewModel,
            modifier = Modifier.weight(1f)
        )

        // Bottom half: playback controls
        PlaybackSection(
            viewModel = viewModel,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TrackListSection(
    tracks: List<TrackData>,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Force recheck whenever the activity resumes (e.g. coming back from settings)

    var hasPermission by remember { mutableStateOf(
        android.os.Environment.isExternalStorageManager()
    )}

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = android.os.Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.padding(16.dp)) {
        if (!hasPermission) {
            Button(onClick = {
                // Opens the system settings page for all-files access
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
                context.startActivity(intent)
            }) {
                Text("Grant Storage Permission")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Promenade needs access to read your track files.",
                fontStyle = FontStyle.Italic
            )
        } else {
            Button(onClick = {
                // For now, hardcoded path. We'll add a folder picker later.
                viewModel.loadFolder("/sdcard/Music/Promenade")
            }) {
                Text("Load Tracks")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (tracks.isEmpty()) {
            Text(
                text = "No tracks loaded. Place .json track files in Music/ECD.",
                fontStyle = FontStyle.Italic
            )
        } else {
            LazyColumn {
                items(tracks) { track ->
                    TrackCard(
                        track = track,
                        isCurrentTrack = track == viewModel.currentTrack,
                        onClick = { viewModel.loadTrack(context, track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: TrackData,
    isCurrentTrack: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = track.name,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                fontSize = 16.sp
            )
            Text(
                text = "${track.repetitionCount} repetitions · Intro: ${track.intro}",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun PlaybackSection(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Current track name
        Text(
            text = viewModel.currentTrack?.name ?: "No track selected",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Seek bar
        SeekBar(
            position = viewModel.position,
            duration = viewModel.duration,
            onSeekMove = { fraction -> viewModel.onSeekMove(fraction) },
            onSeekEnd = { viewModel.onSeekEnd() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Play/Pause
        PlayPauseButton(
            isPlaying = viewModel.isPlaying,
            onClick = { viewModel.togglePlayPause() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Balance slider
        BalanceSlider(
            balance = viewModel.balance,
            onBalanceChange = { newBalance ->
                viewModel.balance = newBalance
                viewModel.applyBalance()
            }
        )
    }
}

@Composable
private fun SeekBar(
    position: Float,
    duration: Float,
    onSeekMove: (Float) -> Unit,
    onSeekEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatTime(position.toInt()))
        Text(formatTime(duration.toInt()))
    }

    Slider(
        value = if (duration > 0f) position / duration else 0f,
        onValueChange = { fraction -> onSeekMove(fraction) },
        onValueChangeFinished = { onSeekEnd() },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(if (isPlaying) "Pause" else "Play")
    }
}

@Composable
private fun BalanceSlider(
    balance: Float,
    onBalanceChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Balance", fontSize = 14.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Music", fontSize = 12.sp)
            Text("Calls", fontSize = 12.sp)
        }

        Slider(
            value = balance,
            onValueChange = onBalanceChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
