package net.shadowspire.promenade

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class PlaylistJson(
    val name: String,
    val entries: List<String>  // list of track JSON filenames
)

data class PlaylistData(
    val name: String,
    val fileName: String,
    val entries: List<String>  // track JSON filenames, may contain duplicates
)

fun loadPlaylistsFromFolder(folderPath: String): List<PlaylistData> {
    val folder = File(folderPath)
    if (!folder.isDirectory) return emptyList()

    val playlists = mutableListOf<PlaylistData>()

    for (file in folder.listFiles().orEmpty()) {
        if (file.isFile && file.name.startsWith("playlist_", ignoreCase = true)
            && file.name.endsWith(".json", ignoreCase = true)
        ) {
            try {
                val jsonText = file.readText()
                val playlistJson = Gson().fromJson(jsonText, PlaylistJson::class.java)
                playlists.add(
                    PlaylistData(
                        name = playlistJson.name,
                        fileName = file.name,
                        entries = playlistJson.entries
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w("PlaylistData", "Skipping ${file.name}: ${e.message}")
            }
        }
    }

    return playlists.sortedBy { it.name.lowercase() }
}

fun savePlaylist(folderPath: String, playlist: PlaylistData) {
    val playlistJson = PlaylistJson(
        name = playlist.name,
        entries = playlist.entries
    )
    val gson = GsonBuilder().setPrettyPrinting().create()
    val json = gson.toJson(playlistJson)
    File(folderPath, playlist.fileName).writeText(json)
}

fun deletePlaylist(folderPath: String, playlist: PlaylistData) {
    File(folderPath, playlist.fileName).delete()
}

fun generatePlaylistFileName(name: String): String {
    val sanitized = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
        .replace(" ", "_")
        .lowercase()
    return "playlist_${sanitized}_${System.currentTimeMillis()}.json"
}
