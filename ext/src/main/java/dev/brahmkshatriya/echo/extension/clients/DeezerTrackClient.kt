package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Companion.toSource
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import dev.brahmkshatriya.echo.extension.LocalAudioServer
import dev.brahmkshatriya.echo.extension.Utils
import dev.brahmkshatriya.echo.extension.generateTrackUrl
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
import okhttp3.Request

class DeezerTrackClient(private val api: DeezerApi, private val parser: DeezerParser) {

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

        suspend fun fetchTrackData(track: Track): JsonObject {
            val response = api.track(arrayOf(track))
            return response["results"]?.jsonObject
                ?.get("data")?.jsonArray
                ?.firstOrNull()?.jsonObject ?: error("Invalid response: missing track data")
        }

        fun extractUrlFromJson(json: JsonObject): String {
            val data = json["data"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("No data found in JSON")
            val media = data["media"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("No media found in JSON data")
            val source = media["sources"]?.jsonArray?.get(1)?.jsonObject
                ?: media["sources"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("No sources found in media")
            return source["url"]?.jsonPrimitive?.content ?: error("No URL found in source")
        }

        suspend fun generateUrl(trackId: String, md5Origin: String, mediaVersion: String): String {
            var url = generateTrackUrl(trackId, md5Origin, mediaVersion, 1)
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 403) {
                    val fallbackJson = fetchTrackData(track)["FALLBACK"]?.jsonObject
                        ?: error("Fallback data missing")
                    val fallbackMd5Origin = fallbackJson["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                    val fallbackMediaVersion = fallbackJson["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                    url = generateTrackUrl(trackId, fallbackMd5Origin, fallbackMediaVersion, 1)
                }
            }
            return url
        }

        suspend fun getGeneratedUrl(trackId: String, originalTrack: Track, useFallback: Boolean): Pair<String, Track?> {
            return if (useFallback) {
                val fallbackData = fetchTrackData(originalTrack)
                val fallbackInfo = fallbackData["FALLBACK"]?.jsonObject ?: error("Fallback info missing")
                val fallbackId = fallbackInfo["SNG_ID"]?.jsonPrimitive?.content.orEmpty()
                val fallbackTrack = originalTrack.copy(id = fallbackId)
                val updatedData = fetchTrackData(fallbackTrack)
                val md5Origin = updatedData["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                val mediaVersion = updatedData["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                generateUrl(trackId, md5Origin, mediaVersion) to fallbackTrack
            } else {
                val freshData = fetchTrackData(originalTrack)
                val md5Origin = freshData["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                val mediaVersion = freshData["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                generateUrl(trackId, md5Origin, mediaVersion) to null
            }
        }

        val hasMp3Misc = track.extras["FILESIZE_MP3_MISC"]?.let { it != "0" } ?: false
        val qualityOptions = listOf("flac", "320", "128")

        return if (hasMp3Misc) {
            val mediaJson = api.getMP3MediaUrl(track)
            val trackJsonData = mediaJson["data"]?.jsonArray?.firstOrNull()?.jsonObject
            val mediaIsEmpty = trackJsonData?.get("media")?.jsonArray?.isEmpty() == true

            val (finalUrl, _) = if (mediaIsEmpty) {
                getGeneratedUrl(track.id, track, useFallback = false)
            } else {
                extractUrlFromJson(mediaJson) to null
            }

            track.copy(
                isLiked = isTrackLiked(track.id),
                streamables = listOf(
                    Streamable.server(
                        id = finalUrl,
                        quality = 0,
                        title = "MP3",
                        extras = mapOf("key" to Utils.createBlowfishKey(track.id))
                    )
                )
            )
        } else {
            val streamables = coroutineScope {
                qualityOptions.map { quality ->
                    async(Dispatchers.IO) {
                        try {
                            val mediaJson = api.getMediaUrl(track, quality)
                            val trackJsonData = mediaJson["data"]?.jsonArray?.firstOrNull()?.jsonObject
                            val mediaIsEmpty = trackJsonData?.get("media")?.jsonArray?.isEmpty() == true

                            val (finalUrl, fallbackTrack) = when {
                                mediaJson.toString().contains("Track token has no sufficient rights on requested media") -> {
                                    val fallbackData = fetchTrackData(track)
                                    val fallbackParsed = parser.run {
                                        fallbackData.toTrack(fallback = true).copy(extras = track.extras)
                                    }
                                    val fallbackMediaJson = api.getMediaUrl(fallbackParsed, quality)
                                    extractUrlFromJson(fallbackMediaJson) to fallbackParsed
                                }
                                mediaIsEmpty -> {
                                    getGeneratedUrl(track.id, track, useFallback = true)
                                }
                                else -> extractUrlFromJson(mediaJson) to null
                            }

                            Streamable.server(
                                id = finalUrl,
                                quality = when (quality) {
                                    "flac" -> 3
                                    "320" -> 2
                                    "128" -> 1
                                    else -> 0
                                },
                                title = when (quality) {
                                    "flac" -> "FLAC"
                                    "320" -> "320kbps"
                                    "128" -> "128kbps"
                                    else -> "UNKNOWN"
                                },
                                extras = mapOf("key" to Utils.createBlowfishKey(fallbackTrack?.id ?: track.id))
                            )
                        } catch (e: Exception) {
                            throw Exception("$quality not available")
                        }
                    }
                }.awaitAll()
            }

            track.copy(
                isLiked = isTrackLiked(track.id),
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