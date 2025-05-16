package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DeezerArtist(private val deezerApi: DeezerApi) {

    suspend fun artist(id: String, language: String): JsonObject {
        return deezerApi.callApi(
            method = "deezer.pageArtist",
            params = buildJsonObject {
                put("art_id", id)
                put ("lang", language.substringBefore("-"))
            }
        )
    }

    suspend fun getArtists(userId: String): JsonObject {
        return deezerApi.callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("nb", 40)
                put ("tab", "artists")
                put("user_id", userId)
            }
        )
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