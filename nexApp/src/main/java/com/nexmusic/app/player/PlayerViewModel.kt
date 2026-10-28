package com.nexmusic.app.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioQueueId
import com.maxrave.domain.utils.toSongEntity
import com.nexmusic.app.ui.components.best
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One instance for the whole app: the mini player, Now Playing and every "play" button share it. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val handler: MediaPlayerHandler,
    private val songRepository: SongRepository,
    private val lyricsRepository: LyricsCanvasRepository,
    private val downloads: DownloadHandler,
) : ViewModel() {
    val nowPlaying = handler.nowPlayingState
    val control = handler.controlState
    val queue = handler.queueData
    val currentIndex = handler.currentSongIndex

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    /** [DownloadState] of the current song. */
    val downloadState: StateFlow<Int> =
        handler.nowPlayingState
            .distinctUntilChangedBy { it.mediaItem.mediaId }
            .flatMapLatest { downloadState(it.mediaItem.mediaId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState.STATE_NOT_DOWNLOADED)

    /** Live from the song's Room row, which the download service keeps up to date. No row means never downloaded. */
    fun downloadState(videoId: String): Flow<Int> = songRepository.getSongAsFlow(videoId).map { it?.downloadState ?: DownloadState.STATE_NOT_DOWNLOADED }

    init {
        viewModelScope.launch {
            handler.simpleMediaState.collect { state ->
                when (state) {
                    is SimpleMediaState.Progress -> {
                        _position.value = state.progress
                        if (_duration.value <= 0) _duration.value = handler.getPlayerDuration().coerceAtLeast(0)
                    }
                    is SimpleMediaState.Ready -> _duration.value = state.duration
                    is SimpleMediaState.Loading -> _duration.value = state.duration
                    is SimpleMediaState.Ended -> _position.value = 0
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            handler.nowPlayingState.distinctUntilChangedBy { it.mediaItem.mediaId }.collect { state ->
                _lyrics.value = null
                _duration.value = 0
                val id = state.mediaItem.mediaId
                if (id.isEmpty()) return@collect
                val meta = state.mediaItem.metadata
                _lyrics.value = fetchLyrics(id, meta.title.orEmpty(), meta.artist.orEmpty(), state.songEntity?.durationSeconds)
            }
        }
    }

    private suspend fun fetchLyrics(
        videoId: String,
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): Lyrics? {
        // LRCLIB first (synced, keyed by title/artist), then SimpMusic's community DB keyed by videoId.
        val lrclib = runCatching { lyricsRepository.getLrclibLyricsData(artist, title, durationSeconds).first() }.getOrNull()
        if (lrclib is Resource.Success && !lrclib.data?.lines.isNullOrEmpty()) return lrclib.data
        val community = runCatching { lyricsRepository.getSimpMusicLyrics(videoId).first() }.getOrNull()
        return (community as? Resource.Success)?.data?.takeIf { !it.lines.isNullOrEmpty() }
    }

    /** A single song starts its own radio, like tapping a song in Apple Music's Home. */
    fun playRadio(track: Track) {
        start(
            QueueData.Data(
                listTracks = arrayListOf(track),
                firstPlayedTrack = track,
                playlistId = "RDAMVM${track.videoId}",
                playlistName = "\"${track.title}\" Radio",
                playlistType = PlaylistType.RADIO,
                continuation = null,
            ),
            track,
            Config.SONG_CLICK,
            null,
        )
    }

    fun playList(
        tracks: List<Track>,
        index: Int = 0,
        name: String,
        playlistId: String?,
        type: PlaylistType = PlaylistType.PLAYLIST,
        shuffle: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val list = if (shuffle) tracks.shuffled() else tracks
        val i = if (shuffle) 0 else index.coerceIn(list.indices)
        start(
            QueueData.Data(
                listTracks = ArrayList(list),
                firstPlayedTrack = list[i],
                playlistId = playlistId,
                playlistName = name,
                playlistType = if (shuffle && type == PlaylistType.ALBUM) PlaylistType.PLAYLIST else type,
                continuation = null,
            ),
            list[i],
            if (type == PlaylistType.ALBUM) Config.ALBUM_CLICK else Config.PLAYLIST_CLICK,
            i,
        )
    }

    /** Mixes and radios ("RD…") have no page of their own; they just start. */
    fun playEndpoint(
        endpoint: YouTubeWatchEndpoint,
        name: String,
    ) {
        viewModelScope.launch {
            val res = songRepository.getRadioFromEndpoint(endpoint).first()
            val tracks = res.data?.first
            if (res !is Resource.Success || tracks.isNullOrEmpty()) return@launch
            val isRadio = endpoint.playlistId?.isRadioQueueId() == true
            start(
                QueueData.Data(
                    listTracks = ArrayList(tracks),
                    firstPlayedTrack = tracks.first(),
                    playlistId = endpoint.playlistId,
                    playlistName = name,
                    playlistType = if (isRadio) PlaylistType.RADIO else PlaylistType.PLAYLIST,
                    continuation = res.data?.second,
                ),
                tracks.first(),
                if (isRadio) Config.RADIO_CLICK else Config.PLAYLIST_CLICK,
                0,
            )
        }
    }

    private fun start(
        data: QueueData.Data,
        track: Track,
        type: String,
        index: Int?,
    ) {
        handler.reset()
        handler.setQueueData(data)
        viewModelScope.launch { handler.loadMediaItem(track, type, index) }
    }

    fun playNext(track: Track) = viewModelScope.launch { handler.playNext(track) }

    fun playQueueItem(index: Int) = handler.playMediaItemInMediaSource(index)

    private fun send(event: PlayerEvent) = viewModelScope.launch { handler.onPlayerEvent(event) }

    fun playPause() = send(PlayerEvent.PlayPause)

    fun next() = send(PlayerEvent.Next)

    fun previous() = send(PlayerEvent.Previous)

    fun shuffle() = send(PlayerEvent.Shuffle)

    fun repeat() = send(PlayerEvent.Repeat)

    fun toggleLike() = send(PlayerEvent.ToggleLike)

    /** Downloads [track], or removes it once downloaded. A download in flight ([state] preparing/downloading) is left alone. */
    fun toggleDownload(
        track: Track,
        state: Int,
    ) = viewModelScope.launch {
        when (state) {
            DownloadState.STATE_NOT_DOWNLOADED -> {
                // The download service records progress on the song's row, so a song never played needs one first (insert ignores an existing row).
                songRepository.insertSong(track.toSongEntity()).first()
                songRepository.updateDownloadState(track.videoId, DownloadState.STATE_PREPARING)
                downloads.downloadTrack(track.videoId, track.title, track.thumbnails.best().orEmpty())
            }
            DownloadState.STATE_DOWNLOADED -> {
                downloads.removeDownload(track.videoId)
                songRepository.updateDownloadState(track.videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
        }
    }

    fun seekTo(fraction: Float) {
        _position.value = (_duration.value * fraction).toLong()
        send(PlayerEvent.UpdateProgress(fraction * 100f))
    }
}
