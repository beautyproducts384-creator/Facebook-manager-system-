package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.data.model.DashboardData
import com.facebookpagemanager.app.data.model.FbPage
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.ErrorView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.RequirementCard
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.components.StatCard
import com.facebookpagemanager.app.ui.components.formatCount
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pages: List<FbPage> = emptyList(),
        val selectedPageId: String? = null,
        val dashboard: DashboardData? = null,
        val error: String? = null,
        val requirement: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, requirement = null) }
            val repo = container.currentRepository()
            when (val pagesResult = repo.getPages()) {
                is RepoResult.Err -> _state.update {
                    it.copy(loading = false, error = pagesResult.message, requirement = pagesResult.requirement)
                }
                is RepoResult.Ok -> {
                    val pages = pagesResult.value
                    var selected = container.modeManager.selectedPageId.first()
                    if (pages.none { it.id == selected }) selected = pages.firstOrNull()?.id
                    if (selected != null) container.modeManager.setSelectedPageId(selected)
                    var dash: DashboardData? = null
                    var dashError: String? = null
                    var dashReq: String? = null
                    if (selected != null) {
                        when (val d = repo.getDashboard(selected)) {
                            is RepoResult.Ok -> dash = d.value
                            is RepoResult.Err -> { dashError = d.message; dashReq = d.requirement }
                        }
                    }
                    _state.update {
                        it.copy(
                            loading = false, pages = pages, selectedPageId = selected,
                            dashboard = dash, error = dashError, requirement = dashReq
                        )
                    }
                }
            }
        }
    }

    fun selectPage(id: String) {
        viewModelScope.launch {
            container.modeManager.setSelectedPageId(id)
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val vm: DashboardViewModel = fpmViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (state.loading && state.dashboard == null) {
            LoadingView("Loading dashboard…")
            return@Column
        }
        if (state.error != null && state.dashboard == null && state.pages.isEmpty()) {
            ErrorView(state.error!!, state.requirement) { vm.refresh() }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            if (state.pages.size > 1) {
                item { PageSelector(state.pages, state.selectedPageId) { vm.selectPage(it) } }
            }

            val dash = state.dashboard
            if (state.error != null && dash == null) {
                item { ErrorView(state.error!!, state.requirement) { vm.refresh() } }
            }

            if (dash != null) {
                item {
                    Text(
                        dash.page.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    if (dash.limited && dash.limitedNote != null) {
                        RequirementCard(dash.limitedNote!!)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Followers", formatCount(dash.followers), Modifier.weight(1f), Icons.Filled.Group)
                            StatCard("Reach", formatCount(dash.reach), Modifier.weight(1f), Icons.Filled.TrendingUp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Engagement", formatCount(dash.engagement), Modifier.weight(1f), Icons.Filled.Favorite)
                            StatCard("Scheduled", dash.scheduledCount.toString(), Modifier.weight(1f), Icons.Filled.Schedule)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Published", dash.publishedCount.toString(), Modifier.weight(1f))
                            StatCard("Unread msgs", dash.unreadMessages.toString(), Modifier.weight(1f), Icons.Filled.Mail)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatCard("Recent comments", dash.recentCommentsCount.toString(), Modifier.weight(1f), Icons.Filled.Comment)
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item { SectionTitle("Top performing posts") }
                if (dash.topPosts.isEmpty()) {
                    item { EmptyView("No posts yet for this Page.") }
                } else {
                    items(dash.topPosts) { post ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    post.message?.take(140) ?: "(no text)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "♥ ${post.likes}   💬 ${post.comments}   ↗ ${post.shares}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            } else if (state.pages.isEmpty()) {
                item { EmptyView("No Pages found. Connect your Facebook account on the Pages tab, or stay in Demo Mode.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSelector(pages: List<FbPage>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by mutableStateOf(false)
    val selected = pages.find { it.id == selectedId } ?: pages.firstOrNull()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Page") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pages.forEach { page ->
                DropdownMenuItem(
                    text = { Text(page.name) },
                    onClick = { onSelect(page.id); expanded = false }
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}
