package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT
import fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import kotlinx.io.IOException
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class LocalAudioWebServer(
    hostname: String,
    port: Int
) : NanoHTTPD(hostname, port) {

    private val trackMap = mutableMapOf<String, Pair<Streamable, String>>()

    fun addOrUpdateTrack(streamable: Streamable) {
        val key = streamable.extras["key"] ?: ""
        trackMap[streamable.id] = streamable to key
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Only GET allowed")
        }

        if (session.uri != "/stream") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        val trackId = session.parms["trackId"]
        if (trackId.isNullOrBlank()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Missing trackId parameter!"
            )
        }

        val (streamable, key) = trackMap[trackId]
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Track not found!"
            )

        val rangeHeader = session.headers["range"]
        var startBytes = 0L
        var endBytes = -1L
        var isRanged = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            isRanged = true
            val ranges = rangeHeader.removePrefix("bytes=").split("-")
            startBytes = ranges[0].toLongOrNull() ?: 0
            if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                endBytes = ranges[1].toLongOrNull() ?: -1
            }
        }

        return deezerStream(streamable.id, startBytes, endBytes, isRanged, key)
    }
}

private fun deezerStream(
    sURL: String,
    startBytes: Long,
    end: Long,
    isRanged: Boolean,
    key: String
): NanoHTTPD.Response {

    var deezerStart = startBytes
    deezerStart -= startBytes % 2048
    val dropBytes = startBytes % 2048

    return try {
        val url = URL(sURL)
        val connection = url.openConnection() as HttpsURLConnection

        connection.connectTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/79.0.3945.130 Safari/537.36"
        )
        connection.setRequestProperty("Accept-Language", "*")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty(
            "Range",
            "bytes=$deezerStart-${if (end == -1L) "" else end}"
        )
        connection.connect()

        val outResponse: NanoHTTPD.Response =
            newFixedLengthResponse(
                if (isRanged) NanoHTTPD.Response.Status.PARTIAL_CONTENT else NanoHTTPD.Response.Status.OK,
                "audio/mpeg",
                BufferedInputStream(object : FilterInputStream(connection.inputStream) {
                    var counter = deezerStart / 2048
                    var drop = dropBytes

                    @Throws(IOException::class)
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val buffer = ByteArray(2048)
                        var readBytes: Int
                        var totalRead = 0

                        while (totalRead < 2048) {
                            readBytes = `in`.read(buffer, totalRead, 2048 - totalRead)
                            if (readBytes == -1) break
                            totalRead += readBytes
                        }
                        if (totalRead == 0) return -1

                        if (totalRead != 2048) {
                            System.arraycopy(buffer, 0, b, off, totalRead)
                            return totalRead
                        }

                        var processedBuffer = buffer
                        if (counter % 3 == 0L) {
                            processedBuffer = Utils.decryptBlowfish(buffer, key)
                        }

                        if (drop > 0) {
                            val output = 2048 - drop.toInt()
                            System.arraycopy(processedBuffer, drop.toInt(), b, off, output)
                            drop = 0
                            counter++
                            return output
                        }

                        System.arraycopy(processedBuffer, 0, b, off, 2048)
                        counter++
                        return 2048
                    }
                }, 2048),
                connection.contentLengthLong - dropBytes
            )

        if (isRanged) {
            val contentLength = connection.contentLengthLong + deezerStart
            val rangeEnd = if (end == -1L) contentLength - 1 else end
            val range = "bytes $startBytes-$rangeEnd/$contentLength"
            outResponse.addHeader("Content-Range", range)
        }
        outResponse.addHeader("Accept-Ranges", "bytes")

        outResponse
    } catch (e: Exception) {
        e.printStackTrace()
        newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            MIME_PLAINTEXT,
            "Failed getting data!"
        )
    }
}

object AudioStreamManager {
    private var server: LocalAudioWebServer? = null
    private val lock = Any()
    private const val HOSTNAME = "127.0.0.1"

    @Volatile
    private var usedPort = -1

    private fun startServerIfNeeded() {
        synchronized(lock) {
            if (server == null) {
                try {
                    server = LocalAudioWebServer(HOSTNAME, 0).apply {
                        start(SOCKET_READ_TIMEOUT, false)
                    }
                    usedPort = server?.listeningPort ?: -1

                    println("LocalAudioWebServer started on port: $usedPort")
                } catch (e: Exception) {
                    println("Failed to start LocalAudioWebServer: ${e.message}")
                    e.printStackTrace()
                    server = null
                }
            }
        }
    }

    fun addTrack(streamable: Streamable) {
        synchronized(lock) {
            startServerIfNeeded()
            server?.addOrUpdateTrack(streamable)
        }
    }

    fun stopServer() {
        synchronized(lock) {
            server?.stop()
            server = null
            println("LocalAudioWebServer stopped.")
        }
    }

    fun getStreamUrlForTrack(trackId: String): String {
        return "http://$HOSTNAME:$usedPort/stream?trackId=$trackId"
    }
}

