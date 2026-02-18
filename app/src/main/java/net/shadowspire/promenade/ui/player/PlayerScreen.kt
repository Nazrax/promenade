package net.shadowspire.promenade.ui.player

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

import net.shadowspire.promenade.DEFAULT_FOLDER_PATH
import net.shadowspire.promenade.PlayerViewModel
import net.shadowspire.promenade.TrackData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateToPlaylistEditor: () -> Unit,
    onNavigateToInstructions: (trackName: String, path: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show warning messages as snackbars
    val warning = viewModel.warningMessage
    LaunchedEffect(warning) {
        if (warning != null) {
            snackbarHostState.showSnackbar(
                message = warning,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissWarning()
        }
    }

    // Permission handling
    var hasPermission by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = Environment.isExternalStorageManager()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Restore saved folder on first composition
    LaunchedEffect(Unit) {
        if (viewModel.folderPath.isEmpty()) {
            viewModel.restoreFolder(context)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasPermission) {
                PermissionSection()
            } else {
                // Playlist selector
                PlaylistSelectorRow(
                    viewModel = viewModel,
                    onNavigateToPlaylistEditor = onNavigateToPlaylistEditor
                )

                // Playlist view (top portion)
                PlaylistSection(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )

                // Player controls (bottom)
                PlayerControlsSection(
                    viewModel = viewModel,
                    onNavigateToInstructions = onNavigateToInstructions
                )            }
        }
    }
}

@Composable
private fun PermissionSection() {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = {
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
    }
}

@Composable
private fun PlaylistSelectorRow(
    viewModel: PlayerViewModel,
    onNavigateToPlaylistEditor: () -> Unit
) {
    val context = LocalContext.current
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (viewModel.folderPath.isEmpty()) {
            Text(
                text = "No folder set. Open the playlist editor to set one.",
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (viewModel.playlists.isNotEmpty()) {
            OutlinedButton(onClick = { showPlaylistPicker = true }) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(viewModel.activePlaylist?.name ?: "Select Playlist")
            }
        } else {
            Text(
                text = "No playlists found. Open the playlist editor to create one.",
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Select Playlist") },
            text = {
                LazyColumn {
                    item {
                        TextButton(onClick = {
                            onNavigateToPlaylistEditor()
                            showPlaylistPicker = false
                        }) {
                            Text("Edit Playlists")
                        }
                        HorizontalDivider()
                    }
                    itemsIndexed(viewModel.playlists) { _, playlist ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    playlist.name,
                                    fontWeight = if (playlist == viewModel.activePlaylist)
                                        FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            supportingContent = {
                                Text("${playlist.entries.size} tracks")
                            },
                            modifier = Modifier.clickable {
                                viewModel.selectPlaylist(playlist, context)
                                showPlaylistPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.selectPlaylist(null, context)
                    showPlaylistPicker = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistSection(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracks = viewModel.resolvedPlaylistTracks
    val listState = rememberLazyListState()

    // Auto-scroll to current track
    LaunchedEffect(viewModel.playlistPosition) {
        if (viewModel.playlistPosition >= 0) {
            listState.animateScrollToItem(viewModel.playlistPosition)
        }
    }

    if (tracks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (viewModel.availableTracks.isEmpty())
                    "No tracks loaded. Set a folder in the playlist editor."
                else
                    "No playlist selected. Choose a playlist or create one.",
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth()
        ) {
            itemsIndexed(tracks) { index, track ->
                val isCurrent = index == viewModel.playlistPosition

                val containerColor = if (isCurrent)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface

                Surface(
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playFromPlaylistPosition(context, index) }
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = containerColor),
                        headlineContent = {
                            Text(
                                text = track.name,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                "${track.intro} · ${track.repetitionCount} reps",
                                fontSize = 12.sp,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Text(
                                "${index + 1}",
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            if (isCurrent && viewModel.isPlaying) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    )
                }

                if (index < tracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsSection(
    viewModel: PlayerViewModel,
    onNavigateToInstructions: (trackName: String, path: String) -> Unit
) {
    val context = LocalContext.current
    val track = viewModel.currentTrack
    var showMuteSettings by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Track name with optional instructions icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track?.name ?: "No track loaded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (track?.instructionsPath != null) {
                    IconButton(
                        onClick = {
                            onNavigateToInstructions(track.name, track.instructionsPath)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "View instructions",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Intro and repetition info
            if (track != null) {
                RepetitionDisplay(viewModel = viewModel, track = track)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Calls mute controls
            if (track != null) {
                CallsMuteControls(viewModel = viewModel)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Scrub bar + play/pause
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatTime(viewModel.currentTimeMs),
                    fontSize = 12.sp,
                    modifier = Modifier.width(45.dp)
                )

                Slider(
                    value = viewModel.progress,
                    onValueChange = { viewModel.seekTo(it) },
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = formatTime(viewModel.totalDurationMs),
                    fontSize = 12.sp,
                    modifier = Modifier.width(45.dp)
                )

                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    enabled = track != null
                ) {
                    Icon(
                        if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Balance slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Music",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = viewModel.balance,
                    onValueChange = {
                        viewModel.balance = it
                        viewModel.applyBalance()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Text(
                    "Calls",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.callsMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { viewModel.toggleCallsMuted() }
                )

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.toggleCallsMuted() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (viewModel.callsMuted) Icons.Default.VolumeOff else Icons.Default.RecordVoiceOver,
                        contentDescription = if (viewModel.callsMuted) "Unmute calls" else "Mute calls",
                        tint = if (viewModel.callsMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showMuteSettings = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Mute settings",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showMuteSettings) {
        AutoMuteDialog(
            muteAfterReps = viewModel.muteAfterReps,
            muteWithRepsRemaining = viewModel.muteWithRepsRemaining,
            onDismiss = { showMuteSettings = false },
            onSave = { after, remaining ->
                viewModel.muteAfterReps = after
                viewModel.muteWithRepsRemaining = remaining
                showMuteSettings = false
            }
        )
    }
}

@Composable
private fun RepetitionDisplay(viewModel: PlayerViewModel, track: TrackData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Intro: ${track.intro}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (track.repetitionCount > 0) {
            Text(
                text = "Rep ${viewModel.currentRepetition} / ${track.repetitionCount}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CallsMuteControls(viewModel: PlayerViewModel) {
}

@Composable
private fun AutoMuteDialog(
    muteAfterReps: Int?,
    muteWithRepsRemaining: Int?,
    onDismiss: () -> Unit,
    onSave: (after: Int?, remaining: Int?) -> Unit
) {
    var afterText by remember { mutableStateOf(muteAfterReps?.toString() ?: "") }
    var remainingText by remember { mutableStateOf(muteWithRepsRemaining?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Mute Calls") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Leave blank to disable auto-mute.",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic
                )

                OutlinedTextField(
                    value = afterText,
                    onValueChange = { afterText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Mute after X reps") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = remainingText,
                    onValueChange = { remainingText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Mute with X reps remaining") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    afterText.toIntOrNull(),
                    remainingText.toIntOrNull()
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
