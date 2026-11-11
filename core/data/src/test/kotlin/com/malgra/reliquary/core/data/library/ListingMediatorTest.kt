package com.malgra.reliquary.core.data.library

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.VideoEntity
import com.malgra.reliquary.core.network.model.PaginateDto
import com.malgra.reliquary.core.network.model.VideoDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingMediatorTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private var now = 1_000_000L

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDatabase()
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    private fun mediator(listKey: String = "test") = ListingMediator<VideoEntity, VideoDto>(
        listKey = listKey,
        db = db,
        library = libraryApiFor(server),
        clock = { now },
        staleAfterMillis = 60_000,
        fetch = { page -> videos(page = page) },
        store = { items, baseUrl, at ->
            db.videoDao().upsert(items.map { it.toEntity(baseUrl, at) })
            items.map { it.youtubeId }
        },
    )

    private val emptyState = PagingState<Int, VideoEntity>(emptyList(), null, PagingConfig(20), 0)

    private suspend fun listed(listKey: String = "test") = db.videoDao().observeListing(listKey).first().map { it.id }

    @Test fun refreshThenAppendKeepsServerOrderUntilTheLastPage() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("c", "a", "e"), page = 1, next = true)))
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("b", "d"), page = 2, next = false)))
        val mediator = mediator()

        val first = mediator.load(LoadType.REFRESH, emptyState)
        assertIs<RemoteMediator.MediatorResult.Success>(first)
        assertEquals(false, first.endOfPaginationReached)
        assertEquals("/api/video/?page=1", server.takeRequest().target)

        val second = mediator.load(LoadType.APPEND, emptyState)
        assertIs<RemoteMediator.MediatorResult.Success>(second)
        assertTrue(second.endOfPaginationReached)
        assertEquals("/api/video/?page=2", server.takeRequest().target)

        assertEquals(listOf("c", "a", "e", "b", "d"), listed())
        val state = db.listingDao().state("test")
        assertNull(state?.nextPage)
        assertEquals(5, state?.totalCount)

        // At the end, APPEND doesn't call the server again.
        val third = mediator.load(LoadType.APPEND, emptyState)
        assertIs<RemoteMediator.MediatorResult.Success>(third)
        assertTrue(third.endOfPaginationReached)
        assertEquals(2, server.requestCount)
    }

    @Test fun refreshReplacesTheList() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("a", "b"), page = 1, next = false)))
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("b", "c"), page = 1, next = false)))
        val mediator = mediator()

        mediator.load(LoadType.REFRESH, emptyState)
        mediator.load(LoadType.REFRESH, emptyState)

        assertEquals(listOf("b", "c"), listed())
    }

    @Test fun notFoundPastTheEndEndsPaging() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("a"), page = 1, next = true)))
        server.enqueue(MockResponse(code = 404, body = """{"detail":"Not found."}"""))
        val mediator = mediator()

        mediator.load(LoadType.REFRESH, emptyState)
        val result = mediator.load(LoadType.APPEND, emptyState)

        assertIs<RemoteMediator.MediatorResult.Success>(result)
        assertTrue(result.endOfPaginationReached)
        assertEquals(listOf("a"), listed())
    }

    @Test fun serverErrorIsReportedAndKeepsTheCache() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("a"), page = 1, next = false)))
        server.enqueue(MockResponse(code = 500))
        val mediator = mediator()

        mediator.load(LoadType.REFRESH, emptyState)
        val result = mediator.load(LoadType.REFRESH, emptyState)

        assertIs<RemoteMediator.MediatorResult.Error>(result)
        assertEquals(listOf("a"), listed())
    }

    @Test fun aFreshListSkipsTheInitialRefresh() = runTest {
        val mediator = mediator()
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())

        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("a"), page = 1, next = false)))
        mediator.load(LoadType.REFRESH, emptyState)
        now += 30_000
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, mediator.initialize())
        now += 60_000
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, mediator.initialize())
    }

    @Test fun nextPageFollowsPaginate() {
        assertNull(nextPage(null))
        assertEquals(3, nextPage(PaginateDto(currentPage = 2, lastPage = 5, nextPages = listOf(3, 4))))
        assertNull(nextPage(PaginateDto(currentPage = 5, lastPage = 5, nextPages = emptyList())))
    }
}
