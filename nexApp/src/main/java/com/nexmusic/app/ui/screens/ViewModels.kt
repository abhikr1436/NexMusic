package com.nexmusic.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SearchHistory
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.AlbumBrowse
import com.maxrave.domain.data.model.browse.artist.ArtistBrowse
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.data.model.home.HomeItem
import com.maxrave.domain.data.model.mood.Mood
import com.maxrave.domain.data.model.mood.moodmoments.MoodsMomentObject
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.data.model.searchResult.songs.SongsResult
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.HomeRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SongRepository
import com.nexmusic.app.taste.PickedArtist
import com.nexmusic.app.taste.TasteStore
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toTrack
import com.maxrave.domain.utils.toAlbumEntity
import com.maxrave.domain.utils.toPlaylistEntity
import com.maxrave.domain.utils.toSongEntity
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.hiRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Network screens: still loading, failed with a message, or loaded. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Failed(val message: String) : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>
}

private suspend fun <T> Flow<Resource<T>>.load(): Load<T> =
    when (val r = runCatching { first() }.getOrElse { return Load.Failed(it.message ?: "Something went wrong") }) {
        is Resource.Success -> r.data?.let { Load.Ready(it) } ?: Load.Failed("Nothing here")
        else -> Load.Failed(r.message ?: "Couldn't load")
    }

private fun <T> ViewModel.loadInto(
    target: MutableStateFlow<Load<T>>,
    source: () -> Flow<Resource<T>>,
) {
    target.value = Load.Loading
    viewModelScope.launch { target.value = source().load() }
}

/**
 * Every song played, last played first: a play stamps `inLibrary` and bumps `totalPlayTime`.
 * `getAllSongs` reads once, so Room's most-played Flow (which re-emits on any song-table write) re-runs it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun SongRepository.recentlyPlayed(): Flow<List<SongEntity>> = onSongChange { getAllSongs(100) }.map { list -> list.filter { it.totalPlayTime > 0 } }

/** Re-runs a one-shot song read whenever the song table changes (a like, a download, a play), using Room's most-played Flow as the signal. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SongRepository.onSongChange(read: () -> Flow<T>): Flow<T> = getMostPlayedSongs().flatMapLatest { read() }

/** A Made For You card: a list to play, or one seed song whose radio to start. */
data class Mix(
    val title: String,
    val subtitle: String,
    val art: String?,
    val tracks: List<Track>,
    val radio: Boolean = false,
)

/** A single or album on the New tab, from an artist the user has been playing. */
data class Release(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String,
    val art: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val home: HomeRepository,
    private val songs: SongRepository,
    private val artists: ArtistRepository,
    private val search: SearchRepository,
    private val taste: TasteStore,
) : ViewModel() {
    /** Artists picked on first launch, shown as a row on Home. */
    val yourArtists: StateFlow<List<PickedArtist>> = taste.artists

    /** "Popular in <language>": top playlists for up to three of the user's languages. */
    val popularIn: StateFlow<List<Pair<String, List<PlaylistsResult>>>> =
        taste.languages
            .mapLatest { langs ->
                coroutineScope {
                    langs.take(3).map { l ->
                        async { l to runCatching { search.getSearchDataPlaylist("top $l songs").first().data.orEmpty() }.getOrDefault(emptyList()).take(10) }
                    }.awaitAll()
                }.filter { it.second.isNotEmpty() }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Lazy: only Home subscribes, so the New tab's instance never reads history or fetches mixes.
    val recentlyPlayed: StateFlow<List<SongEntity>> =
        songs.recentlyPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Taste = the last 100 songs played, weighted by plays, plus loved songs (a heart counts as 3 plays). */
    private val favourites: Flow<List<SongEntity>> =
        combine(recentlyPlayed, songs.onSongChange { songs.getLikedSongs() }) { played, liked ->
            (played + liked).distinctBy { it.videoId }.sortedByDescending { it.totalPlayTime + if (it.liked) 3 else 0 }
        }

    val madeForYou: StateFlow<List<Mix>> =
        favourites
            // Re-fetch only when the favourites change, not on every play.
            .distinctUntilChangedBy { top -> top.take(5).map { it.videoId } }
            .mapLatest { mixes(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Newest singles and albums from the six artists played most in recent history; only the New tab subscribes. */
    val newForYou: StateFlow<List<Release>> =
        combine(recentlyPlayed, taste.artists) { played, picks ->
            // Artists the user picked come first, then the ones they actually play most.
            val fromPlays =
                played
                    .mapNotNull { s -> s.artistId?.firstOrNull { it.startsWith("UC") }?.let { it to s.totalPlayTime } }
                    .groupBy({ it.first }, { it.second })
                    .entries
                    .sortedByDescending { it.value.sum() }
                    .map { it.key }
            (picks.map { it.id } + fromPlays).distinct().take(8)
        }.distinctUntilChanged()
            .mapLatest { releasesOf(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun releasesOf(artistIds: List<String>): List<Release> {
        val perArtist =
            coroutineScope {
                artistIds.map { id ->
                    async {
                        val a = runCatching { artists.getArtistData(id).first().data }.getOrNull() ?: return@async emptyList()
                        // YouTube lists an artist's releases newest first.
                        a.singles?.results.orEmpty().take(4).map { Release(it.browseId, it.title, a.name, it.year, it.thumbnails.best()) } +
                            a.albums?.results.orEmpty().take(2).map { Release(it.browseId, it.title, a.name, it.year, it.thumbnails.best()) }
                    }
                }.awaitAll()
            }
        // Newest year first; within a year, take turns between artists so one doesn't fill the shelf.
        return perArtist
            .flatMap { it.withIndex() }
            .sortedWith(compareByDescending<IndexedValue<Release>> { it.value.year.take(4).toIntOrNull() ?: 0 }.thenBy { it.index })
            .map { it.value }
            .distinctBy { it.browseId }
            .take(24)
    }

    private suspend fun mixes(top: List<SongEntity>): List<Mix> {
        if (top.isEmpty()) return emptyList()
        val heard = top.map { it.videoId }.toSet()
        // Radio of each of the top 3 songs, interleaved, minus anything already played.
        val discovery =
            coroutineScope {
                top.take(3).map { s -> async { runCatching { songs.getRelatedData(s.videoId).firstOrNull()?.data?.first }.getOrNull().orEmpty() } }.awaitAll()
            }.flatMap { it.withIndex() }
                .sortedBy { it.index }
                .map { it.value }
                .filter { it.videoId !in heard }
                .distinctBy { it.videoId }
                .take(40)
        val stations =
            top
                .filter { !it.artistName.isNullOrEmpty() }
                .groupBy { it.artistName!!.first() }
                .entries
                .sortedByDescending { e -> e.value.sumOf { it.totalPlayTime } }
                .take(4)
                .map { (artist, list) -> Mix("$artist Station", "Based on your listening", list.first().thumbnails?.hiRes(), listOf(list.first().toTrack()), radio = true) }
        val replay = top.filter { it.totalPlayTime > 0 }.take(50)
        return listOfNotNull(
            discovery.takeIf { it.isNotEmpty() }?.let { Mix("Discovery Mix", "New music picked for you", it.first().thumbnails.best(), it) },
            replay.takeIf { it.isNotEmpty() }?.let { Mix("Replay", "Your most played", it.first().thumbnails?.hiRes(), it.map { s -> s.toTrack() }) },
        ) + stations
    }

    private val homeFeed = MutableStateFlow<Load<List<HomeItem>>>(Load.Loading)

    /**
     * Quick Picks from the user's taste, not YouTube's signed-out feed (which follows the region): radio of their top
     * songs, top songs of the artists they picked, and hits in their languages, taking turns. Null while loading.
     */
    private val quickPicks: StateFlow<List<Content>?> =
        combine(favourites.map { top -> top.take(3).map { it.videoId } }.distinctUntilChanged(), taste.artists, taste.languages) { seeds, picks, langs ->
            Triple(seeds, picks, langs)
        }.mapLatest { (seeds, picks, langs) -> tastePicks(seeds, picks, langs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** YouTube's Home feed with its Quick Picks swapped for [quickPicks]; YouTube's are kept only when there's no taste to go on. */
    val feed: StateFlow<Load<List<HomeItem>>> =
        combine(homeFeed, quickPicks) { f, picks ->
            if (f !is Load.Ready) return@combine f
            Load.Ready(
                f.value.mapNotNull { s ->
                    when {
                        !s.isQuickPicks() -> s
                        picks == null -> null // hidden until ours arrive, so YouTube's never flash up
                        picks.isEmpty() -> s
                        else -> s.copy(contents = picks, moreEndpoint = null, playAllEndpoint = null)
                    }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    private fun HomeItem.isQuickPicks() = itemsPerColumn != null && title.contains("quick pick", ignoreCase = true)

    private suspend fun tastePicks(
        seeds: List<String>,
        picks: List<PickedArtist>,
        langs: List<String>,
    ): List<Content> =
        coroutineScope {
            val radio = seeds.map { id -> async { runCatching { songs.getRelatedData(id).firstOrNull()?.data?.first }.getOrNull().orEmpty() } }
            val fromArtists =
                picks.shuffled().take(4).map { a ->
                    async { runCatching { artists.getArtistData(a.id).first().data?.songs?.results }.getOrNull().orEmpty().map { it.toTrack() } }
                }
            val fromLanguages =
                langs.take(2).map { l ->
                    async { runCatching { search.getSearchDataSong("top $l songs").first().data }.getOrNull().orEmpty().map { it.toTrack() } }
                }
            (radio + fromArtists + fromLanguages)
                .awaitAll()
                .flatMap { it.withIndex() }
                .sortedBy { it.index }
                .map { it.value }
                .filter { it.videoId !in seeds }
                .distinctBy { it.videoId }
                .take(20)
                .map { it.toContent() }
        }
    val newReleases = MutableStateFlow<Load<List<HomeItem>>>(Load.Loading)

    // Home and New each own an instance; each loads only the feed it shows.
    private var homeAsked = false
    private var newAsked = false

    fun ensureHome() {
        if (!homeAsked) refresh().also { homeAsked = true }
    }

    fun ensureNew() {
        if (!newAsked) refreshNew().also { newAsked = true }
    }

    fun refreshNew() = loadInto(newReleases) { home.getNewRelease("New Releases", "New Music Videos") }

    fun refresh() =
        loadInto(homeFeed) {
            home.getHomeData(viewString = "views", songString = "Song").map { r ->
                if (r is Resource.Success) Resource.Success(r.data!!.second) else Resource.Error(r.message ?: "Couldn't load")
            }
        }
}

enum class SearchScope(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

data class SearchResults(
    val songs: List<SongsResult> = emptyList(),
    val albums: List<AlbumsResult> = emptyList(),
    val artists: List<ArtistsResult> = emptyList(),
    val playlists: List<PlaylistsResult> = emptyList(),
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val search: SearchRepository,
    home: HomeRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val scope = MutableStateFlow(SearchScope.Songs)
    val categories = MutableStateFlow<Load<Mood>>(Load.Loading)
    val history = MutableStateFlow<List<String>>(emptyList())
    private val _results = MutableStateFlow<Load<SearchResults>?>(null)
    val results: StateFlow<Load<SearchResults>?> = _results.asStateFlow()

    /** YouTube Music's completions for what's typed so far. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<String>> =
        query
            .debounce(150)
            .mapLatest { q ->
                if (q.isBlank()) emptyList() else runCatching { search.getSuggestQuery(q.trim()).first().data?.queries.orEmpty() }.getOrDefault(emptyList()).take(6)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadInto(categories) { home.getMoodAndMomentsData() }
        loadHistory()
        viewModelScope.launch {
            combine(query.debounce(350), scope) { q, s -> q.trim() to s }.collectLatest { (q, s) ->
                if (q.isEmpty()) {
                    _results.value = null
                    return@collectLatest
                }
                _results.value = Load.Loading
                _results.value =
                    when (s) {
                        SearchScope.Songs -> search.getSearchDataSong(q).load().map { SearchResults(songs = it) }
                        SearchScope.Albums -> search.getSearchDataAlbum(q).load().map { SearchResults(albums = it) }
                        SearchScope.Artists -> search.getSearchDataArtist(q).load().map { SearchResults(artists = it) }
                        SearchScope.Playlists -> search.getSearchDataPlaylist(q).load().map { SearchResults(playlists = it) }
                    }
            }
        }
    }

    // shortcut: the DAO has no ORDER BY; REPLACE re-inserts a repeat with a new rowid, so rowid order is oldest first. Add ORDER BY rowid if that ever changes.
    private fun loadHistory() =
        viewModelScope.launch {
            history.value = search.getSearchHistory().firstOrNull().orEmpty().map { it.query }.reversed().take(10)
        }

    /** Remembers the current query, on the keyboard's Search key or a tapped result. */
    fun saveQuery() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            search.insertSearchHistory(SearchHistory(q)).first()
            loadHistory()
        }
    }

    fun clearHistory() =
        viewModelScope.launch {
            search.deleteSearchHistory()
            history.value = emptyList()
        }

    private fun <A, B> Load<A>.map(f: (A) -> B): Load<B> =
        when (this) {
            is Load.Ready -> Load.Ready(f(value))
            is Load.Failed -> this
            Load.Loading -> Load.Loading
        }
}

/**
 * Saved albums, playlists, artists and the user's own playlists are read once (not Room Flows),
 * so every write to them bumps this and the reads re-run.
 */
object LibraryChanges {
    val tick = MutableStateFlow(0)

    fun bump() = tick.update { it + 1 }
}

/** One playlist write at a time, across every screen's LibraryViewModel and account sync. */
internal val playlistWrites = Mutex()

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> reloading(read: () -> Flow<T>): Flow<T> = LibraryChanges.tick.flatMapLatest { read() }

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    songs: SongRepository,
    albums: AlbumRepository,
    playlists: PlaylistRepository,
    artists: ArtistRepository,
    private val local: LocalPlaylistRepository,
) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Playlists the user made, newest first. */
    val myPlaylists: StateFlow<List<LocalPlaylistEntity>> = reloading { local.getAllLocalPlaylists() }.map { it.sortedByDescending { p -> p.id } }.state(emptyList())

    fun tracksOf(id: Long): Flow<List<SongEntity>> = reloading { flow { emit(local.getFullPlaylistTracks(id)) } }

    /** Creates a playlist, then adds [first] to it when given (the "New Playlist…" row of Add to Playlist). */
    fun create(
        title: String,
        first: Track? = null,
    ) = viewModelScope.launch {
        local.insertLocalPlaylist(LocalPlaylistEntity(title = title.trim()), "").collect {}
        // insert hands back no id; the newest row is the one just made.
        val id = local.getAllLocalPlaylists().first().maxOfOrNull { it.id }
        if (first != null && id != null) addTrack(id, first) else LibraryChanges.bump()
    }

    fun add(
        id: Long,
        track: Track,
    ) = viewModelScope.launch { addTrack(id, track) }

    // Each write reads the track list and writes it back whole, so two at once would drop a song.
    private suspend fun addTrack(
        id: Long,
        track: Track,
    ) = playlistWrites.withLock {
        val playlist = local.getAllLocalPlaylists().first().firstOrNull { it.id == id } ?: return@withLock
        if (playlist.tracks?.contains(track.videoId) != true) {
            local.addTrackToLocalPlaylist(id, track.toSongEntity(), "", "", "").collect {}
            // The first song added gives the playlist its cover.
            if (playlist.thumbnail.isNullOrEmpty()) track.thumbnails.best()?.let { local.updateThumbnailLocalPlaylist(id, it, "").collect {} }
        }
        LibraryChanges.bump()
    }

    fun remove(
        id: Long,
        song: SongEntity,
    ) = viewModelScope.launch {
        playlistWrites.withLock {
            local.removeTrackFromLocalPlaylist(id, song, "", "", "").collect {}
            settle(id, local.getFullPlaylistTracks(id))
        }
    }

    /** Edit mode's Done: the new name, the songs kept in their new order, and the ones taken out. */
    fun saveEdits(
        id: Long,
        title: String,
        kept: List<SongEntity>,
        removed: List<SongEntity>,
    ) = viewModelScope.launch {
        playlistWrites.withLock {
            val current = local.getAllLocalPlaylists().first().firstOrNull { it.id == id } ?: return@withLock
            if (title.isNotBlank() && title != current.title) local.updateTitleLocalPlaylist(id, title.trim(), "", "", "").collect {}
            removed.forEach { local.removeTrackFromLocalPlaylist(id, it, "", "", "").collect {} }
            settle(id, kept)
        }
    }

    /** Writes [order] as positions 0..n-1, points the cover at the first song, and refreshes the lists. */
    private suspend fun settle(
        id: Long,
        order: List<SongEntity>,
    ) {
        // Absolute positions, so any order left by older writes is corrected too.
        local.setTrackOrder(id, order.map { it.videoId })
        // Empty once the last song goes: "" falls back to the placeholder art.
        local.updateThumbnailLocalPlaylist(id, order.firstOrNull()?.thumbnails.orEmpty(), "").collect {}
        LibraryChanges.bump()
    }

    fun delete(id: Long) =
        viewModelScope.launch {
            local.deleteLocalPlaylist(id, "").collect {}
            LibraryChanges.bump()
        }

    val liked: StateFlow<List<SongEntity>> = songs.onSongChange { songs.getLikedSongs() }.state(emptyList())
    val downloaded: StateFlow<List<SongEntity>> = songs.onSongChange { songs.getDownloadedSongs() }.map { it.orEmpty() }.state(emptyList())
    val mostPlayed: StateFlow<List<SongEntity>> = songs.getMostPlayedSongs().state(emptyList())
    val recentlyPlayed: StateFlow<List<SongEntity>> = songs.recentlyPlayed().state(emptyList())
    val savedAlbums: StateFlow<List<AlbumEntity>> = reloading { albums.getLikedAlbums() }.state(emptyList())
    val savedPlaylists: StateFlow<List<PlaylistEntity>> = reloading { playlists.getLikedPlaylists() }.state(emptyList())
    val followedArtists: StateFlow<List<ArtistEntity>> = reloading { artists.getFollowedArtists() }.state(emptyList())
}

/** Album, playlist, artist and category pages. Each page gets its own instance. */
class DetailViewModel(
    private val albums: AlbumRepository,
    private val playlists: PlaylistRepository,
    private val artists: ArtistRepository,
    private val home: HomeRepository,
) : ViewModel() {
    val album = MutableStateFlow<Load<AlbumBrowse>>(Load.Loading)
    val playlist = MutableStateFlow<Load<PlaylistBrowse>>(Load.Loading)
    val artist = MutableStateFlow<Load<ArtistBrowse>>(Load.Loading)
    val mood = MutableStateFlow<Load<MoodsMomentObject>>(Load.Loading)
    val inLibrary = MutableStateFlow(false)
    private var started = false

    private fun once(block: () -> Unit) {
        if (!started) {
            started = true
            block()
        }
    }

    private fun watchSaved(source: Flow<Boolean>) {
        viewModelScope.launch { source.collect { inLibrary.value = it } }
    }

    private var albumId: String? = null

    fun openAlbum(id: String) =
        once {
            albumId = id
            loadInto(album) { albums.getAlbumData(id) }
            watchSaved(albums.getAlbumAsFlow(id).map { it?.liked == true })
        }

    fun openPlaylist(id: String) =
        once {
            loadInto(playlist) { playlists.getFullPlaylistData(id, "views") }
            watchSaved(playlists.getPlaylist(id.removePrefix("VL")).map { it?.liked == true })
        }

    fun openArtist(id: String) =
        once {
            loadInto(artist) { artists.getArtistData(id) }
            watchSaved(artists.getArtistById(id).map { it?.followed == true })
        }

    fun openMood(params: String) = once { loadInto(mood) { home.getMoodData(params) } }

    fun toggleLibrary() {
        val save = !inLibrary.value
        // Playlist and artist reads are one-shot (not Room Flows), so flip the heart here rather than wait for a re-emit.
        inLibrary.value = save
        viewModelScope.launch {
            (album.value as? Load.Ready)?.value?.let { a ->
                val id = albumId ?: return@let
                if (albums.getAlbum(id).first() == null) albums.insertAlbum(a.toAlbumEntity(id)).first()
                albums.updateAlbumLiked(id, if (save) 1 else 0)
            }
            (playlist.value as? Load.Ready)?.value?.let { p ->
                val entity = p.toPlaylistEntity()
                if (playlists.getPlaylist(entity.id).first() == null) playlists.insertPlaylist(entity)
                playlists.updatePlaylistLiked(entity.id, if (save) 1 else 0)
            }
            (artist.value as? Load.Ready)?.value?.let { a ->
                val id = a.channelId ?: return@let
                if (artists.getArtistById(id).first() == null) {
                    artists.insertArtist(ArtistEntity(channelId = id, name = a.name, thumbnails = a.thumbnails?.lastOrNull()?.url))
                }
                artists.updateFollowedStatus(id, if (save) 1 else 0)
            }
            LibraryChanges.bump()
        }
    }
}

private fun Track.toContent() =
    Content(
        album = album,
        artists = artists,
        description = null,
        isExplicit = isExplicit,
        playlistId = null,
        browseId = null,
        thumbnails = thumbnails.orEmpty(),
        title = title,
        videoId = videoId,
        views = null,
        durationSeconds = durationSeconds,
        videoType = videoType,
    )
