package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.extension.DeezerApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class DeezerSearch(private val deezerApi: DeezerApi, private val json: Json) {

    suspend fun search(query: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "deezer.pageSearch",
            params = buildJsonObject {
                put("nb", 128)
                put("query", query)
                put("start", 0)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun searchSuggestions(query: String): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "search_getSuggestedQueries",
            params = buildJsonObject {
                put("QUERY", query)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun setSearchHistory(query: String) {
        deezerApi.callApi(
            method = "user.addEntryInSearchHistory",
            params = buildJsonObject {
                putJsonObject("ENTRY") {
                    put("query", query)
                    put("type", "query")
                }
            }
        )
    }

    suspend fun getSearchHistory(): JsonObject {
        val jsonData = deezerApi.callApi(
            method = "deezer.userMenu"
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun deleteSearchHistory(userId: String) {
        deezerApi.callApi(
            method = "user.clearSearchHistory",
            params = buildJsonObject {
                put("USER_ID", userId)
            }
        )
    }
}