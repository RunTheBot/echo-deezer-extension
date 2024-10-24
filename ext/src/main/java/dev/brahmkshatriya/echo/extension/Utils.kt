package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Utils {
    private const val SECRET = "g4el58wc0zvf9na1"
    private val secretIvSpec = IvParameterSpec(byteArrayOf(0,1,2,3,4,5,6,7))

    private val keySpecCache = ConcurrentHashMap<String, SecretKeySpec>()

    private fun bitwiseXor(firstVal: Char, secondVal: Char, thirdVal: Char): Char {
        return (firstVal.code xor secondVal.code xor thirdVal.code).toChar()
    }

    fun createBlowfishKey(trackId: String): String {
        val trackMd5Hex = trackId.toMD5()
        val blowfishKey = StringBuilder()

        for (i in 0..15) {
            val nextChar = bitwiseXor(trackMd5Hex[i], trackMd5Hex[i + 16], SECRET[i])
            blowfishKey.append(nextChar)
        }

        return blowfishKey.toString()
    }

    private fun getSecretKeySpec(blowfishKey: String): SecretKeySpec {
        return keySpecCache.computeIfAbsent(blowfishKey) {
            SecretKeySpec(blowfishKey.toByteArray(), "Blowfish")
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02X", it) }
    }

    private fun String.toMD5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray(Charsets.ISO_8859_1))
        return bytesToHex(bytes).lowercase()
    }

    fun decryptBlowfish(chunk: ByteArray, blowfishKey: String): ByteArray {
        val secretKeySpec = getSecretKeySpec(blowfishKey)
        val cipher = Cipher.getInstance("BLOWFISH/CBC/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKeySpec, secretIvSpec)
        }
        return cipher.doFinal(chunk)
    }

    fun getContentLength(url: String, client: OkHttpClient): Long {
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            return response.header("Content-Length")?.toLong() ?: 0L
        }
    }
}

suspend fun getByteChannel(
    scope: CoroutineScope,
    streamable: Streamable,
    client: OkHttpClient,
    contentLength: Long
): ByteChannel {
    val url = streamable.id
    val key = streamable.extra["key"] ?: ""

    val byteChannel = ByteChannel(true)
    var lastActivityTime = System.currentTimeMillis()

    scope.launch {
        val clientWithTimeouts = client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build()

        var totalBytesRead = 0L
        var counter = 0

        while (totalBytesRead < contentLength && !byteChannel.isClosedForWrite) {
            val requestBuilder = Request.Builder().url(url)

            if (totalBytesRead > 0) {
                requestBuilder.header("Range", "bytes=$totalBytesRead-")
            }

            val request = requestBuilder.build()

            val response = clientWithTimeouts.newCall(request).execute()

            response.body.byteStream().use { byteStream ->
                var shouldReopen = false

                while (totalBytesRead < contentLength && !shouldReopen) {
                    if (System.currentTimeMillis() - lastActivityTime > 300_000L) {
                        shouldReopen = true
                        break
                    }

                    val buffer = ByteArray(2048)
                    var bytesRead: Int
                    var totalRead = 0

                    try {
                        while (totalRead < buffer.size) {
                            bytesRead = byteStream.read(buffer, totalRead, buffer.size - totalRead)
                            if (bytesRead == -1) {
                                shouldReopen = true
                                break
                            }
                            totalRead += bytesRead

                            lastActivityTime = System.currentTimeMillis()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        shouldReopen = true
                        break
                    }

                    if (totalRead == 0) {
                        shouldReopen = true
                        break
                    }

                    try {
                        if (System.currentTimeMillis() - lastActivityTime > 300_000L) {
                            shouldReopen = true
                            break
                        }

                        if (totalRead != 2048) {
                            byteChannel.writeFully(buffer, 0, totalRead)
                        } else {
                            if ((counter % 3) == 0) {
                                val decryptedChunk = Utils.decryptBlowfish(buffer, key)
                                byteChannel.writeFully(decryptedChunk, 0, totalRead)
                            } else {
                                byteChannel.writeFully(buffer, 0, totalRead)
                            }
                        }

                        lastActivityTime = System.currentTimeMillis()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        println("Exception occurred while writing to channel: ${e.message}")
                        shouldReopen = true
                        break
                    }
                    totalBytesRead += totalRead
                    counter++
                }

                if (!shouldReopen) {
                    response.close()
                }
            }
        }
    }

    return byteChannel
}

suspend fun getByteStreamAudio(
    scope: CoroutineScope,
    streamable: Streamable,
    client: OkHttpClient
): Streamable.Media {
    val contentLength = Utils.getContentLength(streamable.id, client)
    return Streamable.Audio.Channel(
        channel = getByteChannel(scope, streamable, client, contentLength),
        totalBytes = contentLength
    ).toMedia()
}

@Suppress("NewApi", "GetInstance")
fun generateTrackUrl(trackId: String, md5Origin: String, mediaVersion: String, quality: Int): String {
    val magicByte = 164
    val aesKey = "jo6aey6haid2Teih".toByteArray()
    val keySpec = SecretKeySpec(aesKey, "AES")

    val step1 = ByteArrayOutputStream().apply {
        write(md5Origin.toByteArray())
        write(magicByte)
        write(quality.toString().toByteArray())
        write(magicByte)
        write(trackId.toByteArray())
        write(magicByte)
        write(mediaVersion.toByteArray())
    }

    val md5Digest = MessageDigest.getInstance("MD5").digest(step1.toByteArray())
    val md5hex = md5Digest.joinToString("") { "%02x".format(it) }

    val step2 = ByteArrayOutputStream().apply {
        write(md5hex.toByteArray())
        write(magicByte)
        write(step1.toByteArray())
        write(magicByte)
    }

    while (step2.size() % 16 != 0) {
        step2.write(46)
    }

    val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, keySpec)
    }

    val encryptedHex = StringBuilder()
    val step2Bytes = step2.toByteArray()
    for (i in step2Bytes.indices step 16) {
        val block = step2Bytes.copyOfRange(i, i + 16)
        encryptedHex.append(cipher.doFinal(block).joinToString("") { "%02x".format(it) })
    }

    return "https://e-cdns-proxy-${md5Origin[0]}.dzcdn.net/mobile/1/$encryptedHex"
}