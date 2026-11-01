package com.nexmusic.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTest {
    @Test
    fun firstSignInIsAUnion() {
        val local = Snapshot(liked = setOf("a"), plays = mapOf("a" to 2), playlists = mapOf("p1" to PlaylistSync("Mine", listOf("a"))))
        val remote = Snapshot(liked = setOf("b"), plays = mapOf("a" to 5), playlists = mapOf("p2" to PlaylistSync("Theirs", listOf("b"))))
        val m = merge(Snapshot(), local, remote)
        assertEquals(setOf("a", "b"), m.liked)
        assertEquals(5L, m.plays["a"])
        assertEquals(setOf("p1", "p2"), m.playlists.keys)
    }

    @Test
    fun deletionsOnEitherSideTravel() {
        val base = Snapshot(liked = setOf("a", "b"), playlists = mapOf("p" to PlaylistSync("P", listOf("a"))), albums = mapOf("al" to SavedItem("Al", null, null)))
        // Unliked "a" and deleted the album here; the other device deleted the playlist and liked "c".
        val local = base.copy(liked = setOf("b"), albums = emptyMap())
        val remote = base.copy(liked = setOf("a", "b", "c"), playlists = emptyMap())
        val m = merge(base, local, remote)
        assertEquals(setOf("b", "c"), m.liked)
        assertEquals(emptySet<String>(), m.playlists.keys)
        assertEquals(emptySet<String>(), m.albums.keys)
    }

    @Test
    fun aPlaylistEditedHereWinsElseTheAccountsVersion() {
        val base = Snapshot(playlists = mapOf("p" to PlaylistSync("Old", listOf("a", "b")), "q" to PlaylistSync("Q", listOf("x"))))
        val local = base.copy(playlists = mapOf("p" to PlaylistSync("Renamed", listOf("b", "a")), "q" to PlaylistSync("Q", listOf("x"))))
        val remote = base.copy(playlists = mapOf("p" to PlaylistSync("Old", listOf("a", "b")), "q" to PlaylistSync("Q", listOf("x", "y"))))
        val m = merge(base, local, remote)
        assertEquals(PlaylistSync("Renamed", listOf("b", "a")), m.playlists["p"])
        assertEquals(PlaylistSync("Q", listOf("x", "y")), m.playlists["q"])
    }

    @Test
    fun aNewDeviceTakesTheAccountsTaste() {
        val account = TasteSync(listOf("Hindi"), emptyList(), "Dark")
        val m = merge(Snapshot(), Snapshot(taste = TasteSync(emptyList(), emptyList(), "System")), Snapshot(taste = account))
        assertEquals(account, m.taste)
    }
}
