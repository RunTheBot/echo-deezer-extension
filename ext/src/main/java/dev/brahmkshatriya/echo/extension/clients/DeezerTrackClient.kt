package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Companion.toSource
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.LocalAudioServer
import dev.brahmkshatriya.echo.extension.Utils
import dev.brahmkshatriya.echo.extension.getByteStreamAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient

class DeezerTrackClient(private val api: DeezerApi) {

    private val client = OkHttpClient()

    suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        DeezerExtension().handleArlExpiration()
        return if (streamable.quality == 12) {
            streamable.id.toSource().toMedia()
        } else {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            if(isDownload) {
                getByteStreamAudio(scope, streamable, client)
            } else {
                val localAudioServer = LocalAudioServer
                localAudioServer.addTrack(streamable, scope)
                localAudioServer.getStreamUrlForTrack(streamable.id, scope).toSource().toMedia()
            }
        }
    }

    suspend fun loadTrack(track: Track): Track {
        DeezerExtension().handleArlExpiration()

        if (track.extras["__TYPE__"] == "show") {
            return track
        }

        fun extractUrlFromJson(json: JsonObject): String? {
            val data = json["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val media = data["media"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val source = media["sources"]?.jsonArray?.getOrNull(1)?.jsonObject
                ?: media["sources"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return null
            return source["url"]?.jsonPrimitive?.content
        }

        val originalTrackId = track.id
        val isMp3Misc = track.extras["FILESIZE_MP3_MISC"]?.let { it != "0" } ?: false

        return if (isMp3Misc) {
            val mediaJson = api.getMP3MediaUrl(track, false)
            val finalUrl = extractUrlFromJson(mediaJson)

            if (finalUrl == null) {
                track.copy(isLiked = isTrackLiked(originalTrackId), streamables = emptyList())
            } else {
                track.copy(
                    isLiked = isTrackLiked(originalTrackId),
                    streamables = listOf(
                        Streamable.server(
                            id = finalUrl,
                            quality = 0,
                            title = "MP3",
                            extras = mapOf("key" to Utils.createBlowfishKey(originalTrackId))
                        )
                    )
                )
            }
        } else {
            val qualityOptions = listOf("flac", "320", "128")

            suspend fun createStreamableForQuality(quality: String): Streamable? {
                return try {
                    val currentTrackId = track.id
                    val mediaJson = if(quality != "128") api.getMediaUrl(track, quality) else api.getMP3MediaUrl(track, true)
                    val trackJsonData = mediaJson["data"]?.jsonArray?.firstOrNull()?.jsonObject
                    val mediaIsEmpty = trackJsonData?.get("media")?.jsonArray?.isEmpty() == true

                    val (finalUrl, fallbackTrack) = when {
                        mediaJson.toString().contains("Track token has no sufficient rights on requested media") || mediaIsEmpty -> {
                            val fallbackParsed = track.copy(id = track.extras["FALLBACK_ID"].orEmpty())
                            val fallbackMediaJson = api.getMediaUrl(fallbackParsed, quality)
                            val url = extractUrlFromJson(fallbackMediaJson) ?: return null
                            url to fallbackParsed
                        }

                        else -> {
                            val url = extractUrlFromJson(mediaJson) ?: return null
                            url to null
                        }
                    }

                    val qualityValue = when (quality) {
                        "flac" -> 9
                        "320" -> 6
                        "128" -> 3
                        else -> 0
                    }
                    val qualityTitle = when (quality) {
                        "flac" -> "FLAC"
                        "320" -> "320kbps"
                        "128" -> "128kbps"
                        else -> "UNKNOWN"
                    }
                    val keySourceId = fallbackTrack?.id ?: currentTrackId

                    Streamable.server(
                        id = finalUrl,
                        quality = qualityValue,
                        title = qualityTitle,
                        extras = mapOf("key" to Utils.createBlowfishKey(keySourceId))
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val streamables = coroutineScope {
                qualityOptions.map { quality ->
                    async(Dispatchers.IO) { createStreamableForQuality(quality) }
                }.awaitAll().filterNotNull()
            }

            track.copy(
                isLiked = isTrackLiked(originalTrackId),
                streamables = streamables
            )
        }
    }

    private suspend fun isTrackLiked(id: String): Boolean {
        val dataArray = api.getTracks()["results"]?.jsonObject
            ?.get("data")?.jsonArray ?: return false

        val trackIds = dataArray.mapNotNull { it.jsonObject["SNG_ID"]?.jsonPrimitive?.content }.toSet()
        return id in trackIds
    }
}