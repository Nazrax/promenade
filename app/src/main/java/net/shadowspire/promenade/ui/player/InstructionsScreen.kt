package net.shadowspire.promenade.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    trackName: String,
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val fileContent = remember(filePath) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    var showError by remember { mutableStateOf(fileContent == null) }

    if (showError) {
        LaunchedEffect(Unit) {
            showError = false
        }
        AlertDialog(
            onDismissRequest = { onNavigateBack() },
            title = { Text("Error") },
            text = { Text("Instructions file not found.") },
            confirmButton = {
                TextButton(onClick = { onNavigateBack() }) {
                    Text("OK")
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trackName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = fileContent ?: "",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}