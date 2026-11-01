package com.nexmusic.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A signed-in Supabase user. [expiresAt] is epoch seconds. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

/**
 * Supabase Auth (email + password) and the user's row in `public.libraries`, which holds the library JSON.
 * Row Level Security lets each user read and write only their own row.
 * Plain REST over HttpURLConnection; five calls don't need a client library.
 */
object Supabase {
    private const val URL_BASE = "https://apbclqpgrzxquhbkdsuc.supabase.co"

    // The publishable key is public by design; RLS is what protects the data.
    private const val KEY = "sb_publishable_aHfZEJTNXu3j-6E0KezHLg_kCmzNoZ2"

    /** A new account. Null when the project wants the email confirmed first (no session yet). */
    suspend fun signUp(
        email: String,
        password: String,
    ): Session? {
        val json = JSONObject(request("POST", "/auth/v1/signup", body = credentials(email, password)))
        return if (json.has("access_token")) session(json) else null
    }

    suspend fun signIn(
        email: String,
        password: String,
    ): Session = session(JSONObject(request("POST", "/auth/v1/token?grant_type=password", body = credentials(email, password))))

    suspend fun refresh(refreshToken: String): Session =
        session(JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", refreshToken).toString())))

    /** The library JSON, or null when this account has never synced. */
    suspend fun read(token: String): String? {
        val rows = JSONArray(request("GET", "/rest/v1/libraries?select=data", token))
        return if (rows.length() == 0) null else rows.getJSONObject(0).getJSONObject("data").toString()
    }

    suspend fun write(
        token: String,
        userId: String,
        text: String,
    ) {
        val row = JSONObject().put("user_id", userId).put("data", JSONObject(text)).put("updated_at", java.time.Instant.now().toString())
        request("POST", "/rest/v1/libraries?on_conflict=user_id", token, row.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    private fun credentials(
        email: String,
        password: String,
    ) = JSONObject().put("email", email).put("password", password).toString()

    private fun session(json: JSONObject): Session {
        val user = json.getJSONObject("user")
        return Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = json.optLong("expires_at").takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)),
            userId = user.getString("id"),
            email = user.optString("email"),
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        token: String? = null,
        body: String? = null,
        prefer: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val c = URL(URL_BASE + path).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.setRequestProperty("apikey", KEY)
                if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
                if (prefer != null) c.setRequestProperty("Prefer", prefer)
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = c.responseCode
                if (code !in 200..299) {
                    val detail = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw SupabaseException(code, detail)
                }
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
}

class SupabaseException(
    val code: Int,
    detail: String,
) : IOException(
        // Auth errors carry a readable msg / error_description; show that, not the raw JSON.
        runCatching { JSONObject(detail).let { it.optString("msg").ifEmpty { it.optString("error_description") }.ifEmpty { it.optString("message") } } }
            .getOrNull()
            ?.ifEmpty { null } ?: "Server answered $code",
    )
