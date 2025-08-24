package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class DeezerRadioClient(private val api: DeezerApi, private val parser: DeezerParser) {

    fun loadTracks(radio: Radio): Feed<Track> = PagedData.Single {
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
            val track = parser.run { song.jsonObject.toTrack() }
            val nextTrack = parser.run { dataArray.getOrNull(index + 1)?.jsonObject?.toTrack() }
            val nextTrackId = nextTrack?.id

            track.copy(
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
    }.toFeed()

    suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio {
        when(item) {
            is Artist -> {
                return Radio(
                    id = item.id,
                    title = item.name,
                    cover = item.cover,
                    extras = mapOf(
                        "radio" to "artist"
                    )
                )
            }
            is Album -> {
                val jsonObject = api.album(item)
                val resultsObject = jsonObject["results"]!!.jsonObject
                val songsObject = resultsObject["SONGS"]!!.jsonObject
                val lastTrack = parser.run { songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack() }
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
            is Playlist -> {
                val jsonObject = api.playlist(item)
                val resultsObject = jsonObject["results"]!!.jsonObject
                val songsObject = resultsObject["SONGS"]!!.jsonObject
                val lastTrack = parser.run { songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack() }
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
            is Track -> {
                return try {
                    val track = item
                    when (context) {
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

                        is Radio -> {
                            when (context.extras["radio"]) {
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
                                        id = context.id,
                                        title = context.title,
                                        cover = context.cover,
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
                                            ("radio" to context.extras["radio"].orEmpty()),
                                            "artist" to track.artists[0].id
                                        )
                                    )
                                }

                                else -> {
                                    context
                                }
                            }
                        }

                        is Artist -> {
                            Radio(
                                id = context.id,
                                title = context.name,
                                cover = context.cover,
                                extras = mapOf(
                                    "radio" to "artist"
                                )
                            )
                        }

                        is Playlist -> {
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

                        is Album -> {
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
                } catch (e: Exception) {
                    error("No Radio")
                }
            }
            is Radio -> TODO()
        }
    }
}