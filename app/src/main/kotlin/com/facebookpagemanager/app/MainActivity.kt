package com.facebookpagemanager.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // Lazy: creating it touches the Facebook SDK, which we only initialize
    // on demand (see FacebookAuthManager). Never at activity startup.
    private val callbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) { /* edge-to-edge is cosmetic; never crash for it */ }
        try {
            requestNotificationPermissionIfNeeded()
        } catch (_: Throwable) { /* permission request must never crash startup */ }

        val app = application as FpmApplication
        val bootFailure: Throwable? = try {
            app.startupError
        } catch (_: Throwable) {
            // Even reading the app state failed (e.g. container not initialized).
            IllegalStateException("App failed to initialize. Please reinstall the app.")
        }

        try {
            setContent {
                if (bootFailure != null) {
                    StartupErrorScreen(bootFailure)
                } else {
                    val container = app.container
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
        } catch (t: Throwable) {
            // Last resort: if even Compose setup throws, show a plain Android view
            // so the app NEVER just vanishes on launch.
            try {
                setContent { StartupErrorScreen(t) }
            } catch (_: Throwable) {
                val tv = android.widget.TextView(this).apply {
                    text = "Facebook Page Manager could not start.\n\n${t.message}\n\nPlease reinstall the app."
                    setPadding(48, 48, 48, 48)
                }
                setContentView(tv)
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

/**
 * Friendly fallback shown when the app cannot initialize normally.
 * This guarantees the app OPENS to a readable screen instead of vanishing.
 */
@Composable
fun StartupErrorScreen(error: Throwable) {
    val context = LocalContext.current
    val details = remember(error) {
        buildString {
            appendLine(error.javaClass.name)
            appendLine(error.message ?: "(no message)")
            val trace = error.stackTrace.take(8).joinToString("\n") { "  at $it" }
            appendLine(trace)
        }
    }
    FpmTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Couldn't start fully",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Facebook Page Manager ran into a startup problem, " +
                        "so it opened in safe mode instead of closing.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = {
                    try {
                        val filesDir = context.filesDir
                        java.io.File(filesDir, "crash-report.txt").writeText(
                            "Startup failure report\n\n$details\n\nFull trace:\n" +
                                error.stackTraceToString()
                        )
                    } catch (_: Throwable) { }
                    (context as? Activity)?.recreate()
                }) {
                    Text("Try again")
                }
            }
        }
    }
}
