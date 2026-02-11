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
                }
            )
        }
        composable("playlist_editor") {
            PlaylistEditorScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
