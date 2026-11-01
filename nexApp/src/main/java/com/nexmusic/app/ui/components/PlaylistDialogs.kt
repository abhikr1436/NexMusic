package com.nexmusic.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.browse.album.Track
import com.nexmusic.app.ui.icon.Add
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.screens.LibraryViewModel
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

/** Apple's "New Playlist" prompt: a name, then Create. */
@Composable
fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val create = {
        if (name.isNotBlank()) {
            onCreate(name.trim())
            onDismiss()
        }
    }
    GlassDialog(onDismiss, "New Playlist") {
        GlassTextField(name, { name = it }, "Playlist Name", onDone = create)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
            GlassButton("Create", Modifier.weight(1f), enabled = name.isNotBlank(), onClick = create)
        }
    }
}

/** Picks one of the user's playlists for [track], or makes a new one with it in. */
@Composable
fun AddToPlaylistDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    val vm: LibraryViewModel = koinViewModel()
    val playlists by vm.myPlaylists.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NewPlaylistDialog(onDismiss) { vm.create(it, track) }
        return
    }
    GlassDialog(onDismiss, "Add to Playlist") {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { PickerRow(null, "New Playlist…", isNew = true) { naming = true } }
            items(playlists, key = { it.id }) { p ->
                PickerRow(p.thumbnail, p.title) {
                    vm.add(p.id, track)
                    onDismiss()
                }
            }
        }
        GlassButton("Cancel", Modifier.fillMaxWidth().padding(top = 12.dp), onClick = onDismiss)
    }
}

@Composable
private fun PickerRow(
    art: String?,
    title: String,
    isNew: Boolean = false,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isNew) {
            Icon(NexIcons.Add, null, tint = Accent, modifier = Modifier.size(44.dp).padding(8.dp))
        } else {
            Artwork(art, Modifier.size(44.dp), RoundedCornerShape(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = NexType.Body, color = if (isNew) Accent else LocalNex.current.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
