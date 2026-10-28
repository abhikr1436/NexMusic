package com.nexmusic.app

import android.app.Application
import android.os.Build
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.nexmusic.app.di.nexModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin

class NexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NexApplication)
            // Database, repositories, the YouTube Music client and the media service all come from the shared core.
            loadAllModules(
                AppIdentity(
                    applicationId = BuildConfig.APPLICATION_ID,
                    versionName = BuildConfig.VERSION_NAME,
                    platform = "Android ${Build.VERSION.RELEASE}",
                    isDevBuild = BuildConfig.DEBUG,
                ),
            )
            loadKoinModules(nexModule)
        }
    }
}
