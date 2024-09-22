package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.toAlbum
import dev.brahmkshatriya.echo.extension.toEpisode
import dev.brahmkshatriya.echo.extension.toShow
import dev.brahmkshatriya.echo.extension.toTrack
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerAlbumClient(private val api: DeezerApi) {

    suspend fun loadAlbum(album: Album): Album {
        if (album.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return resultsObject.toShow(true)
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return resultsObject.toAlbum(true)
        }
    }

    fun loadTracks(album: Album): PagedData<Track> = PagedData.Single {
        if (album.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val episodesObject = resultsObject["EPISODES"]!!.jsonObject
            val dataArray = episodesObject["data"]!!.jsonArray
            val data = dataArray.map { episode ->
                episode.jsonObject.toEpisode()
            }.reversed()
            data
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val songsObject = resultsObject["SONGS"]!!.jsonObject
            val dataArray = songsObject["data"]!!.jsonArray
            val data = dataArray.mapIndexed { index, song ->
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
                            Pair("NEXT", nextTrack?.id.orEmpty()),
                            Pair("album_id", album.id)
                        )
                    )
                )
            }
            data
        }
    }
}