package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.toArtist
import dev.brahmkshatriya.echo.extension.toShelfItemsList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerArtistClient(private val api: DeezerApi) {

    fun getShelves(artist: Artist): PagedData.Single<Shelf> = PagedData.Single {
        val jsonObject = api.artist(artist.id)
        val resultsObject = jsonObject["results"]!!.jsonObject

        val keyToBlock: Map<String, suspend (JsonObject) -> Shelf?> = mapOf(
            "TOP" to { jObject ->
                val test = jObject["data"]?.jsonArray?.toShelfItemsList("Top") as Shelf.Lists.Items
                val list = test.list as List<EchoMediaItem.TrackItem>
                Shelf.Lists.Tracks(
                    title = test.title,
                    list = list.map { it.track }.take(5),
                    subtitle = test.subtitle,
                    type = Shelf.Lists.Type.Linear,
                    isNumbered = true,
                    more = PagedData.Single {
                        list.map {
                            it.track
                        }
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
                jObject["data"]?.jsonArray?.toShelfItemsList("Related Artists")
            },
            "ALBUMS" to { jObject ->
                jObject["data"]?.jsonArray?.toShelfItemsList("Albums")
            }
        )

        resultsObject.mapNotNull { (key, value) ->
            val block = keyToBlock[key]
            block?.invoke(value.jsonObject)
        }
    }

    suspend fun loadArtist(artist: Artist): Artist {
        val jsonObject = api.artist(artist.id)
        val resultsObject =
            jsonObject["results"]?.jsonObject?.get("DATA")?.jsonObject ?: return artist
        return resultsObject.toArtist(isFollowingArtist(artist.id), true)
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
}