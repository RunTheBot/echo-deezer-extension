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
        return if (streamable.quality == 1) {
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

    suspend fun loadTrack(track: Track, quality: String): Track {
        DeezerExtension().handleArlExpiration()

        suspend fun fetchTrackData(track: Track): JsonObject {
            val response = api.track(arrayOf(track))
            return response["results"]!!
                .jsonObject["data"]!!
                .jsonArray.first().jsonObject
        }

        fun extractUrlFromJson(json: JsonObject): String {
            val data = json["data"]?.jsonArray?.first()?.jsonObject
                ?: error("No data found in JSON")
            val media = data["media"]?.jsonArray?.first()?.jsonObject
                ?: error("No media found in JSON data")
            val source = media["sources"]?.jsonArray?.first()?.jsonObject
                ?: error("No sources found in media")
            return source["url"]?.jsonPrimitive?.content
                ?: error("No URL found in source")
        }

        suspend fun generateUrl(trackId: String, md5Origin: String, mediaVersion: String): String {
            var url = generateTrackUrl(trackId, md5Origin, mediaVersion, 1)
            val request = Request.Builder().url(url).build()
            val responseCode = client.newCall(request).execute().code

            if (responseCode == 403) {
                val fallbackData = fetchTrackData(track)["FALLBACK"]!!.jsonObject
                val fallbackMd5Origin = fallbackData["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                val fallbackMediaVersion = fallbackData["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                url = generateTrackUrl(trackId, fallbackMd5Origin, fallbackMediaVersion, 1)
            }
            return url
        }

        suspend fun getGeneratedUrl(trackId: String, track: Track, useFallback: Boolean): Pair<String, Track?> {
            return if (useFallback) {
                val data = fetchTrackData(track)
                val fallbackInfo = data["FALLBACK"]!!.jsonObject
                val fallbackId = fallbackInfo["SNG_ID"]?.jsonPrimitive?.content.orEmpty()
                val newFallbackTrack = track.copy(id = fallbackId)
                val newData = fetchTrackData(newFallbackTrack)
                val md5Origin = newData["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                val mediaVersion = newData["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                generateUrl(trackId, md5Origin, mediaVersion) to newFallbackTrack
            } else {
                val freshData = fetchTrackData(track)
                val md5Origin = freshData["MD5_ORIGIN"]?.jsonPrimitive?.content.orEmpty()
                val mediaVersion = freshData["MEDIA_VERSION"]?.jsonPrimitive?.content.orEmpty()
                generateUrl(trackId, md5Origin, mediaVersion) to null
            }
        }

        val initialData = fetchTrackData(track)
        val newTrack = parser.run {
            initialData.toTrack(loaded = true).copy(extras = track.extras)
        }

        if (newTrack.extras["__TYPE__"] == "show") {
            return newTrack
        }

        val hasMp3Misc = newTrack.extras["FILESIZE_MP3_MISC"]?.let { it != "0" } ?: false
        val mediaJson = if (hasMp3Misc) api.getMP3MediaUrl(newTrack) else api.getMediaUrl(newTrack, quality)

        val trackJsonData = mediaJson["data"]?.jsonArray?.first()?.jsonObject
        val mediaIsEmpty = trackJsonData?.get("media")?.jsonArray?.isEmpty() == true

        val (finalUrl, fallbackTrack) = when {
            mediaJson.toString().contains("Track token has no sufficient rights on requested media") -> {
                val fallbackData = fetchTrackData(track)
                val fallbackTrack = parser.run {
                    fallbackData.toTrack(loaded = true, fallback = true).copy(extras = track.extras)
                }
                val fallbackMediaJson = api.getMediaUrl(fallbackTrack, quality)
                extractUrlFromJson(fallbackMediaJson) to fallbackTrack
            }
            mediaIsEmpty -> {
                if (hasMp3Misc) {
                    getGeneratedUrl(newTrack.id, track, useFallback = false)
                } else {
                    getGeneratedUrl(newTrack.id, track, useFallback = true)
                }
            }
            else -> extractUrlFromJson(mediaJson) to null
        }

        return newTrack.copy(
            id = fallbackTrack?.id ?: newTrack.id,
            isLiked = isTrackLiked(newTrack.id),
            streamables = listOf(
                Streamable.server(
                    id = finalUrl,
                    quality = 0,
                    title = fallbackTrack?.title ?: newTrack.title,
                    extras = mapOf("key" to Utils.createBlowfishKey(fallbackTrack?.id ?: newTrack.id))
                )
            )
        )
    }

    private suspend fun isTrackLiked(id: String): Boolean {
        val dataArray = api.getTracks()["results"]?.jsonObject
            ?.get("data")?.jsonArray ?: return false

        val trackIds = dataArray.mapNotNull { it.jsonObject["SNG_ID"]?.jsonPrimitive?.content }.toSet()
        return id in trackIds
    }
}