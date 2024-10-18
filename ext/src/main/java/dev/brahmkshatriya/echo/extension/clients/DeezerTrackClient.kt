package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Audio.Companion.toAudio
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.Utils
import dev.brahmkshatriya.echo.extension.generateTrackUrl
import dev.brahmkshatriya.echo.extension.getByteStreamAudio
import dev.brahmkshatriya.echo.extension.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class DeezerTrackClient(private val api: DeezerApi) {

    private val client = OkHttpClient()

    suspend fun getStreamableMedia(streamable: Streamable): Streamable.Media {
        return if (streamable.quality == 1) {
            streamable.id.toAudio().toMedia()
        } else {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            getByteStreamAudio(scope, streamable, client)
        }
    }

    suspend fun loadTrack(track: Track, quality: String, log: Boolean): Track {
        suspend fun fetchTrackData(track: Track): JsonObject {
            val trackObject = api.track(arrayOf(track))
            return trackObject["results"]!!.jsonObject["data"]!!.jsonArray[0].jsonObject
        }

        val newTrack = fetchTrackData(track).toTrack(loaded = true).copy(extras = track.extras)

        if (newTrack.extras["__TYPE__"] == "show") {
            return newTrack
        }

        val jsonObject =
            if (newTrack.extras["FILESIZE_MP3_MISC"] != "0" && newTrack.extras["FILESIZE_MP3_MISC"] != null) {
                api.getMP3MediaUrl(newTrack)
            } else {
                api.getMediaUrl(newTrack, quality)
            }

        val key = Utils.createBlowfishKey(newTrack.id)

        suspend fun generateUrl(trackId: String, md5Origin: String, mediaVersion: String): String {
            var url = generateTrackUrl(trackId, md5Origin, mediaVersion, 1)
            val request = Request.Builder().url(url).build()
            val code = client.newCall(request).execute().code
            if (code == 403) {
                val fallbackObject = fetchTrackData(track)["FALLBACK"]!!.jsonObject
                val backMd5Origin = fallbackObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val backMediaVersion =
                    fallbackObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                url = generateTrackUrl(trackId, backMd5Origin, backMediaVersion, 1)
            }
            return url
        }

        val url = when {
            jsonObject.toString()
                .contains("Track token has no sufficient rights on requested media") -> {
                val dataObject = fetchTrackData(track)
                val md5Origin = dataObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val mediaVersion = dataObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                generateUrl(newTrack.id, md5Origin, mediaVersion)
            }

            newTrack.extras["FILESIZE_MP3_MISC"] != "0" && newTrack.extras["FILESIZE_MP3_MISC"] != null && jsonObject["data"]!!.jsonArray.first().jsonObject["media"]?.jsonArray?.isEmpty() == true -> {
                val dataObject = fetchTrackData(track)
                val md5Origin = dataObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val mediaVersion = dataObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                generateUrl(newTrack.id, md5Origin, mediaVersion)
            }

            jsonObject["data"]!!.jsonArray.first().jsonObject["media"]?.jsonArray?.isEmpty() == true -> {
                val dataObject = fetchTrackData(track)
                val fallbackObject = dataObject["FALLBACK"]!!.jsonObject
                val backId = fallbackObject["SNG_ID"]?.jsonPrimitive?.content ?: ""
                val fallbackTrack = track.copy(id = backId)
                val newDataObject = fetchTrackData(fallbackTrack)
                val md5Origin = newDataObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val mediaVersion = newDataObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                generateUrl(newTrack.id, md5Origin, mediaVersion)
            }

            else -> {
                val dataObject = jsonObject["data"]!!.jsonArray.first().jsonObject
                val mediaObject = dataObject["media"]!!.jsonArray.first().jsonObject
                val sourcesObject = mediaObject["sources"]!!.jsonArray[0]
                sourcesObject.jsonObject["url"]!!.jsonPrimitive.content
            }
        }

        if (log) {
            api.log(newTrack)
        }

        return newTrack.copy(
            isLiked = isTrackLiked(newTrack.id),
            streamables = listOf(
                Streamable.audio(
                    id = url,
                    quality = 0,
                    title = newTrack.title,
                    extra = mapOf("key" to key)
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