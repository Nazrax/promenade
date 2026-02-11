package net.shadowspire.promenade.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import net.shadowspire.promenade.PlayerViewModel

// ---- Top-level screen composable ----
// This is the only thing MainActivity needs to know about.

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ---- File picker and permission launchers ----
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFile(context, uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            filePickerLauncher.launch(arrayOf("audio/*"))
        }
    }

    // ---- Layout ----
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TrackName(name = viewModel.fileName)

        Spacer(modifier = Modifier.height(24.dp))

        OpenButton(onClick = {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED
            ) {
                filePickerLauncher.launch(arrayOf("audio/*"))
            } else {
                permissionLauncher.launch(permission)
            }
        })

        Spacer(modifier = Modifier.height(24.dp))

        SeekBar(
            position = viewModel.position,
            duration = viewModel.duration,
            onSeekStart = { fraction -> viewModel.onSeekStart(fraction) },
            onSeekEnd = { viewModel.onSeekEnd() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlayPauseButton(
            isPlaying = viewModel.isPlaying,
            onClick = { viewModel.togglePlayPause() }
        )
    }
}

// ---- Small, focused composables ----

@Composable
private fun TrackName(name: String) {
    Text(
        text = name,
        fontSize = 18.sp
    )
}

@Composable
private fun OpenButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text("Open MP3")
    }
}

@Composable
private fun SeekBar(
    position: Float,
    duration: Float,
    onSeekStart: (Float) -> Unit,
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
        onValueChange = { fraction -> onSeekStart(fraction) },
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

// ---- Utility ----

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
