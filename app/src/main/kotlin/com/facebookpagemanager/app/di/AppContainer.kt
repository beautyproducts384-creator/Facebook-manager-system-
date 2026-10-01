package com.facebookpagemanager.app.di

import android.content.Context
import androidx.room.Room
import com.facebookpagemanager.app.BuildConfig
import com.facebookpagemanager.app.auth.FacebookAuthManager
import com.facebookpagemanager.app.auth.TokenStore
import com.facebookpagemanager.app.data.demo.DemoSeeder
import com.facebookpagemanager.app.data.local.AppDatabase
import com.facebookpagemanager.app.data.remote.GraphApiService
import com.facebookpagemanager.app.data.remote.GraphVideoService
import com.facebookpagemanager.app.data.repo.DemoRepository
import com.facebookpagemanager.app.data.repo.GraphApiRepository
import com.facebookpagemanager.app.data.repo.PageManagerRepository
import com.facebookpagemanager.app.prefs.ModeManager
import com.facebookpagemanager.app.worker.NotificationHelper
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection container (no Hilt, keeps the build lean).
 */
class AppContainer(private val appContext: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val modeManager = ModeManager(appContext)
    val tokenStore = TokenStore(appContext)
    val notificationHelper = NotificationHelper(appContext)

    val db: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "fpm.db"
    ).fallbackToDestructiveMigration().build()

    private val gson = GsonBuilder().create()

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val graphRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.GRAPH_API_BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val graphVideoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://graph-video.facebook.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val graphService: GraphApiService by lazy { graphRetrofit.create(GraphApiService::class.java) }
    val graphVideoService: GraphVideoService by lazy {
        graphVideoRetrofit.create(GraphVideoService::class.java)
    }

    val graphRepository = GraphApiRepository(appContext, db, graphService, graphVideoService, tokenStore)
    val demoRepository = DemoRepository(appContext, db, notificationHelper)

    val authManager = FacebookAuthManager(appContext, tokenStore, graphRepository)

    /** In-memory handoff: Templates/Media screens -> Create Post composer. */
    var pendingComposerText: String? = null
    var pendingComposerMediaUri: String? = null

    suspend fun currentRepository(): PageManagerRepository =
        if (modeManager.demoMode.first()) demoRepository else graphRepository

    suspend fun isDemoMode(): Boolean = modeManager.demoMode.first()

    fun seedDemoIfNeeded() {
        appScope.launch {
            DemoSeeder.seedIfEmpty(db, appContext.packageName)
        }
    }
}
