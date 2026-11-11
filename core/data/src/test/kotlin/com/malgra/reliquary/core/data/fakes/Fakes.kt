package com.malgra.reliquary.core.data.fakes

import com.malgra.reliquary.core.data.library.LibraryCache
import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler

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
