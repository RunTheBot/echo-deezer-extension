package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeezerRadio(private val deezerApi: DeezerApi, private val json: Json) {

    suspend fun mix(id: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "song.getSearchTrackMix",
            params = buildJsonObject {
                put("sng_id", id)
                put("start_with_input_track", false)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun mixArtist(id: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "smart.getSmartRadio",
            params = buildJsonObject {
                put("art_id", id)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun radio(trackId: String, artistId: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "radio.getUpNext",
            params = buildJsonObject {
                put("art_id", artistId)
                put("limit", 10)
                put("sng_id", trackId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun flow(id: String, userId: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "radio.getUserRadio",
            params = buildJsonObject {
                if (id != "default") {
                    put("config_id", id)
                }
                put("user_id", userId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }
}