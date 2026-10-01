package com.facebookpagemanager.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facebook.CallbackManager
import com.facebookpagemanager.app.ui.nav.FpmNavHost
import com.facebookpagemanager.app.ui.theme.FpmTheme

val LocalFbLoginHost = compositionLocalOf<FacebookLoginHost?> { null }

/** Implemented by [MainActivity] so Compose screens can trigger the official Facebook Login flow. */
interface FacebookLoginHost {
    fun startFacebookLogin(onSuccess: () -> Unit, onError: (String) -> Unit)
}

class MainActivity : ComponentActivity(), FacebookLoginHost {

    private val callbackManager: CallbackManager = CallbackManager.Factory.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            val container = (application as FpmApplication).container
            val themeChoice by container.modeManager.theme
                .collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (themeChoice) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalFbLoginHost provides this) {
                FpmTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        FpmNavHost()
                    }
                }
            }
        }
    }

    override fun startFacebookLogin(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val container = (application as FpmApplication).container
        container.authManager.login(this, callbackManager, onSuccess, onError)
    }

    @Deprecated("Required by the Facebook SDK CallbackManager")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
