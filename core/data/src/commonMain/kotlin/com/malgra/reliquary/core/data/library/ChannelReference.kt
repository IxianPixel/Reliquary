package com.malgra.reliquary.core.data.library

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What someone pasted to subscribe to a channel, checked on the device so a
 * video or playlist link gets an error straight away instead of failing
 * silently in TA's subscribe task. Handles (`@name`, `/c/`, `/user/`) can only
 * be resolved by the server, so they're passed through as they are.
 *
 * Mirrors TA's `common/src/urlparser.py`, which only treats text with a scheme
 * as a URL, hence adding one.
 */
@JvmInline
value class ChannelReference private constructor(val query: String) {
    companion object {
        fun parse(input: String): ChannelReference? {
            val text = input.trim()
            if (text.isEmpty() || text.any(Char::isWhitespace)) return null
            if (text.startsWith("@")) return ChannelReference(text).takeIf { text.length > 1 }
            if ("/" !in text && "." !in text) return ChannelReference(text).takeIf { text.isChannelId() }

            val withScheme = if ("://" in text) text else "https://$text"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            if (url.host != "youtube.com" && !url.host.endsWith(".youtube.com")) return null
            if (url.queryParameter("v") != null || url.queryParameter("list") != null) return null
            val path = url.pathSegments.filter(String::isNotEmpty)
            return when (path.firstOrNull()) {
                null, "watch", "shorts", "live", "playlist", "embed", "results", "feed" -> null
                "channel" -> ChannelReference(withScheme).takeIf { path.getOrNull(1)?.isChannelId() == true }
                else -> ChannelReference(withScheme)
            }
        }

        private fun String.isChannelId() = length == 24 && startsWith("UC")
    }
}
