package com.facebookpagemanager.app.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Access tokens are stored encrypted on-device (never in plain prefs, never logged).
 * Page tokens come from /me/accounts after Facebook Login; the user token comes
 * from the official Facebook SDK session.
 */
class TokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "fpm_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveUserToken(token: String) {
        prefs.edit().putString("user_token", token).apply()
    }

    fun getUserToken(): String? = prefs.getString("user_token", null)

    fun savePageToken(pageId: String, token: String) {
        prefs.edit().putString("page_token_$pageId", token).apply()
    }

    fun getPageToken(pageId: String): String? = prefs.getString("page_token_$pageId", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
