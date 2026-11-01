package com.nexmusic.app.sync

import android.content.Context
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SearchHistory
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SongRepository
import com.nexmusic.app.library.isLocalSong
import com.nexmusic.app.taste.PickedArtist
import com.nexmusic.app.taste.TasteStore
import com.nexmusic.app.taste.ThemeMode
import com.nexmusic.app.ui.screens.LibraryChanges
import com.nexmusic.app.ui.theme.Backdrop
import com.nexmusic.app.ui.screens.playlistWrites
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/** Reads this device's library as a [Snapshot] and writes a merged one back, touching only what differs. */
class LocalLibrary(
    context: Context,
    private val songs: SongRepository,
    private val albums: AlbumRepository,
    private val playlists: PlaylistRepository,
    private val artists: ArtistRepository,
    private val local: LocalPlaylistRepository,
    private val search: SearchRepository,
    private val downloads: DownloadHandler,
    private val taste: TasteStore,
) {
    private val prefs = context.getSharedPreferences("sync_ids", Context.MODE_PRIVATE)

    /** Songs the account has downloaded that this device still has to fetch (on Wi-Fi). */
    var pendingDownloads: Set<String>
        get() = prefs.getStringSet(PENDING, null).orEmpty()
        set(value) = prefs.edit().putStringSet(PENDING, value).apply()

    /** Local playlist id → the id it has in the account. A local id means nothing on another device. */
    private fun syncIdOf(localId: Long): String =
        prefs.getString("$PL$localId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("$PL$localId", it).apply() }

    private fun localIdOf(syncId: String): Long? =
        prefs.all.entries.firstOrNull { it.key.startsWith(PL) && it.value == syncId }?.key?.removePrefix(PL)?.toLongOrNull()

    /** Forgets the playlist ids, so a later sign-in (maybe another account) starts clean. */
    fun forget() = prefs.edit().clear().apply()

    suspend fun read(): Snapshot {
        val metas = mutableMapOf<String, SongMeta>()
        // Imported files exist only on this phone; another device couldn't play them, so they stay out of the account.
        fun note(list: List<SongEntity>) = list.filterNot { isLocalSong(it.videoId) }.onEach { metas[it.videoId] = it.toMeta() }

        val liked = note(songs.getLikedSongs().first()).map { it.videoId }
        val onDevice =
            note(songs.getDownloadedSongs().first().orEmpty() + songs.getDownloadingSongs().first().orEmpty() + songs.getPreparingSongs().first())
                .map { it.videoId }
        val played = note(songs.getAllSongs(PLAYED_LIMIT).first().filter { it.totalPlayTime > 0 })
        val mine =
            local.getAllLocalPlaylists().first().associate { p ->
                syncIdOf(p.id) to PlaylistSync(p.title, note(local.getFullPlaylistTracks(p.id)).map { it.videoId })
            }
        val pending = pendingDownloads
        // A pending download's row may be missing here; keep the account's copy of its details.
        return Snapshot(
            songs = metas,
            liked = liked.toSet(),
            downloaded = onDevice.toSet() + pending,
            plays = played.associate { it.videoId to it.totalPlayTime },
            lastPlayed = played.associate { it.videoId to it.inLibrary.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() },
            playlists = mine,
            albums = albums.getLikedAlbums().first().associate { it.browseId to SavedItem(it.title, it.artistName?.joinToString(), it.thumbnails) },
            savedPlaylists = playlists.getLikedPlaylists().first().associate { it.id to SavedItem(it.title, it.author, it.thumbnails) },
            artists = artists.getFollowedArtists().first().associate { it.channelId to SavedItem(it.name, null, it.thumbnails) },
            taste =
                if (taste.onboarded.value) {
                    TasteSync(
                        taste.languages.value,
                        taste.artists.value.map { listOf(it.id, it.name, it.thumb.orEmpty()) },
                        taste.theme.value.name,
                        taste.accent.value,
                        taste.backdrop.value.name,
                    )
                } else {
                    null
                },
            searches = search.getSearchHistory().first().map { it.query },
        )
    }

    /** Makes this device match [target], given that it currently holds [current]. */
    suspend fun apply(
        target: Snapshot,
        current: Snapshot,
    ) {
        var changed = false
        suspend fun ensureSong(id: String): SongEntity? {
            val meta = target.songs[id] ?: current.songs[id] ?: return null
            // Insert ignores a row that's already there.
            return meta.toEntity().also { songs.insertSong(it).first() }
        }

        for (id in target.liked - current.liked) if (ensureSong(id) != null) songs.updateLikeStatus(id, 1)
        for (id in current.liked - target.liked) songs.updateLikeStatus(id, 0)
        changed = changed || target.liked != current.liked

        for ((id, count) in target.plays) {
            if (count <= (current.plays[id] ?: 0)) continue
            if (ensureSong(id) == null) continue
            songs.setTotalPlayTime(id, count)
            target.lastPlayed[id]?.let { songs.setInLibrary(id, Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault())) }
            changed = true
        }

        if (target.playlists != current.playlists) {
            playlistWrites.withLock { applyPlaylists(target, current, ::ensureSong) }
            changed = true
        }

        for ((id, a) in target.albums - current.albums.keys) {
            if (albums.getAlbum(id).first() == null) {
                albums.insertAlbum(
                    AlbumEntity(
                        browseId = id,
                        artistName = a.subtitle?.let { listOf(it) },
                        audioPlaylistId = "",
                        description = "",
                        duration = null,
                        durationSeconds = 0,
                        thumbnails = a.thumb,
                        title = a.title,
                        trackCount = 0,
                        type = "Album",
                        year = null,
                    ),
                ).first()
            }
            albums.updateAlbumLiked(id, 1)
        }
        for (id in current.albums.keys - target.albums.keys) albums.updateAlbumLiked(id, 0)

        for ((id, p) in target.savedPlaylists - current.savedPlaylists.keys) {
            if (playlists.getPlaylist(id).first() == null) playlists.insertPlaylist(PlaylistEntity(id = id, title = p.title, author = p.subtitle, thumbnails = p.thumb.orEmpty()))
            playlists.updatePlaylistLiked(id, 1)
        }
        for (id in current.savedPlaylists.keys - target.savedPlaylists.keys) playlists.updatePlaylistLiked(id, 0)

        for ((id, a) in target.artists - current.artists.keys) {
            if (artists.getArtistById(id).first() == null) artists.insertArtist(ArtistEntity(channelId = id, name = a.title, thumbnails = a.thumb))
            artists.updateFollowedStatus(id, 1)
        }
        for (id in current.artists.keys - target.artists.keys) artists.updateFollowedStatus(id, 0)
        changed = changed || target.albums.keys != current.albums.keys || target.savedPlaylists.keys != current.savedPlaylists.keys || target.artists.keys != current.artists.keys

        target.taste?.takeIf { it != current.taste }?.let { t ->
            taste.save(
                t.languages,
                t.artists.mapNotNull { it.takeIf { f -> f.size == 3 }?.let { (id, name, thumb) -> PickedArtist(id, name, thumb.ifEmpty { null }) } },
                runCatching { ThemeMode.valueOf(t.theme) }.getOrDefault(ThemeMode.System),
            )
            t.accent?.let(taste::setAccent)
            runCatching { Backdrop.valueOf(t.backdrop) }.getOrNull()?.let(taste::setBackdrop)
        }

        if (target.searches != current.searches) {
            // History has no single-row delete: when something went, write it out again.
            val add = if ((current.searches - target.searches.toSet()).isNotEmpty()) target.searches.also { search.deleteSearchHistory() } else target.searches - current.searches.toSet()
            add.forEach { search.insertSearchHistory(SearchHistory(it)).first() }
        }

        val fetch = target.downloaded - current.downloaded
        val drop = current.downloaded - target.downloaded
        if (fetch.isNotEmpty() || drop.isNotEmpty()) {
            fetch.forEach { ensureSong(it) }
            for (id in drop - pendingDownloads) {
                downloads.removeDownload(id)
                songs.updateDownloadState(id, DownloadState.STATE_NOT_DOWNLOADED)
            }
            pendingDownloads = pendingDownloads - drop + fetch
        }

        // The library lists read once; re-run them so the synced items show without a restart.
        if (changed) LibraryChanges.bump()
    }

    private suspend fun applyPlaylists(
        target: Snapshot,
        current: Snapshot,
        ensureSong: suspend (String) -> SongEntity?,
    ) {
        for (syncId in current.playlists.keys - target.playlists.keys) {
            val id = localIdOf(syncId) ?: continue
            local.deleteLocalPlaylist(id, "").collect {}
            prefs.edit().remove("$PL$id").apply()
        }
        for ((syncId, want) in target.playlists) {
            if (current.playlists[syncId] == want) continue
            val id =
                localIdOf(syncId) ?: run {
                    local.insertLocalPlaylist(LocalPlaylistEntity(title = want.title), "").collect {}
                    // Insert hands back no id; the newest row is the one just made.
                    val made = local.getAllLocalPlaylists().first().maxOfOrNull { it.id } ?: return@run null
                    prefs.edit().putString("$PL$made", syncId).apply()
                    made
                } ?: continue
            if (current.playlists[syncId]?.title != want.title) local.updateTitleLocalPlaylist(id, want.title, "", "", "").collect {}
            val have = local.getFullPlaylistTracks(id)
            // This phone's imported songs aren't in the account's copy; keep them, at the end.
            val imported = have.map { it.videoId }.filter(::isLocalSong)
            have.filter { it.videoId !in want.tracks && !isLocalSong(it.videoId) }.forEach { local.removeTrackFromLocalPlaylist(id, it, "", "", "").collect {} }
            val haveIds = have.map { it.videoId }.toSet()
            for (v in want.tracks) if (v !in haveIds) ensureSong(v)?.let { local.addTrackToLocalPlaylist(id, it, "", "", "").collect {} }
            val order = want.tracks.filter { v -> v in haveIds || target.songs.containsKey(v) || current.songs.containsKey(v) } + imported
            local.setTrackOrder(id, order)
            local.updateThumbnailLocalPlaylist(id, order.firstOrNull()?.let { (target.songs[it] ?: current.songs[it])?.thumb }.orEmpty(), "").collect {}
        }
    }

    private companion object {
        const val PL = "pl."
        const val PENDING = "pending"
        const val PLAYED_LIMIT = 500
    }
}

private fun SongEntity.toMeta() =
    SongMeta(videoId, title, artistName.orEmpty(), artistId.orEmpty(), albumName, albumId, thumbnails, duration, durationSeconds)

private fun SongMeta.toEntity() =
    SongEntity(
        videoId = videoId,
        albumId = albumId,
        albumName = album,
        artistId = artistIds,
        artistName = artists,
        duration = duration,
        durationSeconds = durationSeconds,
        isAvailable = true,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        thumbnails = thumb,
        title = title,
        videoType = "MUSIC_VIDEO_TYPE_ATV",
        category = null,
        resultType = "Song",
    )
