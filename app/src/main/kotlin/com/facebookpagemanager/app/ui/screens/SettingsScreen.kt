package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.BuildConfig
import com.facebookpagemanager.app.data.model.SavedReply
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.nav.Routes
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(val container: AppContainer) : ViewModel() {

    data class UiState(
        val demoMode: Boolean = true,
        val theme: String = "system",
        val fbName: String? = null,
        val confirmRealMode: Boolean = false,
        val savedReplies: List<SavedReply> = emptyList(),
        val replyDialogOpen: Boolean = false,
        val replyTitle: String = "",
        val replyText: String = "",
        val replyDeleteTarget: SavedReply? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.modeManager.demoMode,
                container.modeManager.theme,
                container.modeManager.connectedFbName
            ) { demo, theme, fbName -> Triple(demo, theme, fbName) }
                .collect { (demo, theme, fbName) ->
                    _state.update { it.copy(demoMode = demo, theme = theme, fbName = fbName) }
                }
        }
        refreshReplies()
    }

    fun refreshReplies() {
        viewModelScope.launch {
            val replies = container.currentRepository().getSavedReplies()
            _state.update { it.copy(savedReplies = replies) }
        }
    }

    fun openReplyDialog() = _state.update { it.copy(replyDialogOpen = true, replyTitle = "", replyText = "") }
    fun closeReplyDialog() = _state.update { it.copy(replyDialogOpen = false) }
    fun setReplyTitle(v: String) = _state.update { it.copy(replyTitle = v) }
    fun setReplyText(v: String) = _state.update { it.copy(replyText = v) }

    fun saveReply() {
        val s = _state.value
        if (s.replyTitle.isBlank() || s.replyText.isBlank()) return
        viewModelScope.launch {
            container.currentRepository().saveSavedReply(
                SavedReply(title = s.replyTitle.trim(), text = s.replyText.trim())
            )
            _state.update { it.copy(replyDialogOpen = false) }
            refreshReplies()
        }
    }

    fun confirmDeleteReply(r: SavedReply) = _state.update { it.copy(replyDeleteTarget = r) }
    fun cancelDeleteReply() = _state.update { it.copy(replyDeleteTarget = null) }

    fun deleteReplyConfirmed() {
        val r = _state.value.replyDeleteTarget ?: return
        viewModelScope.launch {
            container.currentRepository().deleteSavedReply(r)
            _state.update { it.copy(replyDeleteTarget = null) }
            refreshReplies()
        }
    }

    fun askEnableRealMode() = _state.update { it.copy(confirmRealMode = true) }
    fun cancelRealMode() = _state.update { it.copy(confirmRealMode = false) }

    /** Switch to demo (safe, immediate) or confirm the switch to real mode. */
    fun setDemoMode(demo: Boolean) {
        if (!demo) {
            askEnableRealMode()
            return
        }
        viewModelScope.launch {
            container.modeManager.setDemoMode(true)
            _state.update { it.copy(confirmRealMode = false) }
        }
    }

    fun confirmEnableRealMode() {
        viewModelScope.launch {
            container.modeManager.setDemoMode(false)
            _state.update { it.copy(confirmRealMode = false) }
        }
    }

    fun setTheme(choice: String) {
        viewModelScope.launch { container.modeManager.setTheme(choice) }
    }

    fun disconnectFacebook() {
        viewModelScope.launch {
            container.authManager.logout()
            container.modeManager.setConnectedFbName(null)
            container.modeManager.setSelectedPageId(null)
        }
    }
}

@Composable
fun SettingsScreen(navController: NavController) {
    val vm: SettingsViewModel = fpmViewModel { SettingsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDisconnect by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("Mode")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Demo Mode", fontWeight = FontWeight.Bold)
                            Text(
                                if (state.demoMode)
                                    "ON — the app uses built-in sample data. Nothing touches Facebook."
                                else
                                    "OFF — the app uses the official Meta Graph API with your login.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = state.demoMode,
                            onCheckedChange = vm::setDemoMode
                        )
                    }
                    if (!state.demoMode) {
                        Text(
                            "Real mode talks to Meta's servers. Every action that needs a permission " +
                                "your app hasn't been granted will fail with a clear explanation " +
                                "of what's missing and how App Review works — nothing is faked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Appearance")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = state.theme == value,
                                onClick = { vm.setTheme(value) }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Facebook connection")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.fbName != null) {
                        Text("Connected as ${state.fbName}", fontWeight = FontWeight.Bold)
                        Text(
                            "The app only ever uses the official Facebook Login flow. " +
                                "It never asks for or stores your Facebook password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedButton(onClick = { confirmDisconnect = true }) {
                            Text("Disconnect Facebook")
                        }
                    } else {
                        Text("Not connected.", fontWeight = FontWeight.Bold)
                        Text(
                            "Connect with the official Facebook Login to manage real Pages. " +
                                "Requires a Meta Developer app id (see Setup in the README).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Button(onClick = { navController.navigate(Routes.PAGES) }) {
                            Text("Go to Pages to connect")
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Saved replies")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Quick replies you can insert while answering messages in the Inbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    state.savedReplies.forEach { reply ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(reply.title, fontWeight = FontWeight.Bold)
                                Text(
                                    reply.text, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary, maxLines = 2
                                )
                            }
                            TextButton(onClick = { vm.confirmDeleteReply(reply) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    OutlinedButton(onClick = vm::openReplyDialog, modifier = Modifier.fillMaxWidth()) {
                        Text("Add saved reply")
                    }
                }
            }
        }

        item {
            SectionTitle("Notifications")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "The app sends a notification when a scheduled post publishes " +
                            "successfully — or fails, with the reason. Make sure notifications " +
                            "are allowed for this app in Android settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        item {
            SectionTitle("About")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Facebook Page Manager v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                    Text(
                        "Manage Facebook Pages: publish, schedule, inbox, comments, " +
                            "media, analytics. Demo Mode is the default so you can explore " +
                            "every screen safely; Real Mode uses only official Meta APIs " +
                            "and explains every permission it needs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Not affiliated with or endorsed by Meta Platforms, Inc.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }

    if (state.confirmRealMode) {
        AlertDialog(
            onDismissRequest = vm::cancelRealMode,
            title = { Text("Switch to Real Mode?") },
            text = {
                Text(
                    "Real Mode connects to Meta's Graph API. You'll need to log in with " +
                        "Facebook on the Pages tab, and the app needs a valid Meta app id " +
                        "configured at build time. Features your app hasn't been approved " +
                        "for will show an honest explanation instead of failing silently."
                )
            },
            confirmButton = { TextButton(onClick = vm::confirmEnableRealMode) { Text("Switch to Real Mode") } },
            dismissButton = { TextButton(onClick = vm::cancelRealMode) { Text("Stay in Demo") } }
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Facebook?") },
            text = { Text("Your login session and tokens will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { vm.disconnectFacebook(); confirmDisconnect = false }) {
                    Text("Disconnect")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } }
        )
    }

    if (state.replyDialogOpen) {
        AlertDialog(
            onDismissRequest = vm::closeReplyDialog,
            title = { Text("New saved reply") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.replyTitle, onValueChange = vm::setReplyTitle,
                        label = { Text("Title (e.g. Opening hours)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = state.replyText, onValueChange = vm::setReplyText,
                        label = { Text("Reply text") },
                        modifier = Modifier.fillMaxWidth(), minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::saveReply,
                    enabled = state.replyTitle.isNotBlank() && state.replyText.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = vm::closeReplyDialog) { Text("Cancel") } }
        )
    }

    state.replyDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = vm::cancelDeleteReply,
            title = { Text("Delete saved reply?") },
            text = { Text("\"${target.title}\" will be removed.") },
            confirmButton = { TextButton(onClick = vm::deleteReplyConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDeleteReply) { Text("Keep") } }
        )
    }
}
