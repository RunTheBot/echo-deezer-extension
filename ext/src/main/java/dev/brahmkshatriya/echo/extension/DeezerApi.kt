package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class DeezerApi(private val session: DeezerSession) {

    init {
        if (session.credentials == null) {
            session.credentials = DeezerCredentials(
                arl = "",
                sid = "",
                token = "",
                userId = "",
                licenseToken = "",
                email = "",
                pass = ""
            )
        }
    }

    private val language: String
        get() = session.settings?.getString("lang") ?: Locale.getDefault().toLanguageTag()

    private val country: String
        get() = session.settings?.getString("country") ?: Locale.getDefault().country

    private val credentials: DeezerCredentials
        get() = session.credentials ?: throw IllegalStateException("DeezerCredentials not initialized")

    private val arl: String
        get() = credentials.arl

    private val sid: String
        get() = credentials.sid

    private val token: String
        get() = credentials.token

    private val userId: String
        get() = credentials.userId

    private val licenseToken: String
        get() = credentials.licenseToken

    private val email: String
        get() = credentials.email

    private val pass: String
        get() = credentials.pass

    private fun createOkHttpClient(useProxy: Boolean): OkHttpClient {
        return OkHttpClient.Builder().apply {
            addInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                if (originalResponse.header("Content-Encoding") == "gzip") {
                    val gzipSource = GZIPInputStream(originalResponse.body.byteStream())
                    val decompressedBody = gzipSource.readBytes()
                        .toResponseBody(originalResponse.body.contentType())
                    originalResponse.newBuilder().body(decompressedBody).build()
                } else {
                    originalResponse
                }
            }
            if (useProxy && session.settings?.getBoolean("proxy") == true) {
                sslSocketFactory(createTrustAllSslSocketFactory(), createTrustAllTrustManager())
                hostnameVerifier { _, _ -> true }
                proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved("uk.proxy.murglar.app", 3128)
                    )
                )
            }
        }.build()
    }

    private fun createTrustAllSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(createTrustAllTrustManager())
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext.socketFactory
    }

    @Suppress("TrustAllX509TrustManager", "CustomX509TrustManager")
    private fun createTrustAllTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    }

    private val client: OkHttpClient get() = createOkHttpClient(useProxy = true)
    private val clientNP: OkHttpClient get() = createOkHttpClient(useProxy = false)

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private fun getHeaders(method: String? = ""): Headers {
        return Headers.Builder().apply {
            add("Accept", "*/*")
            add("Accept-Charset", "utf-8,ISO-8859-1;q=0.7,*;q=0.3")
            add("Accept-Encoding", "gzip")
            add("Accept-Language", language)
            add("Cache-Control", "max-age=0")
            add("Connection", "keep-alive")
            add("Content-Language", language)
            add("Content-Type", "application/json; charset=utf-8")
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            if (method != "user.getArl") {
                add("Cookie", "arl=$arl; sid=$sid")
            } else {
                add("Cookie", "sid=$sid")
            }
        }.build()
    }

    private suspend fun callApi(method: String, params: JsonObject = buildJsonObject { }, gatewayInput: String? = ""): String = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.deezer.com")
            .addPathSegment("ajax")
            .addPathSegment("gw-light.php")
            .addQueryParameter("method", method)
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", token)
            .apply {
                if (!gatewayInput.isNullOrEmpty()) {
                    addQueryParameter("gateway_input", gatewayInput)
                }
            }
            .build()

        val requestBody = json.encodeToString(params).toRequestBody()
        val request = Request.Builder()
            .url(url)
            .apply {
                if (method != "user.getArl") {
                    post(requestBody)
                } else {
                    get()
                }
                headers(getHeaders(method))
            }
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body.string()

        if (!response.isSuccessful) {
            throw Exception("API call failed with status code ${response.code}: $responseBody")
        }

        if (method == "deezer.getUserData") {
            response.headers.forEach {
                if (it.second.startsWith("sid=")) {
                    session.updateCredentials(sid = it.second.substringAfter("sid=").substringBefore(";"))
                }
            }
        }

        if (responseBody.contains("\"VALID_TOKEN_REQUIRED\":\"Invalid CSRF token\"")) {
            if (email.isEmpty() && pass.isEmpty()) {
                session.isArlExpired(true)
                throw Exception("Please re-login (Best use User + Pass method)")
            } else {
                session.isArlExpired(false)
                val userList = DeezerExtension().onLogin(email, pass)
                DeezerExtension().onSetLoginUser(userList.first())
                return@withContext callApi(method, params, gatewayInput)
            }
        }

        responseBody
    }

    suspend fun makeUser(email: String = "", pass: String = ""): List<User> {
        val userList = mutableListOf<User>()
        val jsonData = callApi("deezer.getUserData")
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

    suspend fun getArlByEmail(mail: String, password: String) {
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
        val responseJson = getToken(params)
        val apiResponse = json.decodeFromString<JsonObject>(responseJson)
        session.updateCredentials(token = apiResponse.jsonObject["access_token"]!!.jsonPrimitive.content)

        // Get ARL
        val arlResponse = callApi("user.getArl")
        val arlObject = json.decodeFromString<JsonObject>(arlResponse)
        session.updateCredentials(arl = arlObject["results"]!!.jsonPrimitive.content)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    private fun getToken(params: Map<String, String>): String {
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

    fun getMP3MediaUrl(track: Track): JsonObject {
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
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("dzmedia.fly.dev")
            .addPathSegment("get_url")
            .build()

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
            .url(url)
            .post(requestBody)
            .build()

        val response = clientNP.newCall(request).execute()
        val responseBody = response.body.string()

        return json.decodeFromString<JsonObject>(responseBody)
    }

    suspend fun search(query: String): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageSearch",
            params = buildJsonObject {
                put("nb", 128)
                put("query", query)
                put("start", 0)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun searchSuggestions(query: String): JsonObject {
        val jsonData = callApi(
            method = "search_getSuggestedQueries",
            params = buildJsonObject {
                put("QUERY", query)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun setSearchHistory(query: String) {
        callApi(
            method = "user.addEntryInSearchHistory",
            params = buildJsonObject {
                putJsonObject("ENTRY") {
                    put("query", query)
                    put("type", "query")
                }
            }
        )
    }

    suspend fun getSearchHistory(): JsonObject {
        val jsonData = callApi(
            method = "deezer.userMenu"
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun deleteSearchHistory() {
        callApi(
            method = "user.clearSearchHistory",
            params = buildJsonObject {
                put("USER_ID", userId)
            }
        )
    }

    suspend fun track(tracks: Array<Track>): JsonObject {
        val jsonData = callApi(
            method = "song.getListData",
            params = buildJsonObject {
                put("sng_ids", buildJsonArray { tracks.forEach { add(it.id) } })
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getTracks(): JsonObject {
        val jsonData = callApi(
            method = "favorite_song.getList",
            params = buildJsonObject {
                put("user_id", userId)
                put("tab", "loved")
                put("nb", 10000)
                put("start", 0)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun addFavoriteTrack(id: String) {
        callApi(
            method = "favorite_song.add",
            params = buildJsonObject {
                put("SNG_ID", id)
            }
        )
    }

    suspend fun removeFavoriteTrack(id: String) {
        callApi(
            method = "favorite_song.remove",
            params = buildJsonObject {
                put("SNG_ID", id)
            }
        )
    }

    suspend fun artist(id: String): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageArtist",
            params = buildJsonObject {
                put("art_id", id)
                put ("lang", language.substringBefore("-"))
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getArtists(): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("nb", 40)
                put ("tab", "artists")
                put("user_id", userId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun followArtist(id: String) {
        callApi(
            method = "artist.addFavorite",
            params = buildJsonObject {
                put("ART_ID", id)
                putJsonObject("CTXT") {
                    put("id", id)
                    put("t", "artist_smartradio")
                }
            }
        )
    }

    suspend fun unfollowArtist(id: String) {
        callApi(
            method = "artist.deleteFavorite",
            params = buildJsonObject {
                put("ART_ID", id)
                putJsonObject("CTXT") {
                    put("id", id)
                    put("t", "artist_smartradio")
                }
            }
        )
    }

    suspend fun album(album: Album): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageAlbum",
            params = buildJsonObject {
                put("alb_id", album.id)
                put("header", true)
                put("lang", language.substringBefore("-"))
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getAlbums(): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("user_id", userId)
                put("tab", "albums")
                put("nb", 50)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun addFavoriteAlbum(id: String) {
        callApi(
            method = "album.addFavorite",
            params = buildJsonObject {
                put("ALB_ID", id)
            }
        )
    }

    suspend fun removeFavoriteAlbum(id: String) {
        callApi(
            method = "album.deleteFavorite",
            params = buildJsonObject {
                put("ALB_ID", id)
            }
        )
    }

    suspend fun show(album: Album): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageShow",
            params = buildJsonObject {
                put("country", language.substringAfter("-"))
                put("lang", language.substringBefore("-"))
                put("nb", album.tracks)
                put("show_id", album.id)
                put("start", 0)
                put("user_id", userId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getShows(): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("user_id", userId)
                put("tab", "podcasts")
                put("nb", 2000)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun playlist(playlist: Playlist): JsonObject {
        val jsonData = callApi(
            method = "deezer.pagePlaylist",
            params = buildJsonObject {
                put("playlist_id", playlist.id)
                put ("lang", language.substringBefore("-"), )
                put("nb", playlist.tracks)
                put("tags", true)
                put("start", 0)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun getPlaylists(): JsonObject {
        val jsonData = callApi(
            method = "deezer.pageProfile",
            params = buildJsonObject {
                put("user_id", userId)
                put ("tab", "playlists")
                put("nb", 100)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun addFavoritePlaylist(id: String) {
        callApi(
            method = "playlist.addFavorite",
            params = buildJsonObject {
                put("PARENT_PLAYLIST_ID", id)
            }
        )
    }

    suspend fun removeFavoritePlaylist(id: String) {
        callApi(
            method = "playlist.deleteFavorite",
            params = buildJsonObject {
                put("PLAYLIST_ID", id)
            }
        )
    }

    suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>) {
        callApi(
            method = "playlist.addSongs",
            params = buildJsonObject {
                put("playlist_id", playlist.id)
                put("songs", buildJsonArray {
                    tracks.forEach { track ->
                        add(buildJsonArray { add(track.id); add(0) })
                    }
                })
            }
        )
    }

    suspend fun removeFromPlaylist(playlist: Playlist, tracks: List<Track>, indexes: List<Int>) = coroutineScope {
        val trackIds = tracks.map { it.id }
        val ids = indexes.map { index -> trackIds[index] }

        callApi(
            method = "playlist.deleteSongs",
            params = buildJsonObject {
                put("playlist_id", playlist.id)
                put("songs", buildJsonArray {
                    ids.forEach { id ->
                        add(buildJsonArray { add(id); add(0) })
                    }
                })
            }
        )
    }

    suspend fun createPlaylist(title: String, description: String? = ""): JsonObject {
        val jsonData = callApi(
            method = "playlist.create",
            params = buildJsonObject {
                put("title", title)
                put ("description", description, )
                put("songs", buildJsonArray {})
                put("status", 0)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun deletePlaylist(id: String) {
        callApi(
            method = "playlist.delete",
            params = buildJsonObject {
                put("playlist_id", id)
            }
        )
    }

    suspend fun updatePlaylist(id: String, title: String, description: String? = "") {
        callApi(
            method = "playlist.update",
            params = buildJsonObject {
                put("description", description)
                put ("playlist_id", id)
                put("status", 0)
                put("title", title)
            }
        )
    }

    suspend fun updatePlaylistOrder(playlistId: String, ids: MutableList<String>) {
        callApi(
            method = "playlist.updateOrder",
            params = buildJsonObject {
                put("order", buildJsonArray { ids.forEach { add(it) } })
                put ("playlist_id", playlistId)
                put("position", 0)
            }
        )
    }

    suspend fun homePage(): JsonObject {
        val jsonData = callApi(
            method = "page.get",
            gatewayInput = """
                {"PAGE":"home","VERSION":"2.5","SUPPORT":{"ads":[],"deeplink-list":["deeplink"],"event-card":["live-event"],"grid-preview-one":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid-preview-two":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-list":["track","song"],"item-highlight":["radio"],"large-card":["album","external-link","playlist","show","video-link"],"list":["episode"],"mini-banner":["external-link"],"slideshow":["album","artist","channel","external-link","flow","livestream","playlist","show","smarttracklist","user","video-link"],"small-horizontal-grid":["flow"],"long-card-horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"filterable-grid":["flow"]},"LANG":"${language.substringBefore("-")}","OPTIONS":["deeplink_newsandentertainment","deeplink_subscribeoffer"]}
            """.trimIndent()
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun browsePage(): JsonObject {
        val jsonData = callApi(
            method = "page.get",
            gatewayInput = """
                {"PAGE":"channels/explore/explore-tab","VERSION":"2.5","SUPPORT":{"ads":[],"deeplink-list":["deeplink"],"event-card":["live-event"],"grid-preview-one":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid-preview-two":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-list":["track","song"],"item-highlight":["radio"],"large-card":["album","external-link","playlist","show","video-link"],"list":["episode"],"message":["call_onboarding"],"mini-banner":["external-link"],"slideshow":["album","artist","channel","external-link","flow","livestream","playlist","show","smarttracklist","user","video-link"],"small-horizontal-grid":["flow"],"long-card-horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"filterable-grid":["flow"]},"LANG":"${language.substringBefore("-")}","OPTIONS":["deeplink_newsandentertainment","deeplink_subscribeoffer"]}
            """.trimIndent()
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun channelPage(target: String): JsonObject {
        val jsonData = callApi(
            method = "page.get",
            gatewayInput = """
                {"PAGE":"${target.substringAfter("/")}","VERSION":"2.5","SUPPORT":{"ads":[],"deeplink-list":["deeplink"],"event-card":["live-event"],"grid-preview-one":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid-preview-two":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"horizontal-list":["track","song"],"item-highlight":["radio"],"large-card":["album","external-link","playlist","show","video-link"],"list":["episode"],"message":["call_onboarding"],"mini-banner":["external-link"],"slideshow":["album","artist","channel","external-link","flow","livestream","playlist","show","smarttracklist","user","video-link"],"small-horizontal-grid":["flow"],"long-card-horizontal-grid":["album","artist","artistLineUp","channel","livestream","flow","playlist","radio","show","smarttracklist","track","user","video-link","external-link"],"filterable-grid":["flow"]},"LANG":"${language.substringBefore("-")}","OPTIONS":["deeplink_newsandentertainment","deeplink_subscribeoffer"]}
            """.trimIndent()
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun updateCountry() {
        callApi(
            method = "user.updateRecommendationCountry",
            params = buildJsonObject {
                put("RECOMMENDATION_COUNTRY", country)
            }
        )
    }

    fun lyrics(id: String): JsonObject {
        val request = Request.Builder()
            .url("https://auth.deezer.com/login/arl?jo=p&rto=c&i=c")
            .post("".toRequestBody())
            .headers(Headers.headersOf("Cookie", "arl=$arl; sid=$sid"))
            .build()
        val response = clientNP.newCall(request).execute()
        val jsonObject = json.decodeFromString<JsonObject>(response.body.string())

        val jwt = jsonObject["jwt"]?.jsonPrimitive?.content
        val params = buildJsonObject {
            put("operationName", "SynchronizedTrackLyrics")
            put("query", "query SynchronizedTrackLyrics(\$trackId: String!) {\n  track(trackId: \$trackId) {\n    id\n    isExplicit\n    lyrics {\n      id\n      copyright\n      text\n      writers\n      synchronizedLines {\n        lrcTimestamp\n        line\n        milliseconds\n        duration\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}")
            putJsonObject("variables") {
                put("trackId", id)
            }
        }
        val pipeRequest = Request.Builder()
            .url("https://pipe.deezer.com/api")
            .post(json.encodeToString(params).toRequestBody())
            .headers(Headers.headersOf("Authorization", "Bearer $jwt", "Content-Type", "application/json"))
            .build()
        val pipeResponse = clientNP.newCall(pipeRequest).execute()
        return json.decodeFromString<JsonObject>(pipeResponse.body.string())
    }

    suspend fun log(track: Track) = withContext(Dispatchers.IO) {
        val id = track.id
        val next = track.extras["NEXT"]
        val ctxtT: String
        val ctxtId = when {
            !track.extras["album_id"].isNullOrEmpty() -> {
                ctxtT = "album_page"
                track.extras["album_id"]
            }
            !track.extras["playlist_id"].isNullOrEmpty() -> {
                ctxtT = "playlist_page"
                track.extras["playlist_id"]
            }
            !track.extras["artist_id"].isNullOrEmpty() -> {
                ctxtT = "up_next_artist"
                track.extras["artist_id"]
            }
            !track.extras["user_id"].isNullOrEmpty() -> {
                ctxtT = "dynamic_page_user_radio"
                userId
            }
            else -> {
                ctxtT = ""
                ""
            }
        }
        callApi(
            method = "log.listen",
            params = buildJsonObject {
                putJsonObject("next_media") {
                    putJsonObject("media") {
                        put("id", next)
                        put("type", "song")
                    }
                }
                putJsonObject("params") {
                    putJsonObject("ctxt") {
                        put("id", ctxtId)
                        put("t", ctxtT)
                    }
                    putJsonObject("dev") {
                        put("t", 0)
                        put("v", "10020240822130111")
                    }
                    put("is_shuffle", false)
                    putJsonArray("ls") {}
                    put("lt", 1)
                    putJsonObject("media") {
                        put("format", "MP3_128")
                        put("id", id)
                        put("type", "song")
                    }
                    putJsonObject("payload") {}
                    putJsonObject("stat") {
                        put("pause", 0)
                        put("seek", 0)
                        put("sync", 0)
                    }
                    put("stream_id", UUID.randomUUID().toString())
                    put("timestamp", System.currentTimeMillis() / 1000)
                    put("ts_listen", System.currentTimeMillis() / 1000)
                    put("type", 0)
                }
            }
        )
    }

    suspend fun mix(id: String): JsonObject {
        val jsonData = callApi(
            method = "song.getSearchTrackMix",
            params = buildJsonObject {
                put("sng_id", id)
                put("start_with_input_track", false)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun mixArtist(id: String): JsonObject {
        val jsonData = callApi(
            method = "smart.getSmartRadio",
            params = buildJsonObject {
                put("art_id", id)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun radio(trackId: String, artistId: String): JsonObject {
        val jsonData = callApi(
            method = "radio.getUpNext",
            params = buildJsonObject {
                put("art_id", artistId)
                put("limit", 10)
                put("sng_id", trackId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }

    suspend fun flow(id: String): JsonObject {
        val jsonData = callApi(
            method = "radio.getUserRadio",
            params = buildJsonObject {
                if (id != "default") {
                    put("config_id", id)
                }
                put("user_id", userId)
            }
        )
        return json.decodeFromString<JsonObject>(jsonData)
    }
}