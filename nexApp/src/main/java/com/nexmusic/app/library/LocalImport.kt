package com.nexmusic.app.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Songs imported from files on the phone get ids no YouTube id can have, so nothing mistakes them for one. */
const val LOCAL_PREFIX = "local-"

fun isLocalSong(videoId: String) = videoId.startsWith(LOCAL_PREFIX)

/**
 * Imports audio files (MP3, M4A, FLAC, OGG, WAV…) as downloaded songs.
 *
 * The player plays any song whose id is fully in the download cache straight from there, so an import copies
 * the file into that cache under a new id and adds a song row marked downloaded. From then on it is an ordinary
 * downloaded song: it plays offline, shows in Downloaded, and can go in playlists or be loved.
 */
@OptIn(UnstableApi::class)
class LocalImport(
    private val context: Context,
    private val songs: SongRepository,
    private val cache: SimpleCache,
) {
    /** Imports each file; returns how many made it. */
    suspend fun import(uris: List<Uri>): Int =
        withContext(Dispatchers.IO) {
            uris.count { uri ->
                runCatching { importOne(uri) }
                    .onFailure { Log.w(TAG, "import $uri", it) }
                    .isSuccess
            }
        }

    /** Removes an imported song's audio; its original file on the phone is untouched. */
    fun remove(videoId: String) {
        if (isLocalSong(videoId)) cache.removeResource(videoId)
        File(artDir, "$videoId.jpg").delete()
    }

    private val artDir get() = File(context.filesDir, "local_art").apply { mkdirs() }

    private suspend fun importOne(uri: Uri) {
        val id = LOCAL_PREFIX + UUID.randomUUID().toString().replace("-", "").take(16)
        // Copy into the download cache under the song's id, the same place a YouTube download lands.
        val source = CacheDataSource(cache, ContentDataSource(context))
        CacheWriter(source, DataSpec.Builder().setUri(uri).setKey(id).build(), null, null).cache()

        val meta = MediaMetadataRetriever()
        try {
            meta.setDataSource(context, uri)
            val art =
                meta.embeddedPicture?.let { bytes ->
                    File(artDir, "$id.jpg").apply { writeBytes(bytes) }.let { Uri.fromFile(it).toString() }
                }
            val seconds = (meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L).div(1000).toInt()
            val title = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: fileName(uri)
            val artist =
                (meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                    ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            songs
                .insertSong(
                    SongEntity(
                        videoId = id,
                        albumName = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
                        artistName = listOf(artist),
                        duration = "%d:%02d".format(seconds / 60, seconds % 60),
                        durationSeconds = seconds,
                        isAvailable = true,
                        isExplicit = false,
                        likeStatus = "INDIFFERENT",
                        thumbnails = art,
                        title = title,
                        videoType = "MUSIC_VIDEO_TYPE_ATV",
                        category = LOCAL_CATEGORY,
                        resultType = "Song",
                    ),
                ).first()
            songs.updateDownloadState(id, DownloadState.STATE_DOWNLOADED)
        } catch (e: Exception) {
            // A file the phone can't read as audio: don't leave half an import behind.
            cache.removeResource(id)
            throw e
        } finally {
            meta.release()
        }
    }

    private fun fileName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.substringBeforeLast('.') ?: "Imported Song"

    private companion object {
        const val TAG = "LocalImport"
        const val LOCAL_CATEGORY = "LOCAL_IMPORT"
    }
}
