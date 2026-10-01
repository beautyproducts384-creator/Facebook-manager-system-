package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.data.model.Post
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.data.model.ScheduledPost
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.util.TimeFmt
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pageId: String? = null,
        val pageName: String? = null,
        val month: YearMonth = YearMonth.now(),
        val scheduled: List<ScheduledPost> = emptyList(),
        val published: List<Post> = emptyList(),
        val selectedDay: LocalDate = LocalDate.now(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val repo = container.currentRepository()
            val pages = (repo.getPages() as? RepoResult.Ok)?.value ?: emptyList()
            val selectedId = container.modeManager.selectedPageId.first()
            val page = pages.find { it.id == selectedId } ?: pages.firstOrNull()
            if (page == null) {
                _state.update { it.copy(loading = false) }
                return@launch
            }
            val scheduled = (repo.getScheduledPosts(page.id) as? RepoResult.Ok)?.value ?: emptyList()
            val published = (repo.getPosts(page.id, 100) as? RepoResult.Ok)?.value ?: emptyList()
            _state.update {
                it.copy(loading = false, pageId = page.id, pageName = page.name,
                    scheduled = scheduled, published = published)
            }
        }
    }

    fun changeMonth(delta: Long) {
        _state.update { it.copy(month = it.month.plusMonths(delta)) }
    }

    fun selectDay(day: LocalDate) {
        _state.update { it.copy(selectedDay = day) }
    }

    fun cancelScheduled(post: ScheduledPost) {
        viewModelScope.launch {
            container.currentRepository().cancelScheduledPost(post)
            refresh()
        }
    }

    fun updateScheduled(post: ScheduledPost, newMessage: String?, newTime: Long?) {
        viewModelScope.launch {
            val updated = post.copy(
                message = newMessage ?: post.message,
                scheduledFor = newTime ?: post.scheduledFor
            )
            container.currentRepository().updateScheduledPost(updated)
            refresh()
        }
    }

    fun dayOfEpoch(epochSec: Long): LocalDate =
        InstantOf(epochSec)

    private fun InstantOf(epochSec: Long): LocalDate =
        java.time.Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate()
}

@Composable
fun CalendarScreen(navController: NavController) {
    val vm: CalendarViewModel = fpmViewModel { CalendarViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var detailPost by remember { mutableStateOf<ScheduledPost?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (state.loading) {
            LoadingView("Loading calendar…")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.changeMonth(-1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(
                        "${state.month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${state.month.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { vm.changeMonth(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                    }
                }
                MonthGrid(
                    month = state.month,
                    scheduledDays = state.scheduled.filter { it.status == "scheduled" }
                        .map { vm.dayOfEpoch(it.scheduledFor) }.toSet(),
                    publishedDays = state.published.map { vm.dayOfEpoch(it.createdTime) }.toSet(),
                    selectedDay = state.selectedDay,
                    onDayClick = vm::selectDay
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Scheduled")
                    LegendDot(MaterialTheme.colorScheme.tertiary, "Published")
                }
            }
            item {
                SectionTitle(
                    "Selected day: ${state.selectedDay.dayOfMonth} " +
                        state.selectedDay.month.name.lowercase().replaceFirstChar { it.uppercase() }
                )
            }
            val dayScheduled = state.scheduled.filter { vm.dayOfEpoch(it.scheduledFor) == state.selectedDay }
            val dayPublished = state.published.filter { vm.dayOfEpoch(it.createdTime) == state.selectedDay }
            if (dayScheduled.isEmpty() && dayPublished.isEmpty()) {
                item { EmptyView("Nothing on this day.") }
            } else {
                items(dayScheduled) { post ->
                    ScheduledRow(post, onClick = { detailPost = post })
                }
                items(dayPublished) { post ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                post.message?.take(120) ?: "(no text)",
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2
                            )
                            Text(
                                "Published • ${TimeFmt.dateTime(post.createdTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    detailPost?.let { post ->
        ScheduledDetailDialog(
            post = post,
            onDismiss = { detailPost = null },
            onCancel = { vm.cancelScheduled(post); detailPost = null },
            onSave = { msg, time -> vm.updateScheduled(post, msg, time); detailPost = null }
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
        )
        Spacer(Modifier.padding(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    scheduledDays: Set<LocalDate>,
    publishedDays: Set<LocalDate>,
    selectedDay: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leadingBlanks = first.dayOfWeek.value - 1 // Monday-first
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + days

    Column {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(((cells.size / 7 + 1) * 52).dp),
            userScrollEnabled = false
        ) {
            items(cells.size) { i ->
                val day = cells[i]
                if (day == null) {
                    Box(Modifier.aspectRatio(1f))
                } else {
                    val isSelected = day == selectedDay
                    val isToday = day == LocalDate.now()
                    Column(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .clickable { onDayClick(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else null
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (day in scheduledDays) Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (day in publishedDays) Box(
                                Modifier.size(6.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledRow(post: ScheduledPost, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                post.message?.take(120) ?: "(no text)",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${post.postType.replaceFirstChar { it.uppercase() }} • ${TimeFmt.dateTime(post.scheduledFor)} • ${post.status}",
                style = MaterialTheme.typography.labelSmall,
                color = if (post.status == "failed") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary
            )
            if (post.status == "failed" && post.error != null) {
                Text(post.error!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledDetailDialog(
    post: ScheduledPost,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String?, Long?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(post.message ?: "") }
    var newTime by remember { mutableStateOf<Long?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "Edit scheduled post" else "Scheduled post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    OutlinedTextField(
                        value = message, onValueChange = { message = it },
                        label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 3
                    )
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(newTime?.let { TimeFmt.dateTime(it) } ?: TimeFmt.dateTime(post.scheduledFor))
                    }
                } else {
                    Text(post.message ?: "(no text)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${post.postType.replaceFirstChar { it.uppercase() }} • ${TimeFmt.dateTime(post.scheduledFor)} • ${post.status}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = {
                    onSave(message.ifBlank { null }, newTime)
                }) { Text("Save") }
            } else if (post.status == "scheduled") {
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        },
        dismissButton = {
            Row {
                if (post.status == "scheduled" && !editing) {
                    TextButton(onClick = { confirmCancel = true }) { Text("Cancel post") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )

    if (showTimePicker) {
        // Reuse the schedule dialog from Create Post screen via a local lightweight picker.
        TimePickDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { epochSec -> newTime = epochSec; showTimePicker = false }
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this scheduled post?") },
            text = { Text("It will not be published. This cannot be undone.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Yes, cancel it") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var step by remember { mutableStateOf(0) }
    val dateState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 3_600_000
    )
    val timeState = androidx.compose.material3.rememberTimePickerState(is24Hour = true)
    if (step == 0) {
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { if (dateState.selectedDateMillis != null) step = 1 }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) { androidx.compose.material3.DatePicker(dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a time") },
            text = { androidx.compose.material3.TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = dateState.selectedDateMillis ?: return@TextButton
                    val localDate = java.time.Instant.ofEpochMilli(dateMillis)
                        .atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                    val epochSec = localDate.atTime(timeState.hour, timeState.minute)
                        .atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
                    onConfirm(epochSec)
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}
