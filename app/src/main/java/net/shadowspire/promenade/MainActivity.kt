package net.shadowspire.promenade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.shadowspire.promenade.ui.theme.PromenadeTheme
import java.net.URLDecoder
import java.net.URLEncoder

import net.shadowspire.promenade.ui.player.InstructionsScreen
import net.shadowspire.promenade.ui.player.PlayerScreen
import net.shadowspire.promenade.ui.player.PlaylistEditorScreen

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PromenadeTheme {
                PromenadeApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PromenadeApp(viewModel: PlayerViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "player") {
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onNavigateToPlaylistEditor = {
                    navController.navigate("playlist_editor")
                },
                onNavigateToInstructions = { trackName, path ->
                    val encodedName = URLEncoder.encode(trackName, "UTF-8")
                    val encodedPath = URLEncoder.encode(path, "UTF-8")
                    navController.navigate("instructions/$encodedName/$encodedPath")
                }
            )
        }
        composable("playlist_editor") {
            PlaylistEditorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("instructions/{trackName}/{path}") { backStackEntry ->
            val trackName = URLDecoder.decode(
                backStackEntry.arguments?.getString("trackName") ?: "", "UTF-8"
            )
            val path = URLDecoder.decode(
                backStackEntry.arguments?.getString("path") ?: "", "UTF-8"
            )
            InstructionsScreen(
                trackName = trackName,
                filePath = path,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}