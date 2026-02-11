package net.shadowspire.promenade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.shadowspire.promenade.ui.player.PlayerScreen
import net.shadowspire.promenade.ui.theme.PromenadeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PromenadeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // viewModel() creates the PlayerViewModel once and
                    // retrieves the same instance on recomposition/rotation
                    val playerViewModel: PlayerViewModel = viewModel()
                    PlayerScreen(
                        viewModel = playerViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
