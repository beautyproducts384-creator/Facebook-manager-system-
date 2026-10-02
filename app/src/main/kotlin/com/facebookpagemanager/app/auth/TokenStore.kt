package com.facebookpagemanager.app.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Access tokens are stored encrypted on-device (never in plain prefs, never logged).
 * Page tokens come from /me/accounts after Facebook Login; the user token comes
 * from the official Facebook SDK session.
 *
 * The encrypted store is created LAZILY and guarded: on devices where the
 * Android Keystore is unavailable/broken, we fall back to a plain private
 * SharedPreferences instead of crashing the app on launch.
 */
class TokenStore(private val context: Context) {

    companion object {
        private const val TAG = "TokenStore"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "fpm_tokens",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs unavailable, using fallback store", e)
            context.getSharedPreferences("fpm_tokens_fallback", Context.MODE_PRIVATE)
        }
    }

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
