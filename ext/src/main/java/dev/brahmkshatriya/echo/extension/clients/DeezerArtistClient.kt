package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerArtistClient(private val deezerExtension: DeezerExtension, private val api: DeezerApi, private val parser: DeezerParser) {

    fun getShelves(artist: Artist): Feed<Shelf> = PagedData.Single {
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
    }.toFeed()

    suspend fun loadArtist(artist: Artist): Artist {
        deezerExtension.handleArlExpiration()
        val jsonObject = api.artist(artist.id)
        val resultsObject =
            jsonObject["results"]?.jsonObject ?: return artist
        return parser.run { resultsObject.toArtist() }
    }

    suspend fun isFollowing(item: EchoMediaItem): Boolean {
        val dataArray = api.getArtists()["results"]?.jsonObject
            ?.get("TAB")?.jsonObject
            ?.get("artists")?.jsonObject
            ?.get("data")?.jsonArray ?: return false

        return dataArray.any { artistItem ->
            val artistId = artistItem.jsonObject["ART_ID"]?.jsonPrimitive?.content
            artistId == item.id
        }
    }

    fun getFollowersCount(item: EchoMediaItem): Long? = item.extras["followers"]?.toLongOrNull()

    private companion object {
        private val shelfFactories: Map<String, DeezerParser.(JsonObject) -> Shelf?> = mapOf(
            "TOP" to { jObject ->
                val shelf =
                    jObject["data"]?.jsonArray?.toShelfItemsList("Top") as? Shelf.Lists.Items
                val preList = shelf?.list as? List<Track>
                val list = preList?.asSequence()?.map { it }?.toList() ?: emptyList()
                Shelf.Lists.Tracks(
                    id = shelf?.id.orEmpty(),
                    title = shelf?.title.orEmpty(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    more = list.map {
                            it.toShelf()
                        }.toFeed(),
                    list = list.take(5)
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
                val list = shelf?.list ?: emptyList()
                Shelf.Lists.Items(
                    id = shelf?.id.orEmpty(),
                    title = shelf?.title.orEmpty(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    more = list.map { it.toShelf() }.toFeed(),
                    list = list
                )
            },
            "ALBUMS" to { jObject ->
                val shelf =
                    jObject["data"]?.jsonArray?.toShelfItemsList("Albums") as? Shelf.Lists.Items
                val list = shelf?.list  ?: emptyList()
                Shelf.Lists.Items(
                    id = shelf?.id.orEmpty(),
                    title = shelf?.title.orEmpty(),
                    subtitle = shelf?.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    more = list.map { it.toShelf() }.toFeed(),
                    list = list
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