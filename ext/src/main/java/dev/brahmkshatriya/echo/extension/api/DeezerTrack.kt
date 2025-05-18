package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

class DeezerTrack(private val deezerApi: DeezerApi) {

    suspend fun track(tracks: Array<Track>): JsonObject {
        return deezerApi.callApi(
            method = "song.getListData",
            paramsBuilder = {
                put("sng_ids", buildJsonArray { tracks.forEach { add(it.id) } })
            },
            np = true
        )
    }

    suspend fun getTracks(userId: String): JsonObject {
        return deezerApi.callApi(
            method = "favorite_song.getList",
            paramsBuilder = {
                put("user_id", userId)
                put("tab", "loved")
                put("nb", 10000)
                put("start", 0)
            },
            np = true
        )
    }

    suspend fun addFavoriteTrack(id: String) {
        deezerApi.callApi(
            method = "favorite_song.add",
            paramsBuilder = {
                put("SNG_ID", id)
            }
        )
    }

    suspend fun removeFavoriteTrack(id: String) {
        deezerApi.callApi(
            method = "favorite_song.remove",
            paramsBuilder = {
                put("SNG_ID", id)
            }
        )
    }
}