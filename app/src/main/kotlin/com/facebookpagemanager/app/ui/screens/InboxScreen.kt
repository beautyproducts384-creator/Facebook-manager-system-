package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.facebookpagemanager.app.data.model.Conversation
import com.facebookpagemanager.app.data.model.ChatMessage
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.ErrorView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.RequirementCard
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.nav.Routes
import com.facebookpagemanager.app.ui.util.TimeFmt
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------- Inbox list ----------------

class InboxViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val conversations: List<Conversation> = emptyList(),
        val error: String? = null,
        val requirement: String? = null,
        val pageName: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, requirement = null) }
            val repo = container.currentRepository()
            val pages = (repo.getPages() as? RepoResult.Ok)?.value ?: emptyList()
            val selectedId = container.modeManager.selectedPageId.first()
            val page = pages.find { it.id == selectedId } ?: pages.firstOrNull()
            if (page == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            when (val r = repo.getConversations(page.id)) {
                is RepoResult.Ok -> _state.update {
                    it.copy(loading = false, conversations = r.value, pageName = page.name)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(loading = false, error = r.message, requirement = r.requirement)
                }
            }
        }
    }
}

@Composable
fun InboxScreen(navController: NavController) {
    val vm: InboxViewModel = fpmViewModel { InboxViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> LoadingView("Loading conversations…")
        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (state.requirement != null) {
                RequirementCard(state.requirement!!)
                Spacer(Modifier.padding(4.dp))
            }
            ErrorView(state.error!!, null, vm::refresh)
        }
        state.conversations.isEmpty() -> EmptyView("No conversations yet.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Inbox for ${state.pageName ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            items(state.conversations) { convo ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { navController.navigate(Routes.inboxDetail(convo.id)) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(convo.participantName ?: "Unknown", fontWeight = FontWeight.Bold)
                            Text(
                                convo.snippet ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                TimeFmt.timeAgo(convo.updatedTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (convo.unreadCount > 0) {
                                androidx.compose.material3.Badge { Text(convo.unreadCount.toString()) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.padding(8.dp)) }
        }
    }
}

// ---------------- Conversation detail ----------------

class InboxDetailViewModel(val container: AppContainer, private val conversationId: String) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pageId: String? = null,
        val messages: List<ChatMessage> = emptyList(),
        val draft: String = "",
        val sending: Boolean = false,
        val error: String? = null,
        val requirement: String? = null,
        val savedReplies: List<com.facebookpagemanager.app.data.model.SavedReply> = emptyList(),
        val title: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, requirement = null) }
            val repo = container.currentRepository()
            val pages = (repo.getPages() as? RepoResult.Ok)?.value ?: emptyList()
            val selectedId = container.modeManager.selectedPageId.first()
            val page = pages.find { it.id == selectedId } ?: pages.firstOrNull()
            if (page == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val title = (repo.getConversations(page.id) as? RepoResult.Ok)?.value
                ?.find { it.id == conversationId }?.participantName ?: "Conversation"
            val replies = repo.getSavedReplies()
            when (val r = repo.getMessages(page.id, conversationId)) {
                is RepoResult.Ok -> _state.update {
                    it.copy(loading = false, pageId = page.id, messages = r.value,
                        savedReplies = replies, title = title)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(loading = false, error = r.message, requirement = r.requirement,
                        savedReplies = replies, title = title)
                }
            }
        }
    }

    fun setDraft(v: String) = _state.update { it.copy(draft = v) }

    fun insertReply(text: String) {
        _state.update { it.copy(draft = (it.draft + " " + text).trim()) }
    }

    fun send() {
        val s = _state.value
        val pageId = s.pageId ?: return
        if (s.draft.isBlank() || s.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null, requirement = null) }
            val repo = container.currentRepository()
            when (val r = repo.sendMessage(pageId, conversationId, s.draft)) {
                is RepoResult.Ok -> _state.update { it.copy(sending = false, draft = "") }
                is RepoResult.Err -> _state.update {
                    it.copy(sending = false, error = r.message, requirement = r.requirement)
                }
            }
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxDetailScreen(navController: NavController, conversationId: String) {
    val vm: InboxDetailViewModel = fpmViewModel { app -> InboxDetailViewModel(app, conversationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingView("Loading messages…")
            state.error != null && state.messages.isEmpty() -> Column(Modifier.padding(16.dp)) {
                if (state.requirement != null) RequirementCard(state.requirement!!)
                ErrorView(state.error!!, null, vm::refresh)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(state.messages) { _, msg ->
                        MessageBubble(msg)
                    }
                }
                if (state.savedReplies.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.savedReplies) { reply ->
                            AssistChip(
                                onClick = { vm.insertReply(reply.text) },
                                label = { Text(reply.title) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = vm::setDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a reply…") },
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = vm::send, enabled = !state.sending && state.draft.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
                if (state.error != null) {
                    Text(
                        state.error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val fromPage = msg.fromPage
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromPage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = if (fromPage) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(msg.text ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(
                    TimeFmt.timeAgo(msg.createdTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
