package com.malgra.reliquary.core.data.library

import android.util.Log
import com.malgra.reliquary.core.model.LoadError
import kotlin.coroutines.cancellation.CancellationException

sealed interface LoadResult<out T> {
    data class Ok<T>(val value: T) : LoadResult<T>
    data class Failed(val error: LoadError) : LoadResult<Nothing>
}

/** Runs [block], turning any failure except cancellation into a [LoadError]. */
internal suspend fun <T> load(block: suspend () -> T): LoadResult<T> = try {
    LoadResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w("LoadResult", "Load failed", e)
    LoadResult.Failed(e.toLoadError())
}

internal val LoadResult<*>.errorOrNull: LoadError?
    get() = (this as? LoadResult.Failed)?.error
