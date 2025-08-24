package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerAlbumClient(private val deezerExtension: DeezerExtension, private val api: DeezerApi, private val parser: DeezerParser) {

    suspend fun loadAlbum(album: Album): Album {
        deezerExtension.handleArlExpiration()
        if (album.type == Album.Type.Show) {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return parser.run { resultsObject.toShow() }
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return parser.run { resultsObject.toAlbum() }
        }
    }

    fun loadTracks(album: Album): Feed<Track> = PagedData.Single {
        deezerExtension.handleArlExpiration()
        if (album.type == Album.Type.Show) {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val episodesObject = resultsObject["EPISODES"]!!.jsonObject
            val dataArray = episodesObject["data"]!!.jsonArray

            val bookmarkJsonObj = api.getBookmarkedEpisodes()
            val bookmarkResultsObj = bookmarkJsonObj["results"]!!.jsonObject
            val bookmarkDataArray = bookmarkResultsObj["data"]!!.jsonArray

            val bookmarkMap = mutableMapOf<String?, Long?>()

            bookmarkDataArray.map { ep ->
                val id = ep.jsonObject["EPISODE_ID"]?.jsonPrimitive?.content
                val offset = ep.jsonObject["OFFSET"]?.jsonPrimitive?.content?.toLongOrNull()
                bookmarkMap.put(id, offset)
            }

            val data = dataArray.map { episode ->
                parser.run {
                    episode.jsonObject.toEpisode(bookmarkMap)
                }
            }.reversed()
            data
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val songsObject = resultsObject["SONGS"]!!.jsonObject
            val dataArray = songsObject["data"]!!.jsonArray
            dataArray.mapIndexed { index, song ->
                val currentTrack = parser.run { song.jsonObject.toTrack() }
                val nextTrack = parser.run { dataArray.getOrNull(index + 1)?.jsonObject?.toTrack() }
                currentTrack.copy(
                    extras = currentTrack.extras + mapOf(
                        "NEXT" to nextTrack?.id.orEmpty(),
                        "album_id" to album.id
                    )
                )
            }
        }
    }.toFeed()
}