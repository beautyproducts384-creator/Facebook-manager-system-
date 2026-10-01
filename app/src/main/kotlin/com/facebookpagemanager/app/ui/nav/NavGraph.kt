package com.facebookpagemanager.app.ui.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.facebookpagemanager.app.ui.components.DemoModeBanner
import com.facebookpagemanager.app.ui.screens.AnalyticsScreen
import com.facebookpagemanager.app.ui.screens.BulkUploadScreen
import com.facebookpagemanager.app.ui.screens.CalendarScreen
import com.facebookpagemanager.app.ui.screens.CommentsScreen
import com.facebookpagemanager.app.ui.screens.CreatePostScreen
import com.facebookpagemanager.app.ui.screens.DashboardScreen
import com.facebookpagemanager.app.ui.screens.InboxDetailScreen
import com.facebookpagemanager.app.ui.screens.InboxScreen
import com.facebookpagemanager.app.ui.screens.MediaLibraryScreen
import com.facebookpagemanager.app.ui.screens.PagesScreen
import com.facebookpagemanager.app.ui.screens.SettingsScreen
import com.facebookpagemanager.app.ui.screens.TemplatesScreen
import com.facebookpagemanager.app.ui.util.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FpmNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val container = appContainer()
    val demoMode by container.modeManager.demoMode.collectAsStateWithLifecycle(initialValue = true)

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // inbox_detail/* counts as inbox for nav highlighting
    val currentTopRoute = ALL_DESTINATIONS.find {
        it.route == currentRoute || (it.route == Routes.INBOX && currentRoute?.startsWith("inbox_detail") == true)
    }?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Facebook Page Manager",
                    modifier = Modifier.padding(16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                ALL_DESTINATIONS.forEach { dest ->
                    NavigationDrawerItem(
                        label = { Text(dest.title) },
                        selected = dest.route == currentTopRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(titleFor(currentTopRoute)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    ALL_DESTINATIONS.filter { it.showInBottomBar }.forEach { dest ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.title) },
                            label = { Text(dest.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (demoMode) DemoModeBanner()
                NavHost(
                    navController = navController,
                    startDestination = Routes.DASHBOARD,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.DASHBOARD) { DashboardScreen(navController) }
                    composable(Routes.PAGES) { PagesScreen(navController) }
                    composable(Routes.CREATE) { CreatePostScreen(navController) }
                    composable(Routes.BULK) { BulkUploadScreen(navController) }
                    composable(Routes.CALENDAR) { CalendarScreen(navController) }
                    composable(Routes.INBOX) { InboxScreen(navController) }
                    composable(Routes.INBOX_DETAIL) { entry ->
                        InboxDetailScreen(
                            navController,
                            entry.arguments?.getString("conversationId") ?: ""
                        )
                    }
                    composable(Routes.COMMENTS) { CommentsScreen(navController) }
                    composable(Routes.MEDIA) { MediaLibraryScreen(navController) }
                    composable(Routes.ANALYTICS) { AnalyticsScreen(navController) }
                    composable(Routes.TEMPLATES) { TemplatesScreen(navController) }
                    composable(Routes.SETTINGS) { SettingsScreen(navController) }
                }
            }
        }
    }
}
