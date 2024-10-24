package dev.brahmkshatriya.echo.extension.api

import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigInteger
import java.security.MessageDigest

class DeezerLogin(
    private val deezerApi: DeezerApi,
    private val json: Json,
    private val session: DeezerSession,
    private val client: OkHttpClient
) {

    suspend fun makeUser(email: String = "", pass: String = "", arl: String, sid: String): List<User> {
        val userList = mutableListOf<User>()
        val jsonData = deezerApi.callApi("deezer.getUserData")
        val jObject = json.decodeFromString<JsonObject>(jsonData)
        val userResults = jObject["results"]!!
        val userObject = userResults.jsonObject["USER"]!!
        val token = userResults.jsonObject["checkForm"]!!.jsonPrimitive.content
        val userId = userObject.jsonObject["USER_ID"]!!.jsonPrimitive.content
        val licenseToken = userObject.jsonObject["OPTIONS"]!!.jsonObject["license_token"]!!.jsonPrimitive.content
        val name = userObject.jsonObject["BLOG_NAME"]!!.jsonPrimitive.content
        val cover = userObject.jsonObject["USER_PICTURE"]!!.jsonPrimitive.content
        val user = User(
            id = userId,
            name = name,
            cover = "https://e-cdns-images.dzcdn.net/images/user/$cover/100x100-000000-80-0-0.jpg".toImageHolder(),
            extras = mapOf(
                "arl" to arl,
                "user_id" to userId,
                "sid" to sid,
                "token" to token,
                "license_token" to licenseToken,
                "email" to email,
                "pass" to pass
            )
        )
        userList.add(user)
        return userList
    }

    suspend fun getArlByEmail(mail: String, password: String, sid: String) {
        // Get SID
        getSid()

        val clientId = "447462"
        val clientSecret = "a83bf7f38ad2f137e444727cfc3775cf"
        val md5Password = md5(password)

        val params = mapOf(
            "app_id" to clientId,
            "login" to mail,
            "password" to md5Password,
            "hash" to md5(clientId + mail + md5Password + clientSecret)
        )

        // Get access token
        val responseJson = getToken(params, sid)
        val apiResponse = json.decodeFromString<JsonObject>(responseJson)
        session.updateCredentials(token = apiResponse.jsonObject["access_token"]!!.jsonPrimitive.content)

        // Get ARL
        val arlResponse = deezerApi.callApi("user.getArl")
        val arlObject = json.decodeFromString<JsonObject>(arlResponse)
        session.updateCredentials(arl = arlObject["results"]!!.jsonPrimitive.content)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    private fun getToken(params: Map<String, String>, sid: String): String {
        val url = "https://connect.deezer.com/oauth/user_auth.php"
        val httpUrl = url.toHttpUrlOrNull()!!.newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .headers(
                Headers.headersOf(
                    "Cookie", "sid=$sid",
                    "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                )
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected code $response")
            return response.body.string()
        }
    }

    fun getSid() {
        val url = "https://www.deezer.com/"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        response.headers.forEach {
            if (it.second.startsWith("sid=")) {
                session.updateCredentials(sid = it.second.substringAfter("sid=").substringBefore(";"))
            }
        }
    }
}