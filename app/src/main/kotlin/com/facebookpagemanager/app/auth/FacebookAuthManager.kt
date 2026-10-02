package com.facebookpagemanager.app.auth

import android.app.Activity
import android.content.Context
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebookpagemanager.app.BuildConfig
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.data.repo.GraphApiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Facebook Login via the official Meta Facebook SDK for Android.
 * No passwords, no cookie extraction, no scraping — OAuth only.
 */
class FacebookAuthManager(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val graphRepository: GraphApiRepository,
) {

    companion object {
        /** Permissions requested at login. Anything beyond public_profile /
         *  pages_show_list needs Meta App Review before it works for non-admin users. */
        val PERMISSIONS = listOf(
            "public_profile",
            "pages_show_list",
            "pages_read_engagement",
            "pages_manage_posts",
            "pages_read_user_content",
            "read_insights",
            "pages_messaging"
        )

        const val PLACEHOLDER_APP_ID = "YOUR_FACEBOOK_APP_ID"
    }

    fun isConfigured(): Boolean =
        BuildConfig.FACEBOOK_APP_ID.isNotBlank() &&
            BuildConfig.FACEBOOK_APP_ID != PLACEHOLDER_APP_ID

    fun isLoggedIn(): Boolean = tokenStore.getUserToken() != null

    /**
     * Starts the official Facebook Login flow. Calls [onError] immediately with an
     * explanatory message when no Meta App ID was configured at build time.
     */
    fun login(
        activity: Activity,
        callbackManager: CallbackManager,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!isConfigured()) {
            onError(
                "Facebook Login is not configured in this build.\n\n" +
                    "To enable real Facebook login, create a Meta App at developers.facebook.com, " +
                    "then rebuild with your App ID:\n\n" +
                    "./gradlew assembleDebug -PFACEBOOK_APP_ID=123456789\n\n" +
                    "Until then, Demo Mode (default) lets you try every screen with sample data."
            )
            return
        }
        // Lazy, guarded SDK init: only ever runs here, never at app startup.
        try {
            if (!FacebookSdk.isInitialized()) {
                FacebookSdk.setApplicationId(BuildConfig.FACEBOOK_APP_ID)
                FacebookSdk.sdkInitialize(context.applicationContext)
            }
        } catch (e: Exception) {
            onError("Facebook SDK could not start: ${e.message}")
            return
        }
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onCancel() = onError("Facebook login was cancelled.")

                override fun onError(error: FacebookException) {
                    onError(error.message ?: "Facebook login failed with an unknown error.")
                }

                override fun onSuccess(result: LoginResult) {
                    val token = result.accessToken.token
                    tokenStore.saveUserToken(token)
                    // Fetch the user's Pages + page access tokens via the official Graph API.
                    CoroutineScope(Dispatchers.IO).launch {
                        when (val r = graphRepository.refreshPages(token)) {
                            is RepoResult.Ok -> withContext(Dispatchers.Main) { onSuccess() }
                            is RepoResult.Err -> withContext(Dispatchers.Main) {
                                onError("Logged in, but loading Pages failed: ${r.message}")
                            }
                        }
                    }
                }
            }
        )
        LoginManager.getInstance().logIn(activity, PERMISSIONS)
    }

    /** Secure logout: ends the Facebook SDK session AND wipes all stored tokens. */
    fun logout() {
        try {
            if (FacebookSdk.isInitialized()) {
                LoginManager.getInstance().logOut()
            }
        } catch (_: Exception) {
            // SDK was never started (Demo Mode) — nothing to tear down.
        }
        tokenStore.clear()
    }
}
