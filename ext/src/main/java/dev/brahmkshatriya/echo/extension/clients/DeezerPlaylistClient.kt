package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.toPlaylist
import dev.brahmkshatriya.echo.extension.toShelfItemsList
import dev.brahmkshatriya.echo.extension.toTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerPlaylistClient(private val api: DeezerApi) {

    fun getShelves(playlist: Playlist): PagedData.Single<Shelf> = PagedData.Single {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val dataArray = songsObject["data"]?.jsonArray ?: JsonArray(emptyList())
        val data = dataArray.mapNotNull { song ->
            song.jsonObject.toShelfItemsList(name = "")
        }
        //data
        emptyList()
    }

    suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        return resultsObject.toPlaylist(true)
    }

    fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        val jsonObject = api.playlist(playlist)
        val dataArray = jsonObject["results"]!!.jsonObject["SONGS"]!!.jsonObject["data"]!!.jsonArray
        dataArray.mapIndexed { index, song ->
            val currentTrack = song.jsonObject.toTrack()
            val nextTrack = dataArray.getOrNull(index + 1)?.jsonObject?.toTrack()
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