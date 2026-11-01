package com.areyouasleep.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.ds: DataStore<Preferences> by preferencesDataStore(name = "sleep_guard")

object GuardStore {

    private const val MAX_BOOKMARKS = 200

    private val K_ARMED = intPreferencesKey("armed_minutes")
    private val K_RESUME = intPreferencesKey("resume_minutes")
    private val K_EARPHONE = booleanPreferencesKey("earphone_trigger")
    private val K_BOOKMARKS = stringPreferencesKey("bookmarks")
    private val K_SNAPSHOT = stringPreferencesKey("last_snapshot")

    private fun DataStore<Preferences>.safe(): Flow<Preferences> = data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    fun settings(context: Context): Flow<GuardSettings> = context.ds.safe().map { p ->
        GuardSettings(
            armedMinutes = p[K_ARMED] ?: 15,
            resumeMinutes = p[K_RESUME] ?: 5,
            earphoneTrigger = p[K_EARPHONE] ?: true,
        )
    }

    suspend fun currentSettings(context: Context): GuardSettings = settings(context).first()

    suspend fun updateSettings(context: Context, change: (GuardSettings) -> GuardSettings) {
        val next = change(currentSettings(context))
        context.ds.edit { p ->
            p[K_ARMED] = next.armedMinutes.coerceIn(1, 120)
            p[K_RESUME] = next.resumeMinutes.coerceIn(1, 30)
            p[K_EARPHONE] = next.earphoneTrigger
        }
    }

    fun bookmarks(context: Context): Flow<List<Bookmark>> = context.ds.safe().map { p ->
        decodeBookmarks(p[K_BOOKMARKS])
    }

    /** 播放过程中每 30 秒写一次，进程被杀也能知道听到哪儿了 */
    suspend fun saveSnapshot(context: Context, np: NowPlaying) {
        context.ds.edit { p -> p[K_SNAPSHOT] = np.toJson().toString() }
    }

    fun snapshot(context: Context): Flow<NowPlaying?> = context.ds.safe().map { p ->
        p[K_SNAPSHOT]?.let { decodeNowPlaying(it) }
    }

    suspend fun addBookmark(context: Context, bookmark: Bookmark) {
        context.ds.edit { p ->
            val list = decodeBookmarks(p[K_BOOKMARKS])
            val next = (listOf(bookmark) + list).take(MAX_BOOKMARKS)
            p[K_BOOKMARKS] = encodeBookmarks(next)
        }
    }

    suspend fun markResumed(context: Context, at: Long) {
        context.ds.edit { p ->
            val list = decodeBookmarks(p[K_BOOKMARKS])
            val next = list.map { if (it.at == at) it.copy(resumed = true) else it }
            p[K_BOOKMARKS] = encodeBookmarks(next)
        }
    }

    suspend fun clearBookmarks(context: Context) {
        context.ds.edit { it.remove(K_BOOKMARKS) }
    }

    // ---- JSON ----

    private fun encodeBookmarks(list: List<Bookmark>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun decodeBookmarks(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(it.toBookmark()) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Bookmark.toJson(): JSONObject = JSONObject().apply {
        put("at", at)
        put("pkg", packageName)
        put("app", appLabel)
        put("title", title)
        put("sub", subtitle)
        put("pos", positionMs)
        put("dur", durationMs)
        put("resumed", resumed)
    }

    private fun JSONObject.toBookmark(): Bookmark = Bookmark(
        at = optLong("at"),
        packageName = optString("pkg"),
        appLabel = optString("app"),
        title = optString("title"),
        subtitle = optString("sub"),
        positionMs = optLong("pos"),
        durationMs = optLong("dur"),
        resumed = optBoolean("resumed"),
    )

    private fun NowPlaying.toJson(): JSONObject = JSONObject().apply {
        put("pkg", packageName)
        put("app", appLabel)
        put("title", title)
        put("sub", subtitle)
        put("pos", positionMs)
        put("dur", durationMs)
        put("at", updatedAt)
    }

    private fun decodeNowPlaying(raw: String): NowPlaying? = runCatching {
        val o = JSONObject(raw)
        NowPlaying(
            packageName = o.optString("pkg"),
            appLabel = o.optString("app"),
            title = o.optString("title"),
            subtitle = o.optString("sub"),
            positionMs = o.optLong("pos"),
            durationMs = o.optLong("dur"),
            updatedAt = o.optLong("at"),
        )
    }.getOrNull()
}
