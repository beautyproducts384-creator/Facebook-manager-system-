package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.data.model.PostTemplate
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.nav.Routes
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TemplatesViewModel(val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val templates: List<PostTemplate> = emptyList(),
        val dialogOpen: Boolean = false,
        val editingId: Long = 0,
        val title: String = "",
        val text: String = "",
        val category: String = "General",
        val deleteTarget: PostTemplate? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val templates = container.currentRepository().getTemplates()
            _state.update { it.copy(loading = false, templates = templates) }
        }
    }

    fun openNew() = _state.update {
        it.copy(dialogOpen = true, editingId = 0, title = "", text = "", category = "General")
    }

    fun openEdit(t: PostTemplate) = _state.update {
        it.copy(dialogOpen = true, editingId = t.id, title = t.title, text = t.text, category = t.category)
    }

    fun closeDialog() = _state.update { it.copy(dialogOpen = false) }
    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setText(v: String) = _state.update { it.copy(text = v) }
    fun setCategory(v: String) = _state.update { it.copy(category = v) }

    fun save() {
        val s = _state.value
        if (s.title.isBlank() || s.text.isBlank()) return
        viewModelScope.launch {
            val repo = container.currentRepository()
            val template = PostTemplate(
                id = s.editingId,
                title = s.title.trim(),
                text = s.text.trim(),
                category = s.category.trim().ifBlank { "General" }
            )
            if (s.editingId == 0L) repo.saveTemplate(template) else repo.updateTemplate(template)
            _state.update { it.copy(dialogOpen = false) }
            refresh()
        }
    }

    fun confirmDelete(t: PostTemplate) = _state.update { it.copy(deleteTarget = t) }
    fun cancelDelete() = _state.update { it.copy(deleteTarget = null) }

    fun deleteConfirmed() {
        val t = _state.value.deleteTarget ?: return
        viewModelScope.launch {
            container.currentRepository().deleteTemplate(t)
            _state.update { it.copy(deleteTarget = null) }
            refresh()
        }
    }

    fun useTemplate(t: PostTemplate) {
        container.pendingComposerText = t.text
    }
}

@Composable
fun TemplatesScreen(navController: NavController) {
    val vm: TemplatesViewModel = fpmViewModel { TemplatesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = vm::openNew) {
                Icon(Icons.Filled.Add, contentDescription = "New template")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SectionTitle("Post Templates")
            Text(
                "Reusable captions. Tap \"Use\" to load one into the composer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.padding(4.dp))
            when {
                state.loading -> LoadingView("Loading templates…")
                state.templates.isEmpty() -> EmptyView("No templates yet. Tap + to create one.")
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val grouped = state.templates.groupBy { it.category.ifBlank { "General" } }
                    grouped.forEach { (category, itemsInCategory) ->
                        item {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(itemsInCategory) { t ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            t.title,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { vm.openEdit(t) }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { vm.confirmDelete(t) }) {
                                            Icon(
                                                Icons.Filled.Delete, contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Text(
                                        t.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 4
                                    )
                                    Spacer(Modifier.padding(4.dp))
                                    TextButton(
                                        onClick = {
                                            vm.useTemplate(t)
                                            navController.navigate(Routes.CREATE)
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Use in composer") }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.padding(32.dp)) }
                }
            }
        }
    }

    if (state.dialogOpen) {
        AlertDialog(
            onDismissRequest = vm::closeDialog,
            title = { Text(if (state.editingId == 0L) "New template" else "Edit template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.title, onValueChange = vm::setTitle,
                        label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = state.category, onValueChange = vm::setCategory,
                        label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = state.text, onValueChange = vm::setText,
                        label = { Text("Caption text") }, modifier = Modifier.fillMaxWidth(), minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::save,
                    enabled = state.title.isNotBlank() && state.text.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = vm::closeDialog) { Text("Cancel") } }
        )
    }

    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("Delete template?") },
            text = { Text("\"${target.title}\" will be removed.") },
            confirmButton = { TextButton(onClick = vm::deleteConfirmed) { Text("Delete") } },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("Keep") } }
        )
    }
}
