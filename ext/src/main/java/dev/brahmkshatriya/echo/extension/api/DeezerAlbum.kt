package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeezerAlbum(private val deezerApi: DeezerApi) {

    suspend fun album(album: Album, language: String): JsonObject {
        return deezerApi.callApi(
            method = "deezer.pageAlbum",
            params = buildJsonObject {
                put("alb_id", album.id)
                put("header", true)
                put("lang", language.substringBefore("-"))
            }
        )
    }

    suspend fun getAlbums(userId: String): JsonObject {
        return deezerApi.callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("user_id", userId)
                put("tab", "albums")
                put("nb", 10000)
            }
        )
    }

    suspend fun addFavoriteAlbum(id: String) {
        deezerApi.callApi(
            method = "album.addFavorite",
            params = buildJsonObject {
                put("ALB_ID", id)
            }
        )
    }

    suspend fun removeFavoriteAlbum(id: String) {
        deezerApi.callApi(
            method = "album.deleteFavorite",
            params = buildJsonObject {
                put("ALB_ID", id)
            }
        )
    }
}