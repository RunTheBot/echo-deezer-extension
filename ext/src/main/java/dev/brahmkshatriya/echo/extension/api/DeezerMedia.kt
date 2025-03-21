package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DeezerMedia(private val json: Json, private val clientNP: OkHttpClient) {

    fun getMP3MediaUrl(track: Track, language: String, arl: String, sid: String, licenseToken: String): JsonObject {
        val headers = Headers.Builder().apply {
            add("Accept-Encoding", "gzip")
            add("Accept-Language", language.substringBefore("-"))
            add("Cache-Control", "max-age=0")
            add("Connection", "Keep-alive")
            add("Content-Type", "application/json; charset=utf-8")
            add("Cookie", "arl=$arl&sid=$sid")
            add("Host", "media.deezer.com")
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
        }.build()

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("media.deezer.com")
            .addPathSegment("v1")
            .addPathSegment("get_url")
            .build()

        val requestBody = json.encodeToString(
            buildJsonObject {
                put("license_token", licenseToken)
                putJsonArray("media") {
                    add(buildJsonObject {
                        put("type", "FULL")
                        putJsonArray("formats") {
                            add(buildJsonObject {
                                put("cipher", "BF_CBC_STRIPE")
                                put("format", "MP3_MISC")
                            })
                        }
                    })
                }
                putJsonArray("track_tokens") { add(track.extras["TRACK_TOKEN"]) }
            }
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .headers(headers)
            .build()

        val response = clientNP.newCall(request).execute()
        val responseBody = response.body.string()

        return json.decodeFromString<JsonObject>(responseBody)
    }

    fun getMediaUrl(track: Track, quality: String): JsonObject {
        val formats = when (quality) {
            "128" -> arrayOf("MP3_128", "MP3_64", "MP3_MISC")
            "flac" -> arrayOf("FLAC", "MP3_320", "MP3_128", "MP3_64", "MP3_MISC")
            else -> arrayOf("MP3_320", "MP3_128", "MP3_64", "MP3_MISC")
        }

        val requestBody = json.encodeToString(
            buildJsonObject {
                put("formats", buildJsonArray { formats.forEach { add(it) } })
                put ("ids", buildJsonArray{ add(track.id.toLong()) })
            }
        ).toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://dzmedia.fly.dev/get_url")
            .post(requestBody)
            .build()

        val response = clientNP.newCall(request).execute()
        val responseBody = response.body.string()

        return json.decodeFromString<JsonObject>(responseBody)
    }
}