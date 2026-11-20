package com.malgra.reliquary.core.shell

import androidx.navigation3.runtime.NavKey
import org.junit.Test
import kotlin.test.assertEquals

class ListDetailTest {
    @Test fun openingFromAListAddsTheDetail() {
        val stack = mutableListOf<NavKey>(Home, Channels)
        stack.openFromList(ChannelDetail("a"))
        assertEquals(listOf<NavKey>(Home, Channels, ChannelDetail("a")), stack)
    }

    // On a wide window the list stays beside its detail, so picking another item replaces it rather than stacking up.
    @Test fun pickingAnotherChannelReplacesTheDetailOnTop() {
        val stack = mutableListOf<NavKey>(Home, Channels, ChannelDetail("a"))
        stack.openFromList(ChannelDetail("b"))
        assertEquals(listOf<NavKey>(Home, Channels, ChannelDetail("b")), stack)
    }

    @Test fun pickingAnotherPlaylistReplacesTheDetailOnTop() {
        val stack = mutableListOf<NavKey>(Home, Playlists, PlaylistDetail("p"))
        stack.openFromList(PlaylistDetail("q"))
        assertEquals(listOf<NavKey>(Home, Playlists, PlaylistDetail("q")), stack)
    }

    @Test fun aScreenOnTopIsKept() {
        val stack = mutableListOf<NavKey>(Home, Channels, Search)
        stack.openFromList(ChannelDetail("a"))
        assertEquals(listOf<NavKey>(Home, Channels, Search, ChannelDetail("a")), stack)
    }

    @Test fun backOnANarrowWindowReturnsToTheList() {
        val stack = mutableListOf<NavKey>(Home, Channels, ChannelDetail("a"))
        stack.back(twoPanes = false)
        assertEquals(listOf<NavKey>(Home, Channels), stack)
    }

    // Back from a channel on a wide window goes where Back from the Channels list goes.
    @Test fun backOnAWideWindowLeavesTheList() {
        val stack = mutableListOf<NavKey>(Home, Channels, ChannelDetail("a"))
        stack.back(twoPanes = true)
        assertEquals(listOf<NavKey>(Home), stack)
    }

    @Test fun backOnAWideWindowLeavesThePlaylists() {
        val stack = mutableListOf<NavKey>(Home, Playlists, PlaylistDetail("p"))
        stack.back(twoPanes = true)
        assertEquals(listOf<NavKey>(Home), stack)
    }

    @Test fun backFromAListIsTheSameOnEveryWindow() {
        val narrow = mutableListOf<NavKey>(Home, Channels)
        narrow.back(twoPanes = false)
        val wide = mutableListOf<NavKey>(Home, Channels)
        wide.back(twoPanes = true)
        assertEquals(listOf<NavKey>(Home), narrow)
        assertEquals(narrow, wide)
    }

    @Test fun backKeepsScreensBelowTheList() {
        val stack = mutableListOf<NavKey>(Home, Search, ChannelDetail("a"))
        stack.back(twoPanes = true)
        assertEquals(listOf<NavKey>(Home, Search), stack)
    }

    @Test fun backAtTheRootDoesNothing() {
        val stack = mutableListOf<NavKey>(Home)
        stack.back(twoPanes = true)
        assertEquals(listOf<NavKey>(Home), stack)
    }
}
