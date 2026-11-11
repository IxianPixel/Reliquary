package com.malgra.reliquary.core.data.fakes

import com.malgra.reliquary.core.data.library.LibraryCache

class FakeLibraryCache : LibraryCache {
    var clears = 0
        private set

    override suspend fun clear() {
        clears++
    }
}
