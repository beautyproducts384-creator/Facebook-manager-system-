package com.facebookpagemanager.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.data.model.BulkRow
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.util.TimeFmt
import com.facebookpagemanager.app.ui.util.fpmViewModel
import com.facebookpagemanager.app.util.BulkValidator
import com.facebookpagemanager.app.util.CsvParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BulkUploadViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val pageId: String? = null,
        val pageName: String? = null,
        val fileName: String? = null,
        val rows: List<BulkRow> = emptyList(),
        val parsed: Boolean = false,
        val busy: Boolean = false,
        val doneMessage: String? = null,
        val scheduled: Int = 0,
        val failed: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val repo = container.currentRepository()
            val pages = (repo.getPages() as? RepoResult.Ok)?.value ?: emptyList()
            val selectedId = container.modeManager.selectedPageId.first()
            val page = pages.find { it.id == selectedId } ?: pages.firstOrNull()
            _state.update { it.copy(pageId = page?.id, pageName = page?.name) }
        }
    }

    fun onCsvText(fileName: String, text: String) {
        val parsed = CsvParser.parse(text)
        val rows = BulkValidator.validate(parsed)
        _state.update {
            it.copy(fileName = fileName, rows = rows, parsed = true,
                doneMessage = null, scheduled = 0, failed = 0)
        }
    }

    fun scheduleValid() {
        val s = _state.value
        val pageId = s.pageId ?: return
        val valid = s.rows.filter { it.isValid }
        if (valid.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, doneMessage = null) }
            val repo = container.currentRepository()
            var ok = 0
            var failed = 0
            for (row in valid) {
                val media = row.media.ifBlank { null }?.let {
                    // Keep URLs as-is; local paths are used as file paths by the publisher.
                    it
                }
                when (repo.schedulePost(pageId, row.caption.ifBlank { null },
                    row.link.ifBlank { null }, media, row.postType, row.scheduledForEpochSec)) {
                    is RepoResult.Ok -> ok++
                    is RepoResult.Err -> failed++
                }
            }
            _state.update {
                it.copy(busy = false, scheduled = ok, failed = failed,
                    doneMessage = "Done: $ok scheduled${if (failed > 0) ", $failed failed" else ""}. " +
                        "See the Calendar tab for the full schedule.")
            }
        }
    }
}

@Composable
fun BulkUploadScreen(navController: NavController) {
    val vm: BulkUploadViewModel = fpmViewModel { BulkUploadViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSample by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) {
                ""
            }
            vm.onCsvText(uri.lastPathSegment ?: "upload.csv", text)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Bulk-scheduling for: ${state.pageName ?: "—"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("How it works", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Upload a CSV with columns: date, time, caption, media, link, post_type\n" +
                            "2. Every row is validated first — errors are shown below, nothing is scheduled until you confirm\n" +
                            "3. Tap \"Schedule valid rows\" — each valid row becomes a scheduled post",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showSample = !showSample }) {
                        Text(if (showSample) "Hide sample CSV" else "View sample CSV")
                    }
                    if (showSample) {
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Text(
                                BulkValidator.sampleCsv(),
                                Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { picker.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.UploadFile, null)
                Spacer(Modifier.height(0.dp))
                Text("  Choose CSV file")
            }
        }
        if (state.parsed) {
            val valid = state.rows.count { it.isValid }
            val invalid = state.rows.size - valid
            item {
                SectionTitle("Validation: ${state.fileName ?: ""}")
                Text(
                    "${state.rows.size} rows — $valid valid, $invalid with errors",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (invalid > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            items(state.rows) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.isValid) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (row.isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                null,
                                tint = if (row.isValid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.padding(4.dp))
                            Text(
                                "Row ${row.lineNumber} — ${row.postType} — " +
                                    if (row.scheduledForEpochSec > 0) TimeFmt.dateTime(row.scheduledForEpochSec)
                                    else "${row.date} ${row.time}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (row.caption.isNotBlank()) {
                            Text(row.caption.take(120), style = MaterialTheme.typography.bodySmall)
                        }
                        row.errors.forEach { e ->
                            Text("• $e", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                if (state.busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Button(
                        onClick = vm::scheduleValid,
                        enabled = state.rows.any { it.isValid },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Schedule ${state.rows.count { it.isValid }} valid rows")
                    }
                }
                if (state.doneMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Card { Text(state.doneMessage!!, Modifier.padding(12.dp)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No file loaded yet. Validation happens before anything is scheduled — " +
                        "invalid rows are never scheduled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
