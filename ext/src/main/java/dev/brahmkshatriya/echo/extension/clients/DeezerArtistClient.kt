package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerArtistClient(private val deezerExtension: DeezerExtension, private val api: DeezerApi, private val parser: DeezerParser) {

    fun getShelves(artist: Artist): PagedData.Single<Shelf> = PagedData.Single {
        deezerExtension.handleArlExpiration()
        try {
            val jsonObject = api.artist(artist.id)
            val resultsObject = jsonObject["results"]!!.jsonObject

            val keyToBlock: Map<String, DeezerParser.(JsonObject) -> Shelf?> = parser.run {
                shelfFactories
            }

            orderedKeys.mapNotNull { key ->
                val value = resultsObject[key]
                val block = keyToBlock[key]
                if (value != null && block != null) {
                    block.invoke(parser, value.jsonObject)
                } else null
            }
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun loadArtist(artist: Artist): Artist {
        deezerExtension.handleArlExpiration()
        val jsonObject = api.artist(artist.id)
        val resultsObject =
            jsonObject["results"]?.jsonObject ?: return artist
        val isFollowing = isFollowingArtist(artist.id)
        return parser.run { resultsObject.toArtist(isFollowing) }
    }

    private suspend fun isFollowingArtist(id: String): Boolean {
        val dataArray = api.getArtists()["results"]?.jsonObject
            ?.get("TAB")?.jsonObject
            ?.get("artists")?.jsonObject
            ?.get("data")?.jsonArray ?: return false

        return dataArray.any { item ->
            val artistId = item.jsonObject["ART_ID"]?.jsonPrimitive?.content
            artistId == id
        }
    }

    private companion object {
        private val shelfFactories: Map<String, DeezerParser.(JsonObject) -> Shelf?> = mapOf(
            "TOP" to { jObject ->
                val shelf =
                    jObject["data"]?.jsonArray?.toShelfItemsList("Top") as? Shelf.Lists.Items
                val list = shelf?.list as? List<EchoMediaItem.TrackItem>
                Shelf.Lists.Tracks(
                    title = shelf?.title.orEmpty(),
                    list = list?.map { it.track }?.take(5) ?: emptyList(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    isNumbered = true,
                    more = PagedData.Single {
                        list?.map {
                            it.track
                        } ?: emptyList()
                    }

                )
            },
            "HIGHLIGHT" to { jObject ->
                jObject["ITEM"]?.jsonObject?.toShelfItemsList("Highlight")
            },
            "SELECTED_PLAYLIST" to { jObject ->
                jObject["data"]?.jsonArray?.toShelfItemsList("Selected Playlists")
            },
            "RELATED_PLAYLIST" to { jObject ->
                jObject["data"]?.jsonArray?.toShelfItemsList("Related Playlists")
            },
            "RELATED_ARTISTS" to { jObject ->
                val shelf =
                    jObject["data"]?.jsonArray?.toShelfItemsList("Related Artists") as? Shelf.Lists.Items
                val list = shelf?.list
                Shelf.Lists.Items(
                    title = shelf?.title.orEmpty(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    more = PagedData.Single {
                        list?.map {
                            it
                        } ?: emptyList()
                    },
                    list = list ?: emptyList()
                )
            },
            "ALBUMS" to { jObject ->
                val shelf =
                    jObject["data"]?.jsonArray?.toShelfItemsList("Albums") as? Shelf.Lists.Items
                val list = shelf?.list
                Shelf.Lists.Items(
                    title = shelf?.title.orEmpty(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    more = PagedData.Single {
                        list?.map {
                            it
                        } ?: emptyList()
                    },
                    list = list ?: emptyList()
                )
            }
        )

        private val orderedKeys = listOf(
            "TOP",
            "HIGHLIGHT",
            "SELECTED_PLAYLIST",
            "ALBUMS",
            "RELATED_PLAYLIST",
            "RELATED_ARTISTS"
        )
    }
}