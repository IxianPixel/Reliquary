package com.malgra.reliquary.core.data.rules

import android.util.Log
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.library.LibraryApi
import com.malgra.reliquary.core.data.library.ListKeys
import com.malgra.reliquary.core.data.library.NotSignedInException
import com.malgra.reliquary.core.data.library.nextPage
import com.malgra.reliquary.core.data.library.toEntity
import com.malgra.reliquary.core.data.library.toModel
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.KeepOfflineRuleEntity
import com.malgra.reliquary.core.model.KeepOfflineRule
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.model.Page
import com.malgra.reliquary.core.network.model.VideoDto
import com.malgra.reliquary.core.network.toFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Runs the library sync (rules plus a refresh of downloaded videos) in the background. */
interface LibrarySyncScheduler {
    /** The regular run, about every 12 hours. */
    fun schedulePeriodic()

    /** A run as soon as there's a connection, e.g. after a rule is saved. */
    fun syncNow()
}

/**
 * Keep-offline rules, and the engine that applies them.
 *
 * A rule only ever removes downloads it made itself; videos the user downloads
 * are left alone. Deleting a rule keeps its videos as ordinary downloads.
 */
@Singleton
class KeepOfflineRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val downloads: DownloadRepository,
    private val scheduler: LibrarySyncScheduler,
    private val clock: () -> Long,
) {
    @Inject constructor(
        db: ReliquaryDatabase,
        library: LibraryApi,
        downloads: DownloadRepository,
        scheduler: LibrarySyncScheduler,
    ) : this(db, library, downloads, scheduler, System::currentTimeMillis)

    enum class Outcome { Done, Retry }

    private val rules = db.keepOfflineRuleDao()
    private val downloadRows = db.downloadDao()

    fun rules(): Flow<List<KeepOfflineRule>> = rules.observeAll().map { rows -> rows.map { it.toModel() } }

    fun rule(kind: KeepOfflineRule.Kind, sourceId: String): Flow<KeepOfflineRule?> =
        rules.observe(KeepOfflineRule.idFor(kind, sourceId)).map { it?.toModel() }

    /** Saves the rule and applies it as soon as there's a connection. */
    suspend fun save(rule: KeepOfflineRule) {
        // Editing keeps the rule's place in the list.
        rules.upsert(rule.toEntity(rules.get(rule.id)?.createdAtMillis ?: clock()))
        scheduler.syncNow()
    }

    /** Deletes the rule; the videos it downloaded stay, as the user's own downloads. */
    suspend fun delete(ruleId: String) {
        rules.delete(ruleId)
        downloadRows.detachRule(ruleId)
    }

    /** A rough size per video for the rule sheet's estimate, from what's cached; null if unknown. */
    suspend fun typicalVideoBytes(kind: KeepOfflineRule.Kind, sourceId: String): Long? {
        val stats = db.videoStatsDao()
        val average = when (kind) {
            KeepOfflineRule.Kind.Channel -> stats.averageSizeForChannel(sourceId)
            KeepOfflineRule.Kind.Playlist -> stats.averageSizeForListing(ListKeys.playlistVideos(sourceId))
        }
        return average?.toLong()?.takeIf { it > 0 }
    }

    /**
     * The full run: refresh every downloaded video's details (so watched state
     * from other devices counts), apply each rule, then clean up watched videos.
     */
    suspend fun syncAll(): Outcome {
        val outcome = try {
            val server = library.server()
            refreshDownloaded(server.api, server.baseUrl)
            for (rule in rules.all()) apply(rule.toModel(), server.api, server.baseUrl)
            Outcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (_: NotSignedInException) {
            Outcome.Done
        } catch (e: Exception) {
            Log.w(TAG, "Library sync failed", e)
            Outcome.Retry
        }
        // Offline too: watched state known on this device is enough to free space.
        removeWatched()
        return outcome
    }

    /**
     * Removes rule downloads whose video is now watched, for rules that ask for
     * it. Local only, so it's cheap enough to run after every progress sync.
     */
    suspend fun removeWatched() {
        for (rule in rules.all().filter { it.removeWhenWatched }) {
            for (download in downloadRows.byRule(rule.id)) {
                val watched = db.videoDao().get(download.videoId)?.toModel()?.watched ?: continue
                if (watched) downloads.remove(download.videoId)
            }
        }
    }

    private suspend fun refreshDownloaded(api: TubeArchivistApi, baseUrl: HttpUrl) {
        for (id in downloadRows.completedIds().take(MAX_REFRESH)) {
            try {
                db.videoDao().upsert(listOf(api.video(id).toEntity(baseUrl, clock())))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = e.toFailure()
                if (failure is Failure.Http && failure.code == 404) continue // gone from the archive; keep the file
                throw e
            }
        }
    }

    /** Downloads what the rule wants and removes its downloads that dropped out. */
    private suspend fun apply(rule: KeepOfflineRule, api: TubeArchivistApi, baseUrl: HttpUrl) {
        val limit = rule.keepCount ?: MAX_PLAYLIST_VIDEOS
        val skipWatched = rule.unwatchedOnly || rule.removeWhenWatched
        val candidates = mutableListOf<String>()
        var page: Int? = 1
        while (page != null && page <= MAX_PAGES && candidates.size < limit + EXTRA_FOR_WATCHED) {
            val response = fetch(api, rule, page) ?: break
            db.videoDao().upsert(response.data.map { it.toEntity(baseUrl, clock()) })
            candidates += response.data.map { it.youtubeId }
            page = nextPage(response.paginate)
        }
        // Local watched state counts too: a video finished offline shouldn't come back.
        val wanted = candidates
            .filterNot { skipWatched && db.videoDao().get(it)?.toModel()?.watched == true }
            .take(limit)
            .toSet()

        wanted.forEach { downloads.startForRule(it, rule.id, rule.wifiOnly) }
        downloadRows.byRule(rule.id)
            .filter { it.videoId !in wanted }
            .forEach { downloads.remove(it.videoId) }
    }

    private suspend fun fetch(api: TubeArchivistApi, rule: KeepOfflineRule, page: Int): Page<VideoDto>? = try {
        val watch = if (rule.unwatchedOnly) "unwatched" else null
        when (rule.kind) {
            KeepOfflineRule.Kind.Channel ->
                api.videos(page = page, channel = rule.sourceId, watch = watch, sort = "published", order = "desc", type = "videos")
            KeepOfflineRule.Kind.Playlist -> api.videos(page = page, playlist = rule.sourceId, watch = watch)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failure = e.toFailure()
        if (failure is Failure.Http && failure.code == 404) null else throw e // 404 past the last page
    }

    private companion object {
        const val TAG = "KeepOffline"
        const val MAX_PAGES = 20
        const val MAX_PLAYLIST_VIDEOS = 200
        const val MAX_REFRESH = 200

        /** Fetch a little past the limit so watched videos skipped locally can be replaced. */
        const val EXTRA_FOR_WATCHED = 5
    }
}

private fun KeepOfflineRuleEntity.toModel() = KeepOfflineRule(
    kind = if (kind == "playlist") KeepOfflineRule.Kind.Playlist else KeepOfflineRule.Kind.Channel,
    sourceId = sourceId,
    name = name,
    thumbnailUrl = thumbnailUrl,
    keepCount = keepCount,
    unwatchedOnly = unwatchedOnly,
    wifiOnly = wifiOnly,
    removeWhenWatched = removeWhenWatched,
)

private fun KeepOfflineRule.toEntity(createdAtMillis: Long) = KeepOfflineRuleEntity(
    id = id,
    kind = kind.name.lowercase(),
    sourceId = sourceId,
    name = name,
    thumbnailUrl = thumbnailUrl,
    keepCount = keepCount?.coerceIn(1, KeepOfflineRule.MAX_KEEP_COUNT),
    unwatchedOnly = unwatchedOnly,
    wifiOnly = wifiOnly,
    removeWhenWatched = removeWhenWatched,
    createdAtMillis = createdAtMillis,
)
