package com.facebookpagemanager.app

import android.app.Application
import androidx.work.Configuration
import com.facebookpagemanager.app.di.AppContainer
import com.facebookpagemanager.app.worker.FpmWorkerFactory

class FpmApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Seed demo content on first launch so Demo Mode works with zero setup.
        container.seedDemoIfNeeded()
    }

    // Custom WorkManager configuration (we disabled the androidx.startup
    // initializer in the manifest so our WorkerFactory is always used).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(FpmWorkerFactory(container))
            .build()
}
