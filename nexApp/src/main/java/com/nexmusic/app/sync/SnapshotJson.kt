package com.nexmusic.app.sync

import org.json.JSONArray
import org.json.JSONObject

/** The JSON kept in the account's Supabase row (and the last-synced copy on the device). Unknown fields are ignored, so later versions can add more. */
object SnapshotJson {
    private const val VERSION = 1

    fun encode(s: Snapshot): String =
        JSONObject()
            .put("version", VERSION)
            .put("songs", JSONObject().apply { s.songs.values.forEach { put(it.videoId, song(it)) } })
            .put("liked", JSONArray(s.liked.toList()))
            .put("downloaded", JSONArray(s.downloaded.toList()))
            .put("plays", JSONObject(s.plays))
            .put("lastPlayed", JSONObject(s.lastPlayed))
            .put("playlists", JSONObject().apply { s.playlists.forEach { (id, p) -> put(id, JSONObject().put("title", p.title).put("tracks", JSONArray(p.tracks))) } })
            .put("albums", items(s.albums))
            .put("savedPlaylists", items(s.savedPlaylists))
            .put("artists", items(s.artists))
            .put("searches", JSONArray(s.searches))
            .apply {
                s.taste?.let { t ->
                    put(
                        "taste",
                        JSONObject()
                            .put("languages", JSONArray(t.languages))
                            .put("artists", JSONArray(t.artists.map { JSONArray(it) }))
                            .put("theme", t.theme)
                            .put("accent", t.accent)
                            .put("backdrop", t.backdrop),
                    )
                }
            }.toString()

    fun decode(text: String): Snapshot {
        val o = JSONObject(text)
        return Snapshot(
            songs = o.optJSONObject("songs").entries { id, v -> (v as JSONObject).toSong(id) },
            liked = o.optJSONArray("liked").strings().toSet(),
            downloaded = o.optJSONArray("downloaded").strings().toSet(),
            plays = o.optJSONObject("plays").entries { _, v -> (v as Number).toLong() },
            lastPlayed = o.optJSONObject("lastPlayed").entries { _, v -> (v as Number).toLong() },
            playlists = o.optJSONObject("playlists").entries { _, v -> (v as JSONObject).let { PlaylistSync(it.optString("title"), it.optJSONArray("tracks").strings()) } },
            albums = o.optJSONObject("albums").savedItems(),
            savedPlaylists = o.optJSONObject("savedPlaylists").savedItems(),
            artists = o.optJSONObject("artists").savedItems(),
            taste =
                o.optJSONObject("taste")?.let { t ->
                    TasteSync(
                        t.optJSONArray("languages").strings(),
                        t.optJSONArray("artists")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).strings() } }.orEmpty(),
                        t.optString("theme", "System"),
                        if (t.has("accent")) t.getLong("accent") else null,
                        t.optString("backdrop", "Classic"),
                    )
                },
            searches = o.optJSONArray("searches").strings(),
        )
    }

    private fun song(m: SongMeta) =
        JSONObject()
            .put("title", m.title)
            .put("artists", JSONArray(m.artists))
            .put("artistIds", JSONArray(m.artistIds))
            .put("album", m.album)
            .put("albumId", m.albumId)
            .put("thumb", m.thumb)
            .put("duration", m.duration)
            .put("durationSeconds", m.durationSeconds)

    private fun JSONObject.toSong(id: String) =
        SongMeta(
            videoId = id,
            title = optString("title"),
            artists = optJSONArray("artists").strings(),
            artistIds = optJSONArray("artistIds").strings(),
            album = optNullable("album"),
            albumId = optNullable("albumId"),
            thumb = optNullable("thumb"),
            duration = optString("duration"),
            durationSeconds = optInt("durationSeconds"),
        )

    private fun items(map: Map<String, SavedItem>) =
        JSONObject().apply { map.forEach { (id, i) -> put(id, JSONObject().put("title", i.title).put("subtitle", i.subtitle).put("thumb", i.thumb)) } }

    private fun JSONObject?.savedItems() = entries { _, v -> (v as JSONObject).let { SavedItem(it.optString("title"), it.optNullable("subtitle"), it.optNullable("thumb")) } }

    // JSONObject.put(name, null) drops the key, and optString then gives "": treat missing as null.
    private fun JSONObject.optNullable(key: String): String? = if (isNull(key)) null else optString(key)

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun <T> JSONObject?.entries(read: (String, Any) -> T): Map<String, T> =
        if (this == null) emptyMap() else keys().asSequence().associateWith { read(it, get(it)) }
}
