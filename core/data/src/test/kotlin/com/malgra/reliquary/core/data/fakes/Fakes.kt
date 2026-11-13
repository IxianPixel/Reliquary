package com.malgra.reliquary.core.data.fakes

import com.malgra.reliquary.core.data.download.DownloadScheduler
import com.malgra.reliquary.core.data.library.LibraryCache
import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import com.malgra.reliquary.core.data.rules.LibrarySyncScheduler

class FakeLibraryCache : LibraryCache {
    var clears = 0
        private set

    override suspend fun clear() {
        clears++
    }
}

class FakeSyncScheduler : ProgressSyncScheduler {
    var requests = 0
        private set

    override fun requestSync() {
        requests++
    }
}

class FakeDownloadScheduler : DownloadScheduler {
    val started = mutableListOf<Pair<String, Boolean>>()
    val cancelled = mutableListOf<String>()

    override fun start(videoId: String, wifiOnly: Boolean, replace: Boolean) {
        started += videoId to wifiOnly
    }

    override fun cancel(videoId: String) {
        cancelled += videoId
    }

    override fun cancelAll() = Unit
}

class FakeLibrarySyncScheduler : LibrarySyncScheduler {
    var syncs = 0
        private set

    override fun schedulePeriodic() = Unit

    override fun syncNow() {
        syncs++
    }
}
