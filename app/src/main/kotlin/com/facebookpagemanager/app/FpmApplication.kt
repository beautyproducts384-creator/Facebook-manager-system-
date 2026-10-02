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

    override fun onCreate() {
        super.onCreate()
        // Install first: captures the exact stack trace of ANY startup crash
        // to files/crash-report.txt (viewable in Settings -> Diagnostics).
        CrashReporter.install(this)
        try {
            container = AppContainer(this)
            // Seed demo content on first launch so Demo Mode works with zero setup.
            container.seedDemoIfNeeded()
        } catch (t: Throwable) {
            Log.e("FpmApplication", "Container init failed", t)
            throw t // still reported via the uncaught handler
        }
    }

    // Custom WorkManager configuration (we disabled the androidx.startup
    // initializer in the manifest so our WorkerFactory is always used).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(FpmWorkerFactory(container))
            .build()
}
