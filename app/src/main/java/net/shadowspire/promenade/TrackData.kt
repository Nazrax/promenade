package net.shadowspire.promenade

import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class Repetition(
    val start: Double
)

data class TrackJson(
    val name: String,
    @SerializedName("music_file") val musicFile: String,
    @SerializedName("calls_file") val callsFile: String,
    val intro: String,
    val repetitions: List<Repetition>
)

data class TrackData(
    val name: String,
    val musicUri: Uri,
    val callsUri: Uri,
    val intro: String,
    val repetitions: List<Repetition>
) {
    val repetitionCount: Int get() = repetitions.size
}

fun loadTracksFromFolder(folderPath: String): List<TrackData> {
    val folder = File(folderPath)
    if (!folder.isDirectory) return emptyList()

    val tracks = mutableListOf<TrackData>()

    for (file in folder.listFiles().orEmpty()) {
        if (file.isFile && file.name.endsWith(".json", ignoreCase = true)) {
            try {
                val track = parseTrackJson(file)
                tracks.add(track)
            } catch (e: Exception) {
                android.util.Log.w("TrackData", "Skipping ${file.name}: ${e.message}")
            }
        }
    }

    return tracks.sortedBy { it.name.lowercase() }
}

private fun parseTrackJson(jsonFile: File): TrackData {
    val jsonText = jsonFile.readText()
    val trackJson = Gson().fromJson(jsonText, TrackJson::class.java)
    val folder = jsonFile.parentFile!!

    val musicFile = File(folder, trackJson.musicFile)
    if (!musicFile.exists()) {
        throw IllegalArgumentException("Music file '${trackJson.musicFile}' not found")
    }

    val callsFile = File(folder, trackJson.callsFile)
    if (!callsFile.exists()) {
        throw IllegalArgumentException("Calls file '${trackJson.callsFile}' not found")
    }

    return TrackData(
        name = trackJson.name,
        musicUri = Uri.fromFile(musicFile),
        callsUri = Uri.fromFile(callsFile),
        intro = trackJson.intro,
        repetitions = trackJson.repetitions
    )
}
