package com.facebookpagemanager.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.facebookpagemanager.app.data.model.MediaItem
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.nav.Routes
import com.facebookpagemanager.app.ui.util.TimeFmt
import com.facebookpagemanager.app.ui.util.fpmViewModel
import com.facebookpagemanager.app.util.MediaUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaLibraryViewModel(val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val items: List<MediaItem> = emptyList(),
        val deleteTarget: MediaItem? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = container.currentRepository().getMedia()
            _state.update { it.copy(loading = false, items = items) }
        }
    }

    fun confirmDelete(item: MediaItem) = _state.update { it.copy(deleteTarget = item) }
    fun cancelDelete() = _state.update { it.copy(deleteTarget = null) }

    fun deleteConfirmed() {
        val item = _state.value.deleteTarget ?: return
        viewModelScope.launch {
            container.currentRepository().deleteMedia(item)
            _state.update { it.copy(deleteTarget = null) }
            refresh()
        }
    }

    /**
     * Copies a picked URI into app-private storage and registers it in the
     * media library. Returns the stored item, or null if the copy failed.
     */
    suspend fun addFromUri(context: Context, uri: Uri): MediaItem? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val type = MediaUtils.uriType(context, uri)
            val file = MediaUtils.copyUriToAppFile(context, uri, "media") ?: return@withContext null
            val repo = container.currentRepository()
            val item = MediaItem(
                uri = file.absolutePath,
                type = type,
                name = file.name,
                addedAt = System.currentTimeMillis() / 1000
            )
            val id = repo.addMedia(item)
            item.copy(id = id)
        }

    fun useInComposer(item: MediaItem) {
        container.pendingComposerMediaUri = item.uri
    }
}

@Composable
fun MediaLibraryScreen(navController: NavController) {
    val vm: MediaLibraryViewModel = fpmViewModel { MediaLibraryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var importing by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            // Copy into app-private storage on a background thread.
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                vm.addFromUri(context, uri)
                importing = false
                vm.refresh()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add media")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SectionTitle("Media Library")
            Text(
                "Images and videos you reuse across posts. Tap one to attach it to a new post.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.padding(4.dp))
            when {
                state.loading || importing -> LoadingView("Loading media…")
                state.items.isEmpty() -> EmptyView("No media yet. Tap + to add images or videos.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items) { item ->
                        MediaTile(
                            item = item,
                            onUse = {
                                vm.useInComposer(item)
                                navController.navigate(Routes.CREATE) {
                                    popUpTo(Routes.MEDIA) { inclusive = false }
                                }
                            },
                            onDelete = { vm.confirmDelete(item) }
                        )
                    }
                }
            }
        }
    }

    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete this media?") },
            text = { Text("It will be removed from your library. Posts that already used it are unaffected.") },
            confirmButton = { TextButton(onClick = vm::deleteConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Keep") } }
        )
    }
}

@Composable
private fun MediaTile(item: MediaItem, onUse: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onUse)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                if (item.type == "video") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle, null,
                            modifier = Modifier.fillMaxSize(0.5f),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.name.take(14),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onDelete, modifier = Modifier.padding(0.dp)) {
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
