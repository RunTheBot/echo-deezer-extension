package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerPlaylistClient(private val api: DeezerApi, private val parser: DeezerParser) {

    fun getShelves(playlist: Playlist): PagedData.Single<Shelf> = PagedData.Single {
        DeezerExtension().handleArlExpiration()
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val dataArray = songsObject["data"]?.jsonArray ?: JsonArray(emptyList())
        val data = dataArray.mapNotNull { song ->
            parser.run {
                song.jsonObject.toShelfItemsList(name = "")
            }
        }
        //data
        emptyList()
    }

    suspend fun loadPlaylist(playlist: Playlist): Playlist {
        DeezerExtension().handleArlExpiration()
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        return parser.run { resultsObject.toPlaylist(true) }
    }

    fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        DeezerExtension().handleArlExpiration()
        val jsonObject = api.playlist(playlist)
        val dataArray = jsonObject["results"]!!.jsonObject["SONGS"]!!.jsonObject["data"]!!.jsonArray
        dataArray.mapIndexed { index, song ->
            val currentTrack = parser.run { song.jsonObject.toTrack() }
            val nextTrack = parser.run { dataArray.getOrNull(index + 1)?.jsonObject?.toTrack() }
            Track(
                id = currentTrack.id,
                title = currentTrack.title,
                cover = currentTrack.cover,
                duration = currentTrack.duration,
                releaseDate = currentTrack.releaseDate,
                artists = currentTrack.artists,
                extras = currentTrack.extras.plus(
                    mapOf(
                        "NEXT" to nextTrack?.id.orEmpty(),
                        "playlist_id" to playlist.id
                    )
                )
            )
        }
    }
}