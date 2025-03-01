package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerAlbumClient(private val api: DeezerApi, private val parser: DeezerParser) {

    suspend fun loadAlbum(album: Album): Album {
        DeezerExtension().handleArlExpiration()
        if (album.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return parser.run { resultsObject.toShow(true) }
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return parser.run { resultsObject.toAlbum(true) }
        }
    }

    fun loadTracks(album: Album): PagedData<Track> = PagedData.Single {
        DeezerExtension().handleArlExpiration()
        if (album.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val episodesObject = resultsObject["EPISODES"]!!.jsonObject
            val dataArray = episodesObject["data"]!!.jsonArray
            val data = dataArray.map { episode ->
                parser.run {
                    episode.jsonObject.toEpisode()
                }
            }.reversed()
            data
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val songsObject = resultsObject["SONGS"]!!.jsonObject
            val dataArray = songsObject["data"]!!.jsonArray
            dataArray.mapIndexed { index, song ->
                val currentTrack = parser.run { song.jsonObject.toTrack(true) }
                val nextTrack = parser.run { dataArray.getOrNull(index + 1)?.jsonObject?.toTrack() }
                currentTrack.copy(
                    extras = currentTrack.extras + mapOf(
                        "NEXT" to nextTrack?.id.orEmpty(),
                        "album_id" to album.id
                    )
                )
            }
        }
    }
}