package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeezerTrack(private val deezerApi: DeezerApi, private val json: Json) {

    suspend fun track(tracks: Array<Track>): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "song.getListData",
            params = buildJsonObject {
                put("sng_ids", buildJsonArray { tracks.forEach { add(it.id) } })
            },
            np = true
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getTracks(userId: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "favorite_song.getList",
            params = buildJsonObject {
                put("user_id", userId)
                put("tab", "loved")
                put("nb", 10000)
                put("start", 0)
            },
            np = true
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun addFavoriteTrack(id: String) {
        deezerApi.callApi(
            method = "favorite_song.add",
            params = buildJsonObject {
                put("SNG_ID", id)
            }
        )
    }

    suspend fun removeFavoriteTrack(id: String) {
        deezerApi.callApi(
            method = "favorite_song.remove",
            params = buildJsonObject {
                put("SNG_ID", id)
            }
        )
    }
}