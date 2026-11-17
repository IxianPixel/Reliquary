package com.malgra.reliquary.core.data.library

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.model.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import kotlin.time.Duration.Companion.minutes

/** How long a cached list is shown before it is refreshed on open. */
internal val LIST_STALE_AFTER = 15.minutes

@OptIn(ExperimentalPagingApi::class)
internal fun <Row : Any, Dto, Model : Any> ReliquaryDatabase.listingPager(
    listKey: String,
    library: LibraryApi,
    clock: () -> Long,
    fetch: suspend TubeArchivistApi.(page: Int) -> Page<Dto>,
    store: suspend (items: List<Dto>, baseUrl: HttpUrl, now: Long) -> List<String>,
    source: () -> PagingSource<Int, Row>,
    toModel: (Row) -> Model,
): Flow<PagingData<Model>> = Pager(
    config = PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false),
    remoteMediator = ListingMediator(
        listKey = listKey,
        db = this,
        library = library,
        clock = clock,
        staleAfterMillis = LIST_STALE_AFTER.inWholeMilliseconds,
        fetch = fetch,
        store = store,
    ),
    pagingSourceFactory = source,
).flow.map { data -> data.map(toModel) }
