package com.nexmusic.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SearchHistory
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.AlbumBrowse
import com.maxrave.domain.data.model.browse.artist.ArtistBrowse
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
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
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SearchRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toTrack
import com.maxrave.domain.utils.toAlbumEntity
import com.maxrave.domain.utils.toPlaylistEntity
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
fun SongRepository.recentlyPlayed(): Flow<List<SongEntity>> =
    getMostPlayedSongs().flatMapLatest { getAllSongs(100) }.map { list -> list.filter { it.totalPlayTime > 0 } }

/** A Made For You card: a list to play, or one seed song whose radio to start. */
data class Mix(
    val title: String,
    val subtitle: String,
    val art: String?,
    val tracks: List<Track>,
    val radio: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val home: HomeRepository,
    private val songs: SongRepository,
) : ViewModel() {
    // Lazy: only Home subscribes, so the New tab's instance never reads history or fetches mixes.
    val recentlyPlayed: StateFlow<List<SongEntity>> =
        songs.recentlyPlayed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Taste = the last 100 songs played, weighted by plays, plus loved songs (a heart counts as 3 plays). */
    val madeForYou: StateFlow<List<Mix>> =
        combine(recentlyPlayed, songs.getLikedSongs()) { played, liked ->
            (played + liked).distinctBy { it.videoId }.sortedByDescending { it.totalPlayTime + if (it.liked) 3 else 0 }
        }
            // Re-fetch only when the favourites change, not on every play.
            .distinctUntilChangedBy { top -> top.take(5).map { it.videoId } }
            .mapLatest { mixes(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val feed = MutableStateFlow<Load<List<HomeItem>>>(Load.Loading)
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
        loadInto(feed) {
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

class LibraryViewModel(
    songs: SongRepository,
    albums: AlbumRepository,
    playlists: PlaylistRepository,
    artists: ArtistRepository,
) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val liked: StateFlow<List<SongEntity>> = songs.getLikedSongs().state(emptyList())
    val downloaded: StateFlow<List<SongEntity>> = songs.getDownloadedSongs().map { it.orEmpty() }.state(emptyList())
    val mostPlayed: StateFlow<List<SongEntity>> = songs.getMostPlayedSongs().state(emptyList())
    val recentlyPlayed: StateFlow<List<SongEntity>> = songs.recentlyPlayed().state(emptyList())
    val savedAlbums: StateFlow<List<AlbumEntity>> = albums.getLikedAlbums().state(emptyList())
    val savedPlaylists: StateFlow<List<PlaylistEntity>> = playlists.getLikedPlaylists().state(emptyList())
    val followedArtists: StateFlow<List<ArtistEntity>> = artists.getFollowedArtists().state(emptyList())
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
        }
    }
}
