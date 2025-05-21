package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object LocalAudioServer {

    private val trackMap = ConcurrentHashMap<String, Pair<Streamable, String>>()

    private val serverStarted = AtomicBoolean(false)
    private lateinit var serverSocket: ServerSocket
    private var usedPort: Int = -1

    private fun startServerIfNeeded(scope: CoroutineScope, client: OkHttpClient) {
        if (serverStarted.compareAndSet(false, true)) {
            serverSocket = ServerSocket(0, 50)
            usedPort = serverSocket.localPort

            scope.launch(Dispatchers.IO) {
                println("LocalAudioServer started on port: $usedPort")
                while (isActive) {
                    try {
                        val clientSocket = serverSocket.accept()
                        launch {
                            handleClient(clientSocket, client)
                        }
                    } catch (e: IOException) {
                        if (isActive) e.printStackTrace()
                    }
                }
            }
        }
    }

    fun addTrack(streamable: Streamable, scope: CoroutineScope, client: OkHttpClient) {
        trackMap[streamable.id] = streamable to (streamable.extras["key"] ?: "")
        startServerIfNeeded(scope, client)
    }

    fun getStreamUrlForTrack(trackId: String, scope: CoroutineScope, client: OkHttpClient): String {
        startServerIfNeeded(scope, client)
        return "http://127.0.0.1:$usedPort/stream?trackId=$trackId"
    }

    private fun handleClient(socket: Socket, client: OkHttpClient) {
        socket.use { s ->
            s.soTimeout = 10_000
            val input = BufferedReader(InputStreamReader(s.getInputStream()))
            val output = BufferedOutputStream(s.getOutputStream())

            val requestLine = input.readLine() ?: return
            val (method, path) = parseRequestLine(requestLine) ?: run {
                sendResponse(output, 400, "Bad Request", "Invalid request line.")
                return
            }

            if (method != "GET") {
                sendResponse(output, 405, "Method Not Allowed", "Only GET supported.")
                return
            }

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isBlank()) break
                val idx = line.indexOf(":")
                if (idx != -1) {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    headers[key.lowercase()] = value
                }
            }

            val (requestPath, queryString) = splitPathQuery(path)
            if (requestPath != "/stream") {
                sendResponse(output, 404, "Not Found", "Unknown path.")
                return
            }
            val queryParams = parseQueryString(queryString)
            val trackId = queryParams["trackId"]
            if (trackId.isNullOrEmpty()) {
                sendResponse(output, 400, "Bad Request", "Missing trackId parameter.")
                return
            }

            val (streamable, key) = trackMap[trackId]
                ?: run {
                    sendResponse(output, 404, "Not Found", "Track not found.")
                    return
                }

            val rangeHeader = headers["range"]
            var startBytes = 0L
            var endBytes = -1L
            var isRanged = false

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                isRanged = true
                val ranges = rangeHeader.removePrefix("bytes=").split("-")
                startBytes = ranges[0].toLongOrNull() ?: 0
                if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                    endBytes = ranges[1].toLongOrNull() ?: -1
                }
            }

            streamDeezer(client, output, isRanged, startBytes, endBytes, key, streamable.id)
        }
    }

    private fun streamDeezer(
        client: OkHttpClient,
        output: BufferedOutputStream,
        isRanged: Boolean,
        startBytes: Long,
        endBytes: Long,
        key: String,
        deezerUrl: String
    ) {
        var deezerStart = startBytes - (startBytes % 2048)
        val dropBytes = startBytes % 2048

        val rangeValue = if (endBytes == -1L) {
            "bytes=$deezerStart-"
        } else {
            "bytes=$deezerStart-$endBytes"
        }

        val request = Request.Builder()
            .url(deezerUrl)
            .header("Range", rangeValue)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = if (isRanged) 206 else 200
                val reason = if (isRanged) "Partial Content" else "OK"
                val contentType = response.header("Content-Type") ?: "audio/mpeg"
                val contentLength = response.body.contentLength()

                val headers = mutableListOf(
                    "HTTP/1.1 $code $reason",
                    "Content-Type: $contentType",
                    "Accept-Ranges: bytes"
                )
                if (isRanged) {
                    val totalLen = deezerStart + contentLength - 1
                    val rangeEnd = if (endBytes == -1L) totalLen else endBytes
                    val contentRange = "bytes $startBytes-$rangeEnd/${deezerStart + contentLength}"
                    headers.add("Content-Range: $contentRange")
                }

                if (contentLength >= 0) {
                    val computedLength = (contentLength - dropBytes).coerceAtLeast(0)
                    headers.add("Content-Length: $computedLength")
                }

                headers.add("")
                val headerBytes = headers.joinToString("\r\n").toByteArray()
                output.write(headerBytes)
                output.write("\r\n".toByteArray())

                response.body.byteStream().use { responseStream ->
                    val bufferedIn = BufferedInputStream(
                        object : FilterInputStream(responseStream) {
                            var counter = deezerStart / 2048
                            var drop = dropBytes

                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                val chunkSize = 2048
                                val buffer = ByteArray(chunkSize)
                                var totalRead = 0
                                while (totalRead < chunkSize) {
                                    val r = `in`.read(buffer, totalRead, chunkSize - totalRead)
                                    if (r == -1) break
                                    totalRead += r
                                }
                                if (totalRead == 0) return -1

                                var processed = buffer
                                if (totalRead == chunkSize && counter % 3 == 0L) {
                                    processed = Utils.decryptBlowfish(buffer, key)
                                }

                                if (drop > 0 && totalRead == chunkSize) {
                                    val toWrite = (chunkSize - drop).toInt()
                                    System.arraycopy(processed, drop.toInt(), b, off, toWrite)
                                    drop = 0
                                    counter++
                                    return toWrite
                                }

                                System.arraycopy(processed, 0, b, off, totalRead)
                                if (totalRead == chunkSize) counter++
                                return totalRead
                            }
                        },
                        2048
                    )

                    val buf = ByteArray(16_384)
                    while (true) {
                        val read = bufferedIn.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                    }
                }
                output.flush()
            }
        } catch (e: IOException) {
            if (e is SocketException && e.message?.contains("Broken pipe") == true) {
                println("Client closed connection: ${e.message}")
            } else {
                e.printStackTrace()
            }
        } finally {
            output.closeQuietly()
        }
    }

    private fun parseRequestLine(line: String): Triple<String, String, String>? {
        val parts = line.split(" ")
        if (parts.size < 3) return null
        return Triple(parts[0], parts[1], parts[2])
    }


    private fun splitPathQuery(path: String): Pair<String, String?> {
        val idx = path.indexOf('?')
        return if (idx != -1) {
            val p = path.substring(0, idx)
            val q = path.substring(idx + 1)
            p to q
        } else {
            path to null
        }
    }

    private fun parseQueryString(qs: String?): Map<String, String> {
        if (qs.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        val pairs = qs.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), Charsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8.name())
                result[key] = value
            }
        }
        return result
    }

    private fun sendResponse(
        output: OutputStream,
        code: Int,
        reason: String,
        message: String
    ) {
        val headers = """
            HTTP/1.1 $code $reason
            Content-Type: text/plain
            Content-Length: ${message.toByteArray().size}
            
        """.trimIndent() + "\r\n"
        output.write(headers.toByteArray())
        output.write(message.toByteArray())
        output.flush()
    }
}