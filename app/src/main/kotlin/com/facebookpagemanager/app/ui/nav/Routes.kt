package com.facebookpagemanager.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val DASHBOARD = "dashboard"
    const val PAGES = "pages"
    const val CREATE = "create"
    const val BULK = "bulk"
    const val CALENDAR = "calendar"
    const val INBOX = "inbox"
    const val INBOX_DETAIL = "inbox_detail/{conversationId}"
    const val COMMENTS = "comments"
    const val MEDIA = "media"
    const val ANALYTICS = "analytics"
    const val TEMPLATES = "templates"
    const val SETTINGS = "settings"

    fun inboxDetail(conversationId: String) = "inbox_detail/$conversationId"
}

data class NavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean = false,
)

val ALL_DESTINATIONS = listOf(
    NavDestination(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard, true),
    NavDestination(Routes.PAGES, "Pages", Icons.Filled.Group, true),
    NavDestination(Routes.CREATE, "Create Post", Icons.Filled.AddCircle, true),
    NavDestination(Routes.CALENDAR, "Calendar", Icons.Filled.CalendarMonth, true),
    NavDestination(Routes.INBOX, "Inbox", Icons.Filled.Inbox, true),
    NavDestination(Routes.BULK, "Bulk Upload", Icons.Filled.Upload),
    NavDestination(Routes.COMMENTS, "Comments", Icons.Filled.Comment),
    NavDestination(Routes.MEDIA, "Media Library", Icons.Filled.PhotoLibrary),
    NavDestination(Routes.ANALYTICS, "Analytics", Icons.Filled.Analytics),
    NavDestination(Routes.TEMPLATES, "Templates", Icons.Filled.Description),
    NavDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

fun titleFor(route: String?): String =
    ALL_DESTINATIONS.find { it.route == route }?.title ?: "Facebook Page Manager"
