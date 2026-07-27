package com.pocketmind

import android.app.Application
import com.pocketmind.data.sync.SyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Application-level entry point for PocketMind's dependency graph. */
@HiltAndroidApp
class PocketMindApplication : Application() {
    @Inject lateinit var syncCoordinator: SyncCoordinator

    override fun onCreate() {
        super.onCreate()
        syncCoordinator.start()
    }
}
