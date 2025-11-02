package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerLibraryClient(private val deezerExtension: DeezerExtension, private val api: DeezerApi, private val parser: DeezerParser) {

    private val tabs = listOf(
        Tab("all", "All"),
        Tab("playlists", "Playlists"),
        Tab("albums", "Albums"),
        Tab("tracks", "Tracks"),
        Tab("artists", "Artists"),
        Tab("shows", "Podcasts")
    )

    suspend fun loadLibraryFeedAll(): List<Shelf>? {
        deezerExtension.handleArlExpiration()

        return supervisorScope {
            tabs.map { tab ->
                async(Dispatchers.Default) {
                    val jsonObject = when (tab.id) {
                        "playlists" -> api.getPlaylists()
                        "albums" -> api.getAlbums()
                        "tracks" -> api.getTracks()
                        "artists" -> api.getArtists()
                        "shows" -> api.getShows()
                        else -> null
                    } ?: return@async null
                    val resultObject =
                        jsonObject["results"]?.jsonObject ?: return@async null
                    val dataArray = when (tab.id) {
                        "playlists", "albums", "artists", "shows" -> {
                            val tabObject =
                                resultObject["TAB"]?.jsonObject?.get(tab.id)?.jsonObject
                            tabObject?.get("data")?.jsonArray
                        }

                        "tracks" -> resultObject["data"]?.jsonArray
                        else -> return@async null
                    }
                    parser.run {
                        dataArray?.toShelfItemsList(tab.title)
                    }
                }
            }.mapNotNull { it.await() }
        }
    }

    suspend fun loadLibraryFeed(): Feed<Shelf> {
        deezerExtension.handleArlExpiration()

        return Feed(tabs) { tab ->
            when (tab?.id) {
                "all" -> loadLibraryFeedAll() ?: emptyList()
                "playlists" -> fetchData { api.getPlaylists() }
                "albums" -> fetchData { api.getAlbums() }
                "tracks" -> fetchData { api.getTracks() }
                "artists" -> fetchData { api.getArtists() }
                "shows" -> fetchData { api.getShows() }
                else -> emptyList()
            }.toFeedData(Feed.Buttons(showPlayAndShuffle = true))
        }
    }

    private suspend fun fetchData(apiCall: suspend () -> JsonObject): List<Shelf> {
        val jsonObject = apiCall()
        val resultObject = jsonObject["results"]?.jsonObject ?: return emptyList()
        val dataArray = when {
            resultObject["data"] != null -> resultObject["data"]?.jsonArray ?: emptyList()
            resultObject["TAB"] != null -> resultObject["TAB"]?.jsonObject?.values?.firstOrNull()?.jsonObject?.get("data")?.jsonArray
            else -> null
        }

        return dataArray?.mapNotNull { item ->
            parser.run {
                item.jsonObject.toEchoMediaItem()?.toShelf()
            }
        } ?: emptyList()
    }
}