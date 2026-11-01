package com.nexmusic.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexmusic.app.sync.AccountSync
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.components.CapsuleButton
import com.nexmusic.app.ui.components.Destructive
import com.nexmusic.app.ui.components.GlassButton
import com.nexmusic.app.ui.components.GlassDialog
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.icon.AccountCircle
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.compose.koinInject

/** Library › Account: optional NexMusic account (email + password) that keeps the library in step across devices. */
@Composable
fun AccountScreen() {
    val sync: AccountSync = koinInject()
    val state by sync.state.collectAsStateWithLifecycle()
    val nex = LocalNex.current
    var emailInput by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = BottomChromePadding)) {
        BackBar()
        LargeTitle("Account")
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val email = state.email
            if (email == null) {
                Icon(NexIcons.AccountCircle, null, tint = nex.tertiaryLabel, modifier = Modifier.padding(top = 24.dp).size(96.dp))
                Text("Your library, everywhere", style = NexType.Title2, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Sign in to keep your playlists, loved songs, saved albums and artists, downloads, appearance and listening history on every device you use.",
                    style = NexType.Subhead,
                    color = nex.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
                AccountField(emailInput, { emailInput = it }, "Email", KeyboardType.Email, ImeAction.Next)
                Spacer(Modifier.height(10.dp))
                AccountField(password, { password = it }, if (creating) "Password (6+ characters)" else "Password", KeyboardType.Password, ImeAction.Done)
                Spacer(Modifier.height(16.dp))
                val ready = emailInput.contains('@') && password.length >= 6
                val label =
                    when {
                        state.syncing -> if (creating) "Creating Account…" else "Signing In…"
                        creating -> "Create Account"
                        else -> "Sign In"
                    }
                CapsuleButton(NexIcons.AccountCircle, label, Modifier.fillMaxWidth()) {
                    if (!state.syncing && ready) sync.signIn(emailInput, password, creating)
                }
                TextButton(onClick = { creating = !creating }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (creating) "Have an account? Sign In" else "New here? Create Account", style = NexType.Body, color = Accent)
                }
                state.notice?.let { Text(it, style = NexType.Footnote, color = nex.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
                Text("Optional. Everything works without an account.", style = NexType.Footnote, color = nex.tertiaryLabel, modifier = Modifier.padding(top = 12.dp))
            } else {
                Icon(NexIcons.AccountCircle, null, tint = Accent, modifier = Modifier.padding(top = 16.dp).size(96.dp))
                Text(state.name ?: email, style = NexType.Title2, modifier = Modifier.padding(top = 14.dp))
                if (state.name != null) Text(email, style = NexType.Subhead, color = nex.secondaryLabel)
                Spacer(Modifier.height(20.dp))
                Text(
                    when {
                        state.syncing -> "Syncing…"
                        state.lastSynced > 0 -> "Last synced " + DateUtils.getRelativeTimeSpanString(state.lastSynced, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                        else -> "Not synced yet"
                    },
                    style = NexType.Subhead,
                    color = nex.secondaryLabel,
                )
                Text(
                    "Playlists, loves, saved items and history sync when the app opens and closes. Songs downloaded on another device download here on Wi-Fi.",
                    style = NexType.Footnote,
                    color = nex.tertiaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                CapsuleButton(NexIcons.AccountCircle, "Sync Now", Modifier.fillMaxWidth()) { sync.syncNow() }
                TextButton(onClick = { confirmSignOut = true }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Sign Out", style = NexType.Body, color = Destructive)
                }
            }
            state.error?.let { Text(it, style = NexType.Footnote, color = Destructive, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)) }
        }
    }

    if (confirmSignOut) {
        GlassDialog({ confirmSignOut = false }, "Sign Out?", "Your library stays on this device and in your account. This device just stops syncing.") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", Modifier.weight(1f)) { confirmSignOut = false }
                GlassButton("Sign Out", Modifier.weight(1f), color = Destructive) {
                    confirmSignOut = false
                    sync.signOut()
                }
            }
        }
    }
}

/** A plain rounded input for the sign-in form (the glass field is made for overlays). */
@Composable
private fun AccountField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    type: KeyboardType,
    ime: ImeAction,
) {
    val nex = LocalNex.current
    BasicTextField(
        value,
        onValueChange,
        singleLine = true,
        textStyle = NexType.Body.copy(color = nex.label),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ime, autoCorrectEnabled = false),
        visualTransformation = if (type == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(nex.fill, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = NexType.Body, color = nex.tertiaryLabel)
                field()
            }
        },
    )
}

/** The account row at the top of Library: who's signed in, or an invitation to. */
@Composable
fun AccountRow(onClick: () -> Unit) {
    val sync: AccountSync = koinInject()
    val state by sync.state.collectAsStateWithLifecycle()
    com.nexmusic.app.ui.components.ListLink(NexIcons.AccountCircle, state.name ?: state.email ?: "Sign In to Sync", onClick = onClick)
}
