package com.nexmusic.app.sync

/** Enough of a song to rebuild its row on another device. */
data class SongMeta(
    val videoId: String,
    val title: String,
    val artists: List<String>,
    val artistIds: List<String>,
    val album: String?,
    val albumId: String?,
    val thumb: String?,
    val duration: String,
    val durationSeconds: Int,
)

/** A playlist the user made; keyed in [Snapshot.playlists] by an id shared across devices. */
data class PlaylistSync(
    val title: String,
    val tracks: List<String>,
)

/** A saved album, saved YouTube playlist or followed artist: what its row needs. */
data class SavedItem(
    val title: String,
    val subtitle: String?,
    val thumb: String?,
)

data class TasteSync(
    val languages: List<String>,
    val artists: List<List<String>>,
    val theme: String,
    /** Appearance: accent ARGB (null = default) and background name. */
    val accent: Long? = null,
    val backdrop: String = "Classic",
)

/** Everything an account carries between devices. Audio files stay on each device; [downloaded] is the list to fetch again. */
data class Snapshot(
    val songs: Map<String, SongMeta> = emptyMap(),
    val liked: Set<String> = emptySet(),
    val downloaded: Set<String> = emptySet(),
    val plays: Map<String, Long> = emptyMap(),
    /** When each song was last played, epoch millis; orders Recently Played. */
    val lastPlayed: Map<String, Long> = emptyMap(),
    val playlists: Map<String, PlaylistSync> = emptyMap(),
    val albums: Map<String, SavedItem> = emptyMap(),
    val savedPlaylists: Map<String, SavedItem> = emptyMap(),
    val artists: Map<String, SavedItem> = emptyMap(),
    val taste: TasteSync? = null,
    val searches: List<String> = emptyList(),
)

/**
 * Three-way merge: [base] is what both sides agreed on at the last sync.
 * Something added on either side is kept and something removed on either side goes, so deletions travel too.
 * With an empty base (first sign-in on a device) it is a plain union.
 */
fun merge(
    base: Snapshot,
    local: Snapshot,
    remote: Snapshot,
): Snapshot {
    val playlistIds = keys3(base.playlists.keys, local.playlists.keys, remote.playlists.keys)
    return Snapshot(
        songs = remote.songs + local.songs,
        liked = keys3(base.liked, local.liked, remote.liked),
        downloaded = keys3(base.downloaded, local.downloaded, remote.downloaded),
        plays = maxOf(local.plays, remote.plays),
        lastPlayed = maxOf(local.lastPlayed, remote.lastPlayed),
        // A playlist edited here wins; otherwise take the account's version.
        playlists = playlistIds.associateWith { id -> local.playlists[id]?.takeIf { it != base.playlists[id] } ?: remote.playlists[id] ?: local.playlists.getValue(id) },
        albums = map3(base.albums, local.albums, remote.albums),
        savedPlaylists = map3(base.savedPlaylists, local.savedPlaylists, remote.savedPlaylists),
        artists = map3(base.artists, local.artists, remote.artists),
        // A new device takes the account's taste; after that, whichever side changed it.
        taste = if (base.taste == null) remote.taste ?: local.taste else local.taste.takeIf { it != base.taste } ?: remote.taste,
        searches = keys3(base.searches.toSet(), local.searches.toSet(), remote.searches.toSet()).let { keep -> (local.searches + remote.searches).distinct().filter { it in keep } },
    )
}

/** Kept: on both sides, or newly added on one side. Gone: removed on either side since [base]. */
internal fun <K> keys3(
    base: Set<K>,
    local: Set<K>,
    remote: Set<K>,
): Set<K> = (local intersect remote) + (local - base) + (remote - base)

private fun <K, V> map3(
    base: Map<K, V>,
    local: Map<K, V>,
    remote: Map<K, V>,
): Map<K, V> = keys3(base.keys, local.keys, remote.keys).associateWith { local[it] ?: remote.getValue(it) }

private fun maxOf(
    a: Map<String, Long>,
    b: Map<String, Long>,
): Map<String, Long> = (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0, b[it] ?: 0) }
