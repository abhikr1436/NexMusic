package org.simpmusic.lyrics.models.response

import kotlinx.serialization.Serializable

@Serializable
data class LrclibObject(
    val id: Int,
    val name: String,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    // LRCLIB returns null for some entries; one such entry used to fail the whole search.
    val duration: Float?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)