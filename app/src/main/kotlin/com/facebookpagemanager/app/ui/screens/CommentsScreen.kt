package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.facebookpagemanager.app.data.model.PageComment
import com.facebookpagemanager.app.data.model.Post
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.ErrorView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.RequirementCard
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.util.TimeFmt
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommentsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pageId: String? = null,
        val posts: List<Post> = emptyList(),
        val selectedPost: Post? = null,
        val comments: List<PageComment> = emptyList(),
        val commentsLoading: Boolean = false,
        val error: String? = null,
        val requirement: String? = null,
        val replyTarget: PageComment? = null,
        val actionMessage: String? = null,
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
            val posts = (repo.getPosts(page.id, 50) as? RepoResult.Ok)?.value ?: emptyList()
            val selected = posts.firstOrNull()
            _state.update { it.copy(loading = false, pageId = page.id, posts = posts, selectedPost = selected) }
            if (selected != null) loadComments(page.id, selected.id)
        }
    }

    fun selectPost(post: Post) {
        _state.update { it.copy(selectedPost = post, actionMessage = null) }
        viewModelScope.launch {
            val pageId = _state.value.pageId ?: return@launch
            loadComments(pageId, post.id)
        }
    }

    private suspend fun loadComments(pageId: String, postId: String) {
        _state.update { it.copy(commentsLoading = true, error = null, requirement = null) }
        val repo = container.currentRepository()
        when (val r = repo.getComments(pageId, postId)) {
            is RepoResult.Ok -> _state.update { it.copy(commentsLoading = false, comments = r.value) }
            is RepoResult.Err -> _state.update {
                it.copy(commentsLoading = false, error = r.message, requirement = r.requirement)
            }
        }
    }

    fun openReply(c: PageComment) = _state.update { it.copy(replyTarget = c) }
    fun closeReply() = _state.update { it.copy(replyTarget = null) }

    fun sendReply(text: String) {
        val target = _state.value.replyTarget ?: return
        val pageId = _state.value.pageId ?: return
        viewModelScope.launch {
            val repo = container.currentRepository()
            when (val r = repo.replyToComment(pageId, target.id, text)) {
                is RepoResult.Ok -> {
                    _state.update { it.copy(replyTarget = null, actionMessage = "Reply posted.") }
                    loadComments(pageId, _state.value.selectedPost?.id ?: return@launch)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(actionMessage = "Failed: ${r.message}")
                }
            }
        }
    }

    fun hideComment(c: PageComment) {
        val pageId = _state.value.pageId ?: return
        viewModelScope.launch {
            val repo = container.currentRepository()
            when (val r = repo.hideComment(pageId, c.id, true)) {
                is RepoResult.Ok -> {
                    _state.update { it.copy(actionMessage = "Comment hidden.") }
                    loadComments(pageId, _state.value.selectedPost?.id ?: return@launch)
                }
                is RepoResult.Err -> _state.update { it.copy(actionMessage = "Failed: ${r.message}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(navController: NavController) {
    val vm: CommentsViewModel = fpmViewModel { CommentsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var postMenuOpen by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }

    if (state.loading) {
        LoadingView("Loading posts…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionTitle("Comments")
            Text(
                "Reply to fans, or hide spam — right from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.padding(4.dp))
            if (state.posts.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = postMenuOpen,
                    onExpandedChange = { postMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = state.selectedPost?.message?.take(60) ?: "Select a post",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Post") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(postMenuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = postMenuOpen,
                        onDismissRequest = { postMenuOpen = false }
                    ) {
                        state.posts.forEach { post ->
                            DropdownMenuItem(
                                text = { Text(post.message?.take(60) ?: "(no text)", maxLines = 1) },
                                onClick = { vm.selectPost(post); postMenuOpen = false }
                            )
                        }
                    }
                }
            }
        }
        if (state.requirement != null) {
            item { RequirementCard(state.requirement!!) }
        }
        if (state.error != null) {
            item { ErrorView(state.error!!, null) { vm.refresh() } }
        } else if (state.commentsLoading) {
            item { LoadingView("Loading comments…") }
        } else if (state.comments.isEmpty()) {
            item { EmptyView("No comments on this post yet.") }
        } else {
            items(state.comments) { comment ->
                CommentRow(
                    comment = comment,
                    onReply = { vm.openReply(comment); replyText = "" },
                    onHide = { vm.hideComment(comment) }
                )
            }
        }
        if (state.actionMessage != null) {
            item {
                Card { Text(state.actionMessage!!, Modifier.padding(12.dp)) }
            }
        }
        item { Spacer(Modifier.padding(8.dp)) }
    }

    state.replyTarget?.let { target ->
        AlertDialog(
            onDismissRequest = vm::closeReply,
            title = { Text("Reply to ${target.fromName}") },
            text = {
                Column {
                    Text(
                        "\"" + (target.text?.take(100) ?: "") + "\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Your reply") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.sendReply(replyText) },
                    enabled = replyText.isNotBlank()
                ) { Text("Send reply") }
            },
            dismissButton = { TextButton(onClick = vm::closeReply) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CommentRow(
    comment: PageComment,
    onReply: () -> Unit,
    onHide: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.fromName ?: "Unknown", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    TimeFmt.timeAgo(comment.createdTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.padding(2.dp))
            Text(comment.text ?: "", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onReply) { Text("Reply") }
                TextButton(onClick = onHide) {
                    Text("Hide", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "♥ ${comment.likeCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}
