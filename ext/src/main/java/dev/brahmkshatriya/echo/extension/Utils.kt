package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.coroutines.executeAsync
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

    private val cipherCache = ConcurrentHashMap<String, Cipher>()

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

    private fun createCipher(blowfishKey: String): Cipher {
        return Cipher.getInstance("BLOWFISH/CBC/NoPadding").apply {
            val secretKeySpec = SecretKeySpec(blowfishKey.toByteArray(), "Blowfish")
            init(Cipher.DECRYPT_MODE, secretKeySpec, secretIvSpec)
        }
    }

    fun decryptBlowfish(chunk: ByteArray, blowfishKey: String): ByteArray {
        val cipher = cipherCache.computeIfAbsent(blowfishKey) { createCipher(it) }
        return cipher.doFinal(chunk)
    }

    fun getContentLength(url: String, client: OkHttpClient): Long {
        var totalLength = 0L
        val request = Request.Builder().url(url).head().build()
        val response = client.newCall(request).execute()
        totalLength += response.header("Content-Length")?.toLong() ?: 0L
        response.close()
        return totalLength
    }
}

private fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { String.format("%02X", it) }
}

fun String.toMD5(): String {
    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray(Charsets.ISO_8859_1))
    return bytesToHex(bytes).lowercase()
}

@Suppress("NewApi")
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun getByteStreamAudio(
    scope: CoroutineScope,
    streamable: Streamable,
    client: OkHttpClient
): Streamable.Media {
    val url = streamable.id
    val contentLength = Utils.getContentLength(url, client)
    val key = streamable.extra["key"] ?: ""

    val request = Request.Builder().url(url).build()

    val clientWithTimeouts = client.newBuilder()
        .readTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    val byteChannel = ByteChannel()
    val response = clientWithTimeouts.newCall(request).executeAsync()

    scope.launch(Dispatchers.IO) {
        try {
            response.body.byteStream().use { byteStream ->
                try {
                    var totalBytesRead = 0L
                    var counter = 0

                    while (totalBytesRead < contentLength) {
                        val buffer = ByteArray(2048)
                        var bytesRead: Int
                        var totalRead = 0

                        while (totalRead < buffer.size) {
                            bytesRead =
                                byteStream.read(buffer, totalRead, buffer.size - totalRead)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                        }

                        if (totalRead == 0) break

                        try {
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
                        } catch (e: IOException) {
                            println("Channel closed while writing, aborting.")
                            break
                        }
                        totalBytesRead += totalRead
                        counter++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw IOException("Error while reading/writing stream: ${e.message}", e)
                } finally {
                    byteChannel.close()
                }
            }
        } catch (e: IOException) {
            println("Exception during decryption or streaming: ${e.message}")
        }
    }

    return Streamable.Audio.Channel(
        channel = byteChannel,
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