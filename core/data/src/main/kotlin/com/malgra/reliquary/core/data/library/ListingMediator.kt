package com.malgra.reliquary.core.data.library

import android.util.Log
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.toFailure
import com.malgra.reliquary.core.network.model.PaginateDto
import com.malgra.reliquary.core.network.model.Page
import okhttp3.HttpUrl
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fills one server-ordered list in Room, a page at a time. The screen reads
 * the list from Room, so a cached list shows at once and offline; it is
 * refreshed from page 1 when it is older than [staleAfterMillis] or the user
 * pulls to refresh.
 *
 * [fetch] gets a page from the signed-in server; [store] upserts its rows
 * inside the write transaction and returns their IDs in server order.
 */
@OptIn(ExperimentalPagingApi::class)
internal class ListingMediator<Row : Any, Dto>(
    private val listKey: String,
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
    private val staleAfterMillis: Long,
    private val fetch: suspend TubeArchivistApi.(page: Int) -> Page<Dto>,
    private val store: suspend (items: List<Dto>, baseUrl: HttpUrl, now: Long) -> List<String>,
) : RemoteMediator<Int, Row>() {

    private val listings = db.listingDao()

    override suspend fun initialize(): InitializeAction {
        val state = listings.state(listKey)
        val fresh = state != null && clock() - state.refreshedAtMillis < staleAfterMillis
        return if (fresh) InitializeAction.SKIP_INITIAL_REFRESH else InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Row>): MediatorResult {
        val previous = listings.state(listKey)
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            // No state yet means page 1 hasn't loaded; Paging appends again after the refresh.
            LoadType.APPEND -> previous?.nextPage
                ?: return MediatorResult.Success(endOfPaginationReached = previous != null)
        }
        return try {
            val server = library.server()
            val response = server.api.fetchPage(page)
            val now = clock()
            val next = nextPage(response.paginate)
            db.withTransaction {
                if (loadType == LoadType.REFRESH) listings.clearItems(listKey)
                listings.append(listKey, store(response.data, server.baseUrl, now))
                listings.upsertState(
                    ListingStateEntity(
                        listKey = listKey,
                        nextPage = next,
                        totalCount = response.paginate?.totalHits ?: previous?.totalCount,
                        refreshedAtMillis = if (loadType == LoadType.REFRESH) now else previous?.refreshedAtMillis ?: now,
                    ),
                )
            }
            MediatorResult.Success(endOfPaginationReached = next == null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Loading $listKey ($loadType) failed", e)
            MediatorResult.Error(e)
        }
    }

    private companion object {
        const val TAG = "ListingMediator"
    }

    /** TA answers 404 for a page past the end, which here just means an empty page. */
    private suspend fun TubeArchivistApi.fetchPage(page: Int): Page<Dto> = try {
        fetch(page)
    } catch (e: Exception) {
        val failure = e.toFailure()
        if (failure is Failure.Http && failure.code == 404) Page(emptyList(), null) else throw e
    }
}

/** The page after this one, or null on the last page (or an unpaginated response). */
internal fun nextPage(paginate: PaginateDto?): Int? = when {
    paginate == null -> null
    !paginate.nextPages.isNullOrEmpty() -> paginate.currentPage + 1
    paginate.lastPage > paginate.currentPage -> paginate.currentPage + 1
    else -> null
}
