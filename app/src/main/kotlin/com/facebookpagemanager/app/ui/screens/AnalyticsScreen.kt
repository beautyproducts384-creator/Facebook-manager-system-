package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.data.model.AnalyticsData
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

class AnalyticsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pageName: String? = null,
        val data: AnalyticsData? = null,
        val error: String? = null,
        val requirement: String? = null,
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
            when (val r = repo.getAnalytics(page.id)) {
                is RepoResult.Ok -> _state.update {
                    it.copy(loading = false, pageName = page.name, data = r.value)
                }
                is RepoResult.Err -> _state.update {
                    it.copy(loading = false, error = r.message, requirement = r.requirement)
                }
            }
        }
    }
}

@Composable
fun AnalyticsScreen(navController: NavController) {
    val vm: AnalyticsViewModel = fpmViewModel { AnalyticsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> LoadingView("Loading analytics…")
        state.error != null -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (state.requirement != null) RequirementCard(state.requirement!!)
            ErrorView(state.error!!, null, vm::refresh)
        }
        state.data == null -> EmptyView("No analytics available.")
        else -> {
            val data = state.data!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Analytics for ${state.pageName ?: ""}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    if (data.limited && data.note != null) {
                        RequirementCard(data.note!!)
                        Spacer(Modifier.padding(4.dp))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("Followers", data.followers.toString(), Modifier.weight(1f))
                        MetricCard("Reach", data.reach.toString(), Modifier.weight(1f))
                        MetricCard("Engagement", data.engagement.toString(), Modifier.weight(1f))
                    }
                }
                if (data.impressionsByDay.isNotEmpty()) {
                    item {
                        SectionTitle("Impressions (last ${data.impressionsByDay.size} days)")
                        Card(modifier = Modifier.fillMaxWidth()) {
                            ImpressionBars(data.impressionsByDay, Modifier.padding(16.dp))
                        }
                    }
                }
                item { SectionTitle("Top posts") }
                if (data.topPosts.isEmpty()) {
                    item { EmptyView("No post stats yet.") }
                } else {
                    items(data.topPosts) { post ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    post.message?.take(120) ?: "(no text)",
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 2
                                )
                                Spacer(Modifier.padding(2.dp))
                                Text(
                                    "${TimeFmt.date(post.createdTime)} • ♥ ${post.likes} • 💬 ${post.comments} • ↗ ${post.shares} • Reach ${post.reach}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ImpressionBars(days: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { (label, value) ->
            val fraction = (value.toFloat() / max).coerceIn(0.04f, 1f)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(fraction, fill = true)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.padding(2.dp))
                Text(
                    label.take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
