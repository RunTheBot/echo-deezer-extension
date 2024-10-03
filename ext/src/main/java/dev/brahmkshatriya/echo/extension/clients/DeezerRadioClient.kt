package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.toTrack
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerRadioClient(private val api: DeezerApi) {

    fun loadTracks(radio: Radio): PagedData<Track> = PagedData.Single {
        println("FUCK YOU $radio")
        val dataArray = when (radio.extras["radio"]) {
            "track"-> {
                val jsonObject = api.mix(radio.id)
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }

            "artist" -> {
                val jsonObject = api.mixArtist(radio.id)
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }

            "playlist", "album" -> {
                val jsonObject = api.radio(radio.id, radio.extras["artist"] ?: "")
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }

            else -> {
                val jsonObject = api.flow(radio.id)
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }
        }

        dataArray.mapIndexed { index, song ->
            val track = song.jsonObject.toTrack()
            val nextTrack = dataArray.getOrNull(index + 1)?.jsonObject?.toTrack()
            val nextTrackId = nextTrack?.id

            Track(
                id = track.id,
                title = track.title,
                cover = track.cover,
                duration = track.duration,
                releaseDate = track.releaseDate,
                artists = track.artists,
                extras = track.extras.plus(
                    mapOf(
                        "NEXT" to (nextTrackId ?: ""),
                        when (radio.extras["radio"]) {
                            "track" -> "artist_id" to track.artists[0].id
                            "artist" -> "artist_id" to radio.id
                            "playlist", "album" -> "artist_id" to (radio.extras["artist"] ?: "")
                            else -> "user_id" to "0"
                        }
                    )
                )
            )
        }
    }

    fun radio(track: Track, context: EchoMediaItem?): Radio {
        return when (context) {
            null -> {
                Radio(
                    id = track.id,
                    title = track.title,
                    cover = track.cover,
                    extras = mapOf(
                        "radio" to "track"
                    )
                )
            }

            is EchoMediaItem.Lists.RadioItem -> {
                when (context.radio.extras["radio"]) {
                    "track" -> {
                        Radio(
                            id = track.id,
                            title = track.title,
                            cover = track.cover,
                            extras = mapOf(
                                "radio" to "track"
                            )
                        )
                    }

                    "artist" -> {
                        Radio(
                            id = context.radio.id,
                            title = context.radio.title,
                            cover = context.radio.cover,
                            extras = mapOf(
                                "radio" to "artist"
                            )
                        )
                    }

                    "playlist", "album" -> {
                        Radio(
                            id = track.id,
                            title = track.title,
                            cover = track.cover,
                            extras = mapOf(
                                ("radio" to context.radio.extras["radio"].orEmpty()),
                                "artist" to track.artists[0].id
                            )
                        )
                    }

                    else -> {
                        context.radio
                    }
                }
            }

            is EchoMediaItem.Profile.ArtistItem -> {
                Radio(
                    id = context.artist.id,
                    title =  context.artist.name,
                    cover = context.artist.cover,
                    extras = mapOf(
                        "radio" to "artist"
                    )
                )
            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                Radio(
                    id = track.id,
                    title = track.title,
                    cover = track.cover,
                    extras = mapOf(
                        "radio" to "playlist",
                        "artist" to track.artists[0].id
                    )
                )
            }

            is EchoMediaItem.Lists.AlbumItem -> {
                Radio(
                    id = track.id,
                    title = track.title,
                    cover = track.cover,
                    extras = mapOf(
                        "radio" to "album",
                        "artist" to track.artists[0].id
                    )
                )
            }

            else -> throw Exception("Radio Error")
        }
    }

    suspend fun radio(album: Album): Radio {
        val jsonObject = api.album(album)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val lastTrack = songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack()
        return Radio(
            id = lastTrack.id,
            title = lastTrack.title,
            cover = lastTrack.cover,
            extras = mapOf(
                "radio" to "album",
                "artist" to lastTrack.artists[0].id
            )
        )
    }

    suspend fun radio(playlist: Playlist): Radio {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val lastTrack = songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack()
        return Radio(
            id = lastTrack.id,
            title = lastTrack.title,
            cover = lastTrack.cover,
            extras = mapOf(
                "radio" to "playlist",
                "artist" to lastTrack.artists[0].id
            )
        )
    }

    fun radio(artist: Artist): Radio {
        return Radio(
            id = artist.id,
            title = artist.name,
            cover = artist.cover,
            extras = mapOf(
                "radio" to "artist"
            )
        )
    }
}