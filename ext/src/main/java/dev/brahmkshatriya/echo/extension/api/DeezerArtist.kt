package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DeezerArtist(private val deezerApi: DeezerApi, private val json: Json) {

    suspend fun artist(id: String, language: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "deezer.pageArtist",
            params = buildJsonObject {
                put("art_id", id)
                put ("lang", language.substringBefore("-"))
            }
        )
        return withContext(Dispatchers.Default) {
            json.decodeFromString<JsonObject>(jsonData)
        }
    }

    suspend fun getArtists(userId: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("nb", 40)
                put ("tab", "artists")
                put("user_id", userId)
            }
        )
        return withContext(Dispatchers.Default) {
            json.decodeFromString<JsonObject>(jsonData)
        }
    }

    suspend fun followArtist(id: String) {
        deezerApi.callApi(
            method = "artist.addFavorite",
            params = buildJsonObject {
                put("ART_ID", id)
                putJsonObject("CTXT") {
                    put("id", id)
                    put("t", "artist_smartradio")
                }
            }
        )
    }

    suspend fun unfollowArtist(id: String) {
        deezerApi.callApi(
            method = "artist.deleteFavorite",
            params = buildJsonObject {
                put("ART_ID", id)
                putJsonObject("CTXT") {
                    put("id", id)
                    put("t", "artist_smartradio")
                }
            }
        )
    }
}