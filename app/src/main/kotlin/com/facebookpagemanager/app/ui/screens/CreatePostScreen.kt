package com.facebookpagemanager.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.facebookpagemanager.app.data.model.Draft
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.ErrorView
import com.facebookpagemanager.app.ui.components.RequirementCard
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.nav.Routes
import com.facebookpagemanager.app.ui.util.fpmViewModel
import com.facebookpagemanager.app.util.MediaUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId

class CreatePostViewModel(val container: AppContainer) : ViewModel() {

    data class UiState(
        val pageId: String? = null,
        val pageName: String? = null,
        val message: String = "",
        val link: String = "",
        val postType: String = "text",
        val mediaPath: String? = null,
        val mediaType: String? = null,
        val busy: Boolean = false,
        val result: String? = null,
        val error: String? = null,
        val requirement: String? = null,
        val drafts: List<Draft> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val repo = container.currentRepository()
            val pages = (repo.getPages() as? RepoResult.Ok)?.value ?: emptyList()
            val selectedId = container.modeManager.selectedPageId.first()
            val page = pages.find { it.id == selectedId } ?: pages.firstOrNull()
            // Consume handoff from Templates / Media Library screens.
            val pendingText = container.pendingComposerText
            val pendingMedia = container.pendingComposerMediaUri
            container.pendingComposerText = null
            container.pendingComposerMediaUri = null
            _state.update {
                it.copy(
                    pageId = page?.id, pageName = page?.name,
                    message = pendingText ?: it.message,
                    mediaPath = pendingMedia ?: it.mediaPath,
                    mediaType = if (pendingMedia != null) "image" else it.mediaType,
                    drafts = repo.getDrafts()
                )
            }
        }
    }

    fun setMessage(v: String) = _state.update { it.copy(message = v, result = null, error = null) }
    fun setLink(v: String) = _state.update { it.copy(link = v, result = null, error = null) }
    fun setPostType(v: String) = _state.update { it.copy(postType = v) }
    fun attachMedia(path: String, type: String) =
        _state.update { it.copy(mediaPath = path, mediaType = type, result = null, error = null) }
    fun clearMedia() = _state.update { it.copy(mediaPath = null, mediaType = null) }
    fun clearFeedback() = _state.update { it.copy(result = null, error = null, requirement = null) }

    fun publishNow() {
        val s = _state.value
        val pageId = s.pageId ?: return
        if (s.message.isBlank() && s.link.isBlank() && s.mediaPath == null) {
            _state.update { it.copy(error = "Write something first — the post is empty.") }
            return
        }
        if (s.postType in setOf("image", "video", "reel") && s.mediaPath == null) {
            _state.update { it.copy(error = "Attach ${if (s.postType == "image") "an image" else "a video"} for a ${s.postType} post.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, result = null, error = null, requirement = null) }
            val repo = container.currentRepository()
            val result: RepoResult<String> = when (s.postType) {
                "image" -> repo.createImagePost(pageId, s.message, File(s.mediaPath!!))
                "video" -> repo.createVideoPost(pageId, s.message, File(s.mediaPath!!))
                "reel" -> repo.createReel(pageId, s.message, File(s.mediaPath!!))
                else -> repo.createTextPost(pageId, s.message, s.link.ifBlank { null })
            }
            when (result) {
                is RepoResult.Ok -> _state.update {
                    it.copy(busy = false, result = "Published! Post id: ${result.value}", message = "", link = "",
                        mediaPath = null, mediaType = null)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(busy = false, error = result.message, requirement = result.requirement)
                }
            }
        }
    }

    fun schedule(atEpochSec: Long) {
        val s = _state.value
        val pageId = s.pageId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, result = null, error = null, requirement = null) }
            val repo = container.currentRepository()
            when (val r = repo.schedulePost(pageId, s.message.ifBlank { null }, s.link.ifBlank { null },
                s.mediaPath, s.postType, atEpochSec)) {
                is RepoResult.Ok -> _state.update {
                    it.copy(busy = false,
                        result = "Scheduled for ${formatEpoch(atEpochSec)}. You'll get a notification when it publishes.",
                        message = "", link = "", mediaPath = null, mediaType = null)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(busy = false, error = r.message, requirement = r.requirement)
                }
            }
        }
    }

    fun saveDraft() {
        val s = _state.value
        if (s.message.isBlank() && s.link.isBlank() && s.mediaPath == null) {
            _state.update { it.copy(error = "Nothing to save — the draft is empty.") }
            return
        }
        viewModelScope.launch {
            val repo = container.currentRepository()
            repo.saveDraft(Draft(message = s.message, link = s.link.ifBlank { null },
                mediaUri = s.mediaPath, postType = s.postType))
            _state.update { it.copy(result = "Draft saved.", drafts = repo.getDrafts()) }
        }
    }

    fun loadDraft(d: Draft) = _state.update {
        it.copy(message = d.message, link = d.link ?: "", postType = d.postType,
            mediaPath = d.mediaUri, mediaType = if (d.mediaUri != null) "image" else null,
            result = null, error = null)
    }

    fun deleteDraft(d: Draft) {
        viewModelScope.launch {
            val repo = container.currentRepository()
            repo.deleteDraft(d)
            _state.update { it.copy(drafts = repo.getDrafts()) }
        }
    }
}

private fun formatEpoch(epochSec: Long): String {
    val zdt = Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault())
    return "%04d-%02d-%02d %02d:%02d".format(zdt.year, zdt.monthValue, zdt.dayOfMonth, zdt.hour, zdt.minute)
}

@Composable
fun CreatePostScreen(navController: NavController) {
    val vm: CreatePostViewModel = fpmViewModel { CreatePostViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showScheduler by remember { mutableStateOf(false) }
    var showDrafts by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val type = MediaUtils.uriType(context, uri)
            val file = MediaUtils.copyUriToAppFile(context, uri, "post")
            if (file != null) {
                vm.attachMedia(file.absolutePath, type)
                vm.setPostType(if (type == "video") "video" else "image")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Posting as: ${state.pageName ?: "—"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        item {
            OutlinedTextField(
                value = state.message,
                onValueChange = vm::setMessage,
                label = { Text("What's on your mind?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
        }
        item {
            OutlinedTextField(
                value = state.link,
                onValueChange = vm::setLink,
                label = { Text("Link (optional, https://…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            SectionTitle("Post type")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("text", "image", "video", "reel", "link").forEach { t ->
                    FilterChip(
                        selected = state.postType == t,
                        onClick = { vm.setPostType(t) },
                        label = { Text(t.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            if (state.postType == "reel") {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    "Reels via the API have limited availability — Meta enables them per app. " +
                        "If publishing fails, the error will say so honestly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        item {
            if (state.mediaPath != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = state.mediaPath,
                            contentDescription = "Attached media",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (state.mediaType == "video") "Video" else "Image") + " attached",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = vm::clearMedia) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove media")
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        mediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AttachFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Attach image / video")
                }
            }
        }
        item {
            if (state.busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::publishNow, modifier = Modifier.fillMaxWidth()) {
                        Text("Publish now")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showScheduler = true }, modifier = Modifier.weight(1f)) {
                            Text("Schedule…")
                        }
                        OutlinedButton(onClick = vm::saveDraft, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Drafts, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save draft")
                        }
                    }
                }
            }
        }
        if (state.result != null) {
            item {
                Card { Text(state.result!!, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (state.error != null) {
            item { ErrorView(state.error!!, state.requirement) { vm.clearFeedback() } }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Drafts (${state.drafts.size})")
                TextButton(onClick = { showDrafts = !showDrafts }) {
                    Text(if (showDrafts) "Hide" else "Show")
                }
            }
        }
        if (showDrafts) {
            if (state.drafts.isEmpty()) {
                item { Text("No drafts yet.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(state.drafts) { draft ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                draft.message.take(80).ifBlank { "(media draft)" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                            TextButton(onClick = { vm.loadDraft(draft) }) { Text("Load") }
                            IconButton(onClick = { vm.deleteDraft(draft) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete draft")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            TextButton(onClick = { navController.navigate(Routes.MEDIA) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Media Library")
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showScheduler) {
        ScheduleDialog(
            onDismiss = { showScheduler = false },
            onConfirm = { epochSec ->
                showScheduler = false
                vm.schedule(epochSec)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var step by remember { mutableStateOf(0) }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 3_600_000
    )
    val now = remember {
        java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) to it.get(java.util.Calendar.MINUTE) }
    }
    val timeState = rememberTimePickerState(initialHour = (now.first + 1) % 24, initialMinute = now.second, is24Hour = true)

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { if (dateState.selectedDateMillis != null) step = 1 }) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(dateState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a time") },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = dateState.selectedDateMillis ?: return@TextButton
                    val localDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    val epochSec = localDate.atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toEpochSecond()
                    onConfirm(epochSec)
                }) { Text("Schedule") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { step = 0 }) { Text("Back") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    }
}
