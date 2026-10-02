package com.facebookpagemanager.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.util.CrashReporter
import com.facebookpagemanager.app.worker.FpmWorkerFactory

class FpmApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    /** Non-null when startup init failed; the UI shows a friendly error instead of crashing. */
    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // CrashReporter is already installed by FpmInitProvider (runs before us),
        // but installing again is harmless and covers direct Application use.
        try {
            CrashReporter.install(this)
        } catch (_: Throwable) { /* diagnostics must never break startup */ }
        try {
            container = AppContainer(this)
            // Seed demo content on first launch so Demo Mode works with zero setup.
            container.seedDemoIfNeeded()
        } catch (t: Throwable) {
            // NEVER crash the whole app because a startup dependency failed.
            // Record it; MainActivity will show a friendly error screen instead.
            startupError = t
            Log.e("FpmApplication", "Container init failed - app will show error screen", t)
        }
    }

    // Custom WorkManager configuration (we disabled the androidx.startup
    // initializer in the manifest so our WorkerFactory is always used).
    // Guarded: if the container failed to init, fall back to the default config
    // instead of throwing from here.
    override val workManagerConfiguration: Configuration
        get() = try {
            Configuration.Builder()
                .setWorkerFactory(FpmWorkerFactory(container))
                .build()
        } catch (_: Throwable) {
            Configuration.Builder().build()
        }
}
