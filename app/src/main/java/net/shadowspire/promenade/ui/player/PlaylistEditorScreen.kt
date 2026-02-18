package net.shadowspire.promenade.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

import net.shadowspire.promenade.DEFAULT_FOLDER_PATH
import net.shadowspire.promenade.PlaylistData
import net.shadowspire.promenade.PlayerViewModel
import net.shadowspire.promenade.TrackData
import net.shadowspire.promenade.deletePlaylist
import net.shadowspire.promenade.generatePlaylistFileName
import net.shadowspire.promenade.savePlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedPlaylist by remember { mutableStateOf<PlaylistData?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<PlaylistData?>(null) }
    var showFolderDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedPlaylist != null) {
        selectedPlaylist = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedPlaylist?.name ?: "Playlists")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylist != null) {
                            selectedPlaylist = null
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedPlaylist == null) {
                        IconButton(onClick = { showFolderDialog = true }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Choose folder")
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New Playlist")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Current folder display
            if (selectedPlaylist == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = viewModel.folderPath.ifEmpty { "No folder set" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${viewModel.availableTracks.size} tracks",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            if (viewModel.folderPath.isNotEmpty()) {
                                viewModel.loadFolder(viewModel.folderPath)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                HorizontalDivider()
            }

            if (selectedPlaylist == null) {
                PlaylistListView(
                    playlists = viewModel.playlists,
                    folderPath = viewModel.folderPath,
                    onSelect = { selectedPlaylist = it },
                    onDelete = { showDeleteConfirm = it },
                    modifier = Modifier.weight(1f)
                )
            } else {
                PlaylistDetailView(
                    playlist = selectedPlaylist!!,
                    availableTracks = viewModel.availableTracks,
                    viewModel = viewModel,
                    onPlaylistUpdated = { updated ->
                        selectedPlaylist = updated
                        viewModel.reloadPlaylists()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Folder dialog
    if (showFolderDialog) {
        var folderText by remember { mutableStateOf(viewModel.folderPath) }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Set Music Folder") },
            text = {
                OutlinedTextField(
                    value = folderText,
                    onValueChange = { folderText = it },
                    label = { Text("Folder path") },
                    singleLine = true,
                    placeholder = { Text(DEFAULT_FOLDER_PATH) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setFolder(folderText, context)
                        showFolderDialog = false
                    },
                    enabled = folderText.isNotBlank()
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                val fileName = generatePlaylistFileName(name)
                val newPlaylist = PlaylistData(
                    name = name,
                    fileName = fileName,
                    entries = emptyList()
                )
                savePlaylist(viewModel.folderPath, newPlaylist)
                viewModel.reloadPlaylists()
                selectedPlaylist = newPlaylist
                showCreateDialog = false
            }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Playlist") },
            text = { Text("Delete \"${playlist.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deletePlaylist(viewModel.folderPath, playlist)
                    viewModel.reloadPlaylists()
                    showDeleteConfirm = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PlaylistListView(
    playlists: List<PlaylistData>,
    folderPath: String,
    onSelect: (PlaylistData) -> Unit,
    onDelete: (PlaylistData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                if (folderPath.isEmpty()) {
                    Text("No folder selected.", fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap the folder icon above to set your music folder.",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("No playlists yet.", fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + to create a playlist.",
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        LazyColumn(modifier = modifier) {
            itemsIndexed(playlists) { _, playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.entries.size} tracks") },
                    trailingContent = {
                        IconButton(onClick = { onDelete(playlist) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    modifier = Modifier.clickable { onSelect(playlist) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistData,
    availableTracks: List<TrackData>,
    viewModel: PlayerViewModel,
    onPlaylistUpdated: (PlaylistData) -> Unit,
    modifier: Modifier = Modifier
) {
    var entries by remember(playlist.fileName) {
        mutableStateOf(playlist.entries.toList())
    }
    var showAddTrackDialog by remember { mutableStateOf(false) }

    val resolvedEntries = remember(entries, availableTracks) {
        entries.map { fileName ->
            availableTracks.find { it.jsonFileName == fileName }
        }
    }

    fun saveCurrentState() {
        val updated = playlist.copy(entries = entries)
        savePlaylist(viewModel.folderPath, updated)
        onPlaylistUpdated(updated)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { showAddTrackDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Track")
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Empty playlist. Tap Add Track to get started.")
            }
        } else {
            val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val reorderableLazyListState =
                rememberReorderableLazyListState(lazyListState) { from, to ->
                    entries = entries.toMutableList().apply {
                        add(to.index, removeAt(from.index))
                    }
                    val updated = playlist.copy(entries = entries)
                    savePlaylist(viewModel.folderPath, updated)
                    onPlaylistUpdated(updated)
                }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(entries, key = { index, item -> "$index-$item" }) { index, entry ->
                    ReorderableItem(
                        reorderableLazyListState,
                        key = "$index-$entry"
                    ) { isDragging ->
                        val track = resolvedEntries.getOrNull(index)
                        val elevation = if (isDragging) 8.dp else 0.dp

                        Surface(tonalElevation = elevation) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = track?.name ?: "Unknown: $entry",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    if (track != null) {
                                        Text(
                                            "${track.intro} · ${track.repetitionCount} reps",
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                leadingContent = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle()
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Reorder"
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        entries =
                                            entries.toMutableList().apply { removeAt(index) }
                                        saveCurrentState()
                                    }) {
                                        Icon(
                                            Icons.Default.RemoveCircleOutline,
                                            contentDescription = "Remove"
                                        )
                                    }
                                }
                            )
                        }

                        if (index < entries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddTrackDialog) {
        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false },
            title = { Text("Add Track") },
            text = {
                LazyColumn {
                    itemsIndexed(availableTracks) { _, track ->
                        ListItem(
                            headlineContent = { Text(track.name) },
                            supportingContent = {
                                Text(
                                    "${track.intro} · ${track.repetitionCount} reps",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.clickable {
                                entries = entries + track.jsonFileName
                                val updated = playlist.copy(entries = entries)
                                savePlaylist(viewModel.folderPath, updated)
                                onPlaylistUpdated(updated)
                                showAddTrackDialog = false
                            }
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
