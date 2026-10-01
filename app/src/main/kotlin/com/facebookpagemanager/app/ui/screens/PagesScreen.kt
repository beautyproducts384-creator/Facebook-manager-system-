package com.facebookpagemanager.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.facebookpagemanager.app.LocalFbLoginHost
import com.facebookpagemanager.app.data.model.FbPage
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.ui.components.EmptyView
import com.facebookpagemanager.app.ui.components.ErrorView
import com.facebookpagemanager.app.ui.components.LoadingView
import com.facebookpagemanager.app.ui.components.RequirementCard
import com.facebookpagemanager.app.ui.components.SectionTitle
import com.facebookpagemanager.app.ui.components.formatCount
import com.facebookpagemanager.app.ui.util.fpmViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PagesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val pages: List<FbPage> = emptyList(),
        val selectedPageId: String? = null,
        val error: String? = null,
        val requirement: String? = null,
        val demoMode: Boolean = true,
        val loggedIn: Boolean = false,
        val fbName: String? = null,
        val loginBusy: Boolean = false,
        val loginError: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, requirement = null) }
            val demo = container.isDemoMode()
            val repo = container.currentRepository()
            val loggedIn = container.authManager.isLoggedIn()
            val fbName = container.modeManager.connectedFbName.first()
            when (val r = repo.getPages()) {
                is RepoResult.Err -> _state.update {
                    it.copy(
                        loading = false, error = r.message, requirement = r.requirement,
                        demoMode = demo, loggedIn = loggedIn, fbName = fbName
                    )
                }
                is RepoResult.Ok -> {
                    val pages = r.value
                    var selected = container.modeManager.selectedPageId.first()
                    if (pages.none { it.id == selected }) selected = pages.firstOrNull()?.id
                    if (selected != null) container.modeManager.setSelectedPageId(selected)
                    _state.update {
                        it.copy(
                            loading = false, pages = pages, selectedPageId = selected,
                            demoMode = demo, loggedIn = loggedIn, fbName = fbName
                        )
                    }
                }
            }
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            val name = container.graphRepository.getMyName()
            container.modeManager.setConnectedFbName(name)
            container.modeManager.setDemoMode(false)
            container.modeManager.setSelectedPageId(null)
            refresh()
        }
    }

    fun setLoginBusy(busy: Boolean) = _state.update { it.copy(loginBusy = busy) }
    fun setLoginError(msg: String?) = _state.update { it.copy(loginError = msg) }

    fun selectPage(id: String) {
        viewModelScope.launch {
            container.modeManager.setSelectedPageId(id)
            _state.update { it.copy(selectedPageId = id) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            container.authManager.logout()
            container.modeManager.setConnectedFbName(null)
            container.modeManager.setDemoMode(true)
            container.modeManager.setSelectedPageId(null)
            refresh()
        }
    }
}

@Composable
fun PagesScreen(navController: NavController) {
    val vm: PagesViewModel = fpmViewModel { PagesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val loginHost = LocalFbLoginHost.current
    val scope = rememberCoroutineScope()
    var showLoginError by mutableStateOf(false)

    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingView("Loading Pages…")
            state.error != null && state.pages.isEmpty() -> ErrorView(
                state.error!!, state.requirement
            ) { vm.refresh() }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    SectionTitle("Facebook account")
                    AccountCard(
                        loggedIn = state.loggedIn,
                        fbName = state.fbName,
                        demoMode = state.demoMode,
                        loginBusy = state.loginBusy,
                        onConnect = {
                            vm.setLoginError(null)
                            if (loginHost == null) {
                                vm.setLoginError("Login host unavailable — please restart the app.")
                                showLoginError = true
                                return@AccountCard
                            }
                            vm.setLoginBusy(true)
                            loginHost.startFacebookLogin(
                                onSuccess = {
                                    vm.setLoginBusy(false)
                                    vm.onLoginSuccess()
                                },
                                onError = { msg ->
                                    vm.setLoginBusy(false)
                                    vm.setLoginError(msg)
                                    showLoginError = true
                                }
                            )
                        },
                        onDisconnect = { vm.disconnect() }
                    )
                    if (state.loginError != null && !showLoginError) {
                        Spacer(Modifier.height(8.dp))
                        RequirementCard(state.loginError!!)
                    }
                }

                item {
                    SectionTitle(
                        if (state.demoMode) "Demo Pages (${state.pages.size})"
                        else "Your Facebook Pages (${state.pages.size})"
                    )
                }
                if (state.pages.isEmpty()) {
                    item { EmptyView("No Pages found for this account.") }
                } else {
                    items(state.pages) { page ->
                        PageRow(
                            page = page,
                            selected = page.id == state.selectedPageId,
                            onSelect = { vm.selectPage(page.id) }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showLoginError && state.loginError != null) {
        AlertDialog(
            onDismissRequest = { showLoginError = false },
            title = { Text("Facebook Login") },
            text = { Text(state.loginError!!) },
            confirmButton = { TextButton(onClick = { showLoginError = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun AccountCard(
    loggedIn: Boolean,
    fbName: String?,
    demoMode: Boolean,
    loginBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            loggedIn && fbName != null -> fbName
                            loggedIn -> "Connected"
                            else -> "Not connected"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (demoMode) "Demo Mode — try everything with sample data"
                        else "Real Mode — official Meta Graph API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (loggedIn) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect Facebook (secure logout)")
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loginBusy
                ) {
                    if (loginBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting…")
                    } else {
                        Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Connect with Facebook")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Official Meta OAuth only. You'll be asked for Page permissions; " +
                        "advanced permissions need Meta App Review before they work for non-test users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun PageRow(page: FbPage, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(page.name, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        page.category.ifBlank { null },
                        if (page.followers > 0) "${formatCount(page.followers)} followers" else null
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
