package com.facebookpagemanager.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "fpm_settings")

/**
 * App preferences: Demo/Real mode, theme, selected page.
 * Demo Mode is the default so the app is fully usable with zero setup.
 */
class ModeManager(private val context: Context) {

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val THEME = stringPreferencesKey("theme") // "system" | "light" | "dark"
        val SELECTED_PAGE_ID = stringPreferencesKey("selected_page_id")
        val CONNECTED_FB_NAME = stringPreferencesKey("connected_fb_name")
    }

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEMO_MODE] ?: true }
    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "system" }
    val selectedPageId: Flow<String?> = context.dataStore.data.map { it[Keys.SELECTED_PAGE_ID] }
    val connectedFbName: Flow<String?> = context.dataStore.data.map { it[Keys.CONNECTED_FB_NAME] }

    suspend fun isDemoMode(): Boolean = demoMode.first()

    suspend fun setDemoMode(demo: Boolean) {
        context.dataStore.edit { it[Keys.DEMO_MODE] = demo }
    }

    suspend fun setTheme(choice: String) {
        context.dataStore.edit { it[Keys.THEME] = choice }
    }

    suspend fun setSelectedPageId(pageId: String?) {
        context.dataStore.edit {
            if (pageId == null) it.remove(Keys.SELECTED_PAGE_ID) else it[Keys.SELECTED_PAGE_ID] = pageId
        }
    }

    suspend fun setConnectedFbName(name: String?) {
        context.dataStore.edit {
            if (name == null) it.remove(Keys.CONNECTED_FB_NAME) else it[Keys.CONNECTED_FB_NAME] = name
        }
    }
}
