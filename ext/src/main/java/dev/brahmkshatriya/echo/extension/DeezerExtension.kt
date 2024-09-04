package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ArtistFollowClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SearchClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ClientException
import dev.brahmkshatriya.echo.common.models.Lyric
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Audio.Companion.toAudio
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toMedia
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSlider
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.DeezerCountries.getDefaultCountryIndex
import dev.brahmkshatriya.echo.extension.DeezerCountries.getDefaultLanguageIndex
import dev.brahmkshatriya.echo.extension.DeezerUtils.settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

class DeezerExtension : ExtensionClient, HomeFeedClient, TrackClient, RadioClient, SearchClient, AlbumClient, ArtistClient,
    ArtistFollowClient, PlaylistClient, LyricsClient, ShareClient, LoginClient.WebView.Cookie,
    LoginClient.UsernamePassword, LoginClient.CustomTextInput, LibraryClient {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val api = DeezerApi()

    override val settingItems: List<Setting> get() = listOf(
        SettingSwitch(
          "Use Proxy",
            "proxy",
            "Use proxy to prevent GEO-Blocking",
            false
        ),
        SettingSwitch(
            "Enable Logging",
            "log",
            "Enables logging to deezer",
            false
        ),
        SettingSwitch(
            "Enable Search History",
            "history",
            "Enables the search history",
            true
        ),
        SettingCategory(
            "Quality",
            "quality",
            mutableListOf(
                SettingList(
                    "Audio Quality",
                    "audio_quality",
                    "Choose your preferred audio quality",
                    mutableListOf("FLAC", "320kbps", "128kbps"),
                    mutableListOf("flac", "320", "128"),
                    1
                ),
                SettingSlider(
                    "Image Quality",
                    "image_quality",
                    "Choose your preferred image quality",
                    120,
                    1920,
                    120
                )
            )
        ),
        SettingCategory(
            "Language & Country",
            "langcount",
            mutableListOf(
                SettingList(
                    "Language",
                    "lang",
                    "Choose your preferred language for loaded stuff",
                    DeezerCountries.languageEntryTitles,
                    DeezerCountries.languageEntryValues,
                    getDefaultLanguageIndex(settings)
                ),
                SettingList(
                    "Country",
                    "country",
                    "Choose your preferred country for browse recommendations",
                    DeezerCountries.countryEntryTitles,
                    DeezerCountries.countryEntryValues,
                    getDefaultCountryIndex(settings)
                )
            )
        ),
    )

    init {
        initializeCredentialsIfNeeded()
    }

    override fun setSettings(settings: Settings) {
        DeezerUtils.settings = settings
    }

    override suspend fun onExtensionSelected() {}

    //<============= HomeTab =============>

    override suspend fun getHomeTabs() = listOf<Tab>()

    override fun getHomeFeed(tab: Tab?): PagedData<MediaItemsContainer> = PagedData.Continuous {
        handleArlExpiration()
        val dataList = mutableListOf<MediaItemsContainer>()
        val sections = api.homePage()["results"]!!.jsonObject["sections"]!!.jsonArray

        coroutineScope {
            sections.map { section ->
                async {
                    val id = section.jsonObject["module_id"]!!.jsonPrimitive.content
                    if (isSupportedSection(id)) {
                        val data = section.toMediaItemsContainer(section.jsonObject["title"]!!.jsonPrimitive.content)
                        dataList.add(data)
                    }
                }
            }.awaitAll()
        }

        Page(dataList, it)
    }


    private fun isSupportedSection(id: String) = listOf(
        "348128f5-bed6-4ccb-9a37-8e5f5ed08a62",
        "8d10a320-f130-4dcb-a610-38baf0c57896",
        "2a7e897f-9bcf-4563-8e11-b93a601766e1",
        "7a65f4ed-71e1-4b6e-97ba-4de792e4af62",
        "25f9200f-1ce0-45eb-abdc-02aecf7604b2",
        "c320c7ad-95f5-4021-8de1-cef16b053b6d",
        "b2e8249f-8541-479e-ab90-cf4cf5896cbc",
        "927121fd-ef7b-428e-8214-ae859435e51c"
    ).contains(id)

    //<============= Library =============>

    private var allTabs: Pair<String, List<MediaItemsContainer>>? = null

    override suspend fun getLibraryTabs(): List<Tab> {
        handleArlExpiration()

        val tabs = listOf(
            Tab("playlists", "Playlists"),
            Tab("albums", "Albums"),
            Tab("tracks", "Tracks"),
            Tab("artists", "Artists"),
            Tab("shows", "Podcasts")
        )

        allTabs = "all" to coroutineScope {
            tabs.map { tab ->
                async {
                    val jsonObject = when (tab.id) {
                        "playlists" -> api.getPlaylists()
                        "albums" -> api.getAlbums()
                        "tracks" -> api.getTracks()
                        "artists" -> api.getArtists()
                        "shows" -> api.getShows()
                        else -> null
                    } ?: return@async null

                    val resultObject = jsonObject["results"]?.jsonObject ?: return@async null
                    val dataArray = when (tab.id) {
                        "playlists", "albums", "artists", "shows" -> {
                            val tabObject = resultObject["TAB"]?.jsonObject?.get(tab.id)?.jsonObject
                            tabObject?.get("data")?.jsonArray
                        }
                        "tracks" -> resultObject["data"]?.jsonArray
                        else -> return@async null
                    }

                    dataArray?.toMediaItemsContainer(tab.name)
                }
            }.awaitAll().filterNotNull() // Wait for all async operations to complete
        }

        return listOf(Tab("all", "All")) + tabs
    }

    override fun getLibraryFeed(tab: Tab?) = PagedData.Single {
        handleArlExpiration()

        val tabId = tab?.id ?: "all"
        val list = when (tabId) {
            "all" -> allTabs?.second ?: emptyList()
            "playlists" -> fetchData { api.getPlaylists() }
            "albums" -> fetchData { api.getAlbums() }
            "tracks" -> fetchData { api.getTracks() }
            "artists" -> fetchData { api.getArtists() }
            "shows" -> fetchData { api.getShows() }
            else -> emptyList()
        }
        list
    }

    private suspend fun fetchData(apiCall: suspend () -> JsonObject): List<MediaItemsContainer> = withContext(Dispatchers.IO) {
        val jsonObject = apiCall()
        val resultObject = jsonObject["results"]?.jsonObject ?: return@withContext emptyList()
        val dataArray = when {
            resultObject["data"] != null -> resultObject["data"]!!.jsonArray
            resultObject["TAB"] != null -> resultObject["TAB"]!!.jsonObject.values.firstOrNull()?.jsonObject?.get("data")?.jsonArray
            else -> null
        }

        dataArray?.mapNotNull { item ->
            item.jsonObject.toEchoMediaItem()?.toMediaItemsContainer()
        } ?: emptyList()
    }

    override suspend fun addTracksToPlaylist(playlist: Playlist, tracks: List<Track>, index: Int, new: List<Track>) {
        handleArlExpiration()
        api.addToPlaylist(playlist, new)
    }

    override suspend fun createPlaylist(title: String, description: String?): Playlist {
        handleArlExpiration()
        val jsonObject = api.createPlaylist(title, description)
        val id = jsonObject["results"]?.jsonPrimitive?.content ?: ""
        val playlist = Playlist(
            id = id,
            title = title,
            description = description,
            isEditable = true
        )
        return playlist
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        handleArlExpiration()
        api.deletePlaylist(playlist.id)
    }

    override suspend fun editPlaylistMetadata(playlist: Playlist, title: String, description: String?) {
        handleArlExpiration()
        api.updatePlaylist(playlist.id, title, description)
    }

    override suspend fun likeTrack(track: Track, liked: Boolean): Boolean {
        handleArlExpiration()
        if(liked) {
            api.addFavoriteTrack(track.id)
            return true
        } else {
            api.removeFavoriteTrack(track.id)
            return false
        }
    }

    override suspend fun listEditablePlaylists(): List<Playlist> {
        handleArlExpiration()
        val playlistList = mutableListOf<Playlist>()
        val jsonObject = api.getPlaylists()
        val resultObject = jsonObject["results"]!!.jsonObject
        val tabObject = resultObject["TAB"]!!.jsonObject
        val playlistObject = tabObject["playlists"]!!.jsonObject
        val dataArray = playlistObject["data"]!!.jsonArray
        dataArray.map {
            val playlist = it.jsonObject.toPlaylist()
            if(playlist.isEditable) {
                playlistList.add(playlist)
            }

        }
        return playlistList
    }

    override suspend fun moveTrackInPlaylist(playlist: Playlist, tracks: List<Track>, fromIndex: Int, toIndex: Int) {
        handleArlExpiration()
        val idArray = tracks.map { it.id }.toMutableList()
        idArray.add(toIndex, idArray.removeAt(fromIndex))
        api.updatePlaylistOrder(playlist.id, idArray)
    }

    override suspend fun removeTracksFromPlaylist(playlist: Playlist, tracks: List<Track>, indexes: List<Int>) {
        handleArlExpiration()
        api.removeFromPlaylist(playlist, tracks, indexes)
    }

    //<============= Search =============>

    override suspend fun quickSearch(query: String?) =
        if (query?.isEmpty() == true) {
            val queryList = mutableListOf<QuickSearchItem.SearchQueryItem>()
            val jsonObject = api.getSearchHistory()
            val resultObject = jsonObject["results"]!!.jsonObject
            val searchObject = resultObject["SEARCH_HISTORY"]?.jsonObject
            val dataArray = searchObject?.get("data")?.jsonArray
            val historyList = dataArray?.mapNotNull { item ->
                val queryItem = item.jsonObject["query"]?.jsonPrimitive?.content
                queryItem?.let { QuickSearchItem.SearchQueryItem(it, true) }
            } ?: emptyList()
            queryList.addAll(historyList)
            val trendingObject = resultObject["TRENDING_QUERIES"]?.jsonObject
            val dataTrendingArray = trendingObject?.get("data")?.jsonArray
            val trendingList = dataTrendingArray?.mapNotNull { item ->
                val queryItem = item.jsonObject["QUERY"]?.jsonPrimitive?.content
                queryItem?.let { QuickSearchItem.SearchQueryItem(it, false) }
            } ?: emptyList()
            queryList.addAll(trendingList)
            queryList
        } else {
            query?.let {
                runCatching {
                    val jsonObject = api.searchSuggestions(it)
                    val resultObject = jsonObject["results"]?.jsonObject
                    val suggestionArray = resultObject?.get("SUGGESTION")?.jsonArray
                    suggestionArray?.mapNotNull { item ->
                        val queryItem = item.jsonObject["QUERY"]?.jsonPrimitive?.content
                        queryItem?.let { QuickSearchItem.SearchQueryItem(it, false) }
                    } ?: emptyList()
                }.getOrElse {
                    emptyList()
                }
            } ?: emptyList()
        }

    override suspend fun deleteSearchHistory(query: QuickSearchItem.SearchQueryItem) {
        api.deleteSearchHistory()
    }

    private var oldSearch: Pair<String, List<MediaItemsContainer>>? = null

    override fun searchFeed(query: String?, tab: Tab?) = PagedData.Single {
        handleArlExpiration()
        query ?: return@Single browseFeed()

        if (history) {
            api.setSearchHistory(query)
        }
        oldSearch?.takeIf { it.first == query && (tab == null || tab.id == "All") }?.second?.let {
            return@Single it
        }

        if (tab?.id == "TOP_RESULT") return@Single emptyList()

        val jsonObject = api.search(query)
        val resultObject = jsonObject["results"]?.jsonObject
        val tabObject = resultObject?.get(tab?.id ?: "")?.jsonObject
        val dataArray = tabObject?.get("data")?.jsonArray

        dataArray?.mapNotNull { item ->
            item.jsonObject.toEchoMediaItem()?.toMediaItemsContainer()
        } ?: emptyList()
    }

    private suspend fun browseFeed(): List<MediaItemsContainer> {
        val dataList = mutableListOf<MediaItemsContainer>()
        api.updateCountry()
        val jsonObject = api.browsePage()
        val resultObject = jsonObject["results"]!!.jsonObject
        val sections = resultObject["sections"]!!.jsonArray
        val jsonData = json.decodeFromString<JsonArray>(sections.toString())
        jsonData.map { section ->
            val id = section.jsonObject["module_id"]!!.jsonPrimitive.content
            // Just for the time being until everything is implemented
            if (id == "67aa1c1b-7873-488d-88a0-55b6596cf4d6" ||
                id == "486313b7-e3c7-453d-ba79-27dc6bea20ce" ||
                id == "1d8dfed4-582f-40e1-b29c-760b44c0301e") {
                val name = section.jsonObject["title"]!!.jsonPrimitive.content
                val data = section.toMediaItemsContainer(name = name)
                dataList.add(data)
            }
        }
        return dataList
    }

    override suspend fun searchTabs(query: String?): List<Tab> {
        handleArlExpiration()
        query ?: return emptyList()

        val jsonObject = api.search(query)
        val resultObject = jsonObject["results"]?.jsonObject
        val orderObject = resultObject?.get("ORDER")?.jsonArray

        val tabs = coroutineScope {
            orderObject?.mapNotNull {
                async {
                    val tabId = it.jsonPrimitive.content
                    if (tabId != "TOP_RESULT" && tabId != "FLOW_CONFIG") {
                        Tab(tabId, tabId.lowercase().capitalize(Locale.ROOT))
                    } else {
                        null
                    }
                }
            }?.awaitAll()?.filterNotNull() ?: emptyList()
        }

        oldSearch = query to tabs.mapNotNull { tab ->
            val name = tab.id
            val tabObject = resultObject?.get(name)?.jsonObject
            val dataArray = tabObject?.get("data")?.jsonArray
            dataArray?.toMediaItemsContainer(name.lowercase().capitalize(Locale.ROOT))
        }
        return listOf(Tab("All", "All")) + tabs
    }


    //<============= Play =============>

    override suspend fun getStreamableMedia(streamable: Streamable): Streamable.Media {
        return if (streamable.quality == 1) {
           streamable.id.toAudio().toMedia()
        } else {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            getByteStreamAudio(scope, streamable, client)
        }
    }

    override suspend fun loadTrack(track: Track) = coroutineScope {
        val newTrack = track.toNewTrack()

        if (track.extras["__TYPE__"] == "show") {
            return@coroutineScope newTrack
        }

        val jsonObjectDeferred = async {
            if (track.extras["FILESIZE_MP3_MISC"] != "0" && track.extras["FILESIZE_MP3_MISC"] != null) {
                api.getMP3MediaUrl(track)
            } else {
                api.getMediaUrl(track, quality)
            }
        }

        val key = Utils.createBlowfishKey(track.id)

        suspend fun fetchTrackData(track: Track): JsonObject {
            val trackObject = api.track(arrayOf(track))
            return trackObject["results"]!!.jsonObject["data"]!!.jsonArray[0].jsonObject
        }

        suspend fun generateUrl(trackId: String, md5Origin: String, mediaVersion: String): String = withContext(Dispatchers.IO) {
            var url = generateTrackUrl(trackId, md5Origin, mediaVersion, 1)
            val request = Request.Builder().url(url).build()
            val code = client.newCall(request).execute().code
            if (code == 403) {
                val fallbackObject = fetchTrackData(track)["FALLBACK"]!!.jsonObject
                val backMd5Origin = fallbackObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val backMediaVersion = fallbackObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                url = generateTrackUrl(trackId, backMd5Origin, backMediaVersion, 1)
            }
            url
        }

        val jsonObject = jsonObjectDeferred.await()
        val url = when {
            jsonObject.toString().contains("Track token has no sufficient rights on requested media") -> {
                val dataObject = fetchTrackData(track)
                val md5Origin = dataObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val mediaVersion = dataObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                generateUrl(track.id, md5Origin, mediaVersion)
            }
            jsonObject["data"]!!.jsonArray.first().jsonObject["media"]?.jsonArray?.isEmpty() == true -> {
                val dataObject = fetchTrackData(track)
                val fallbackObject = dataObject["FALLBACK"]!!.jsonObject
                val backId = fallbackObject["SNG_ID"]?.jsonPrimitive?.content ?: ""
                val fallbackTrack = track.copy(id = backId)
                val newDataObject = fetchTrackData(fallbackTrack)
                val md5Origin = newDataObject["MD5_ORIGIN"]?.jsonPrimitive?.content ?: ""
                val mediaVersion = newDataObject["MEDIA_VERSION"]?.jsonPrimitive?.content ?: ""
                generateUrl(track.id, md5Origin, mediaVersion)
            }
            else -> {
                val dataObject = jsonObject["data"]!!.jsonArray.first().jsonObject
                val mediaObject = dataObject["media"]!!.jsonArray.first().jsonObject
                val sourcesObject = mediaObject["sources"]!!.jsonArray[0]
                sourcesObject.jsonObject["url"]!!.jsonPrimitive.content
            }
        }

        if (log) {
            api.log(track)
        }

        Track(
            id = track.id,
            title = track.title,
            cover = newTrack.cover,
            artists = track.artists,
            streamables = listOf(
                Streamable.audio(
                    id = url,
                    quality = 0,
                    title = track.title,
                    extra = mapOf("key" to key)
                )
            )
        )
    }

    override fun getMediaItems(track: Track): PagedData<MediaItemsContainer> = getMediaItems(track.artists.first())

    //<============= Radio =============>

    override suspend fun radio(track: Track): Playlist {
        return Playlist(
            id = track.id,
            title = track.title,
            cover = track.cover,
            isEditable = false,
            extras = mapOf(
                "radio" to "track"
            )
        )
    }

    override suspend fun radio(album: Album): Playlist {
        val jsonObject = api.album(album)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val lastTrack = songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack()
        return Playlist(
            id = lastTrack.id,
            title = lastTrack.title,
            cover = lastTrack.cover,
            isEditable = false,
            extras = mapOf(
                "radio" to "album",
                "artist" to lastTrack.artists[0].id
            )
        )
    }

    override suspend fun radio(artist: Artist): Playlist {
        TODO("Not yet implemented")
    }

    override suspend fun radio(user: User): Playlist {
        TODO("Not yet implemented")
    }

    override suspend fun radio(playlist: Playlist): Playlist {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val lastTrack = songsObject["data"]!!.jsonArray.reversed()[0].jsonObject.toTrack()
        return Playlist(
            id = lastTrack.id,
            title = lastTrack.title,
            cover = lastTrack.cover,
            isEditable = false,
            extras = mapOf(
                "radio" to "playlist",
                "artist" to lastTrack.artists[0].id
            )
        )
    }

    //<============= Lyrics =============>

    override suspend fun loadLyrics(small: Lyrics) = small

    override fun searchTrackLyrics(clientId: String, track: Track) = PagedData.Single {
        try {
            val jsonObject = api.lyrics(track.id)
            val dataObject = jsonObject["data"]!!.jsonObject
            val trackObject = dataObject["track"]!!.jsonObject
            val lyricsObject = trackObject["lyrics"]!!.jsonObject
            val lyricsId = lyricsObject["id"]?.jsonPrimitive?.content ?: ""
            val linesArray = lyricsObject["synchronizedLines"]!!.jsonArray
            val lyrics = linesArray.map { lineObj ->
                val line = lineObj.jsonObject["line"]?.jsonPrimitive?.content ?: ""
                val start = lineObj.jsonObject["milliseconds"]?.jsonPrimitive?.int ?: 0
                val end = lineObj.jsonObject["duration"]?.jsonPrimitive?.int ?: 0
                Lyric(line, start.toLong(), start.toLong() + end.toLong())
            }
            listOf(Lyrics(lyricsId, track.title, lyrics = lyrics))
        } catch (e: Exception) {
            emptyList()
        }
    }

    //<============= Album =============>

    override fun getMediaItems(album: Album) = getMediaItems(album.artists.first())

    override suspend fun loadAlbum(small: Album): Album {
        if(small.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(small)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return resultsObject.toShow(true)
        } else {
            val jsonObject = api.album(small)
            val resultsObject = jsonObject["results"]!!.jsonObject
            return resultsObject.toAlbum(true)
        }
    }

    override fun loadTracks(album: Album): PagedData<Track> = PagedData.Single {
        if(album.extras["__TYPE__"] == "show") {
            val jsonObject = api.show(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val episodesObject = resultsObject["EPISODES"]!!.jsonObject
            val dataArray = episodesObject["data"]!!.jsonArray
            val data = dataArray.map { episode ->
                episode.jsonObject.toEpisode()
            }.reversed()
            data
        } else {
            val jsonObject = api.album(album)
            val resultsObject = jsonObject["results"]!!.jsonObject
            val songsObject = resultsObject["SONGS"]!!.jsonObject
            val dataArray = songsObject["data"]!!.jsonArray
            val data = dataArray.mapIndexed { index, song ->
                val currentTrack = song.jsonObject.toTrack()
                val nextTrack = dataArray.getOrNull(index + 1)?.jsonObject?.toTrack()
                val nextTrackId = nextTrack?.id
                Track(
                    id = currentTrack.id,
                    title = currentTrack.title,
                    cover = currentTrack.cover,
                    duration = currentTrack.duration,
                    releaseDate = currentTrack.releaseDate,
                    artists = currentTrack.artists,
                    extras = currentTrack.extras.plus(mapOf(Pair("NEXT", nextTrackId ?: ""), Pair("album_id", album.id)))
                )
            }
            data
        }
    }

    //<============= Playlist =============>

    override fun getMediaItems(playlist: Playlist) = PagedData.Single {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        val songsObject = resultsObject["SONGS"]!!.jsonObject
        val dataArray = songsObject["data"]!!.jsonArray
        val data = dataArray.map { song ->
            song.jsonObject.toMediaItemsContainer(name = "")
        }
        emptyList<MediaItemsContainer>()
    }

    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        val jsonObject = api.playlist(playlist)
        val resultsObject = jsonObject["results"]!!.jsonObject
        return resultsObject.toPlaylist(true)
    }

    override fun loadTracks(playlist: Playlist): PagedData<Track> = PagedData.Single {
        val dataArray = when (playlist.extras["radio"]) {
            "track" -> {
                val jsonObject = api.mix(playlist.id)
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }
            "playlist", "album" -> {
                val jsonObject = api.radio(playlist.id, playlist.extras["artist"] ?: "")
                jsonObject["results"]!!.jsonObject["data"]!!.jsonArray
            }
            else -> {
                val jsonObject = api.playlist(playlist)
                jsonObject["results"]!!.jsonObject["SONGS"]!!.jsonObject["data"]!!.jsonArray
            }
        }

        dataArray.mapIndexed { index, song ->
            val currentTrack = song.jsonObject.toTrack()
            val nextTrack = dataArray.getOrNull(index + 1)?.jsonObject?.toTrack()
            val nextTrackId = nextTrack?.id

            Track(
                id = currentTrack.id,
                title = currentTrack.title,
                cover = currentTrack.cover,
                duration = currentTrack.duration,
                releaseDate = currentTrack.releaseDate,
                artists = currentTrack.artists,
                extras = currentTrack.extras.plus(
                    mapOf(
                        "NEXT" to (nextTrackId ?: ""),
                        when (playlist.extras["radio"]) {
                            "track" -> "artist_id" to currentTrack.artists[0].id
                            "playlist", "album" -> "artist_id" to (playlist.extras["artist"] ?: "")
                            else -> "playlist_id" to playlist.id
                        }
                    )
                )
            )
        }
    }

    //<============= Artist =============>

    override fun getMediaItems(artist: Artist) = PagedData.Single {
        val dataList = mutableListOf<MediaItemsContainer>()
        val jsonObject = api.artist(artist)

        val resultsObject = jsonObject["results"]!!.jsonObject
        for (result in resultsObject) {
            when (result.key) {
                "TOP" -> {
                    val jObject = resultsObject["TOP"]!!.jsonObject
                    val jArray = jObject["data"]!!.jsonArray
                    val data = jArray.toMediaItemsContainer(name = "Top")
                    dataList.add(data)
                }
                "HIGHLIGHT" -> {
                    val jObject = resultsObject["HIGHLIGHT"]!!.jsonObject
                    val itemObject = jObject["ITEM"]!!.jsonObject
                    val data = itemObject.toMediaItemsContainer(name = "Highlight")
                    dataList.add(data)
                }
                "SELECTED_PLAYLIST" -> {
                    val jObject = resultsObject["SELECTED_PLAYLIST"]!!.jsonObject
                    val jArray = jObject["data"]!!.jsonArray
                    val data = jArray.toMediaItemsContainer(name = "Selected Playlists")
                    dataList.add(data)
                }
                "RELATED_PLAYLIST" -> {
                    val jObject = resultsObject["RELATED_PLAYLIST"]!!.jsonObject
                    val jArray = jObject["data"]!!.jsonArray
                    val data = jArray.toMediaItemsContainer(name = "Related Playlists")
                    dataList.add(data)
                }
                "RELATED_ARTISTS" -> {
                    val jObject = resultsObject["RELATED_ARTISTS"]!!.jsonObject
                    val jArray = jObject["data"]!!.jsonArray
                    val data = jArray.toMediaItemsContainer(name = "Related Artists")
                    dataList.add(data)
                }
                "ALBUMS" -> {
                    val jObject = resultsObject["ALBUMS"]!!.jsonObject
                    val jArray = jObject["data"]!!.jsonArray
                    val data = jArray.toMediaItemsContainer(name = "Albums")
                    dataList.add(data)
                }
            }
            }
        dataList
    }

    override suspend fun loadArtist(small: Artist): Artist {
        val jsonObject = api.artist(small)
        val resultsObject = jsonObject["results"]!!.jsonObject["DATA"]!!.jsonObject
        return resultsObject.toArtist(isFollowingArtist(small.id), true)
    }

    private suspend fun isFollowingArtist(id: String): Boolean {
        val artObject = api.getArtists()
        val resultObject = artObject["results"]!!.jsonObject
        val tabObject = resultObject["TAB"]!!.jsonObject
        val artistsObject = tabObject["artists"]!!.jsonObject
        val dataArray = artistsObject["data"]!!.jsonArray
        var isFollowing = false
        dataArray.map { item ->
            val artistId = item.jsonObject["ART_ID"]?.jsonPrimitive?.content ?: ""
            if(artistId.contains(id)) {
                isFollowing = true
            }
        }
        return isFollowing
    }

    override suspend fun followArtist(artist: Artist): Boolean {
        api.followArtist(artist.id)
        return true
    }

    override suspend fun unfollowArtist(artist: Artist): Boolean {
        api.unfollowArtist(artist.id)
        return true
    }

    //<============= Login =============>

    override suspend fun getCurrentUser(): User {
        val userList = api.makeUser()
        return userList.first()
    }

    override val loginWebViewInitialUrl = "https://www.deezer.com/login?redirect_type=page&redirect_link=%2Faccount%2F"
        .toRequest(mapOf(Pair("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")))

    override val loginWebViewStopUrlRegex = "https://www\\.deezer\\.com/account/.*".toRegex()

    override suspend fun onLoginWebviewStop(url: String, data: String): List<User> {
        if (data.contains("arl=")) {
            DeezerCredentialsHolder.updateCredentials(arl = data.substringAfter("arl=").substringBefore(";"))
            DeezerCredentialsHolder.updateCredentials(sid = data.substringAfter("sid=").substringBefore(";"))
            val userList = api.makeUser()
            return userList
        } else {
            return emptyList()
        }
    }

    override val loginInputFields: List<LoginClient.InputField>
        get() = listOf(
            LoginClient.InputField(
                key = "arl",
                label = "ARL",
                isRequired = false,
                isPassword = true

            )
        )

    override suspend fun onLogin(data: Map<String, String?>): List<User> {
        DeezerCredentialsHolder.updateCredentials(arl = data["arl"] ?: "")
        api.getSid()
        val userList = api.makeUser()
        return userList
    }

    override suspend fun onLogin(username: String, password: String): List<User> {
        // Set shared credentials
        DeezerCredentialsHolder.updateCredentials(email = username)
        DeezerCredentialsHolder.updateCredentials(pass = password)

        api.getArlByEmail(username, password)
        val userList = api.makeUser(username, password)
        return userList
    }

    override suspend fun onSetLoginUser(user: User?) {
        if (user != null) {
            DeezerCredentialsHolder.updateCredentials(arl = user.extras["arl"] ?: "")
            DeezerCredentialsHolder.updateCredentials(userId = user.extras["user_id"] ?: "")
            DeezerCredentialsHolder.updateCredentials(sid = user.extras["sid"] ?: "")
            DeezerCredentialsHolder.updateCredentials(token = user.extras["token"] ?: "")
            DeezerCredentialsHolder.updateCredentials(licenseToken = user.extras["license_token"] ?: "")
            DeezerCredentialsHolder.updateCredentials(email = user.extras["email"] ?: "")
            DeezerCredentialsHolder.updateCredentials(pass = user.extras["pass"] ?: "")
        }
    }

    //<============= Share =============>

    override suspend fun onShare(album: Album): String = "https://www.deezer.com/album/${album.id}"

    override suspend fun onShare(artist: Artist): String = "https://www.deezer.com/artist/${artist.id}"

    override suspend fun onShare(playlist: Playlist): String = "https://www.deezer.com/playlist/${playlist.id}"

    override suspend fun onShare(track: Track): String = "https://www.deezer.com/track/${track.id}"

    override suspend fun onShare(user: User): String = "https://www.deezer.com/profile/${user.id}"

    //<============= Utils =============>

    private fun initializeCredentialsIfNeeded() {
        if (DeezerCredentialsHolder.credentials == null) {
            DeezerCredentialsHolder.initialize(
                DeezerCredentials(arl = "", sid = "", token = "", userId = "", licenseToken = "", email = "", pass = "")
            )
        }
    }

    private fun handleArlExpiration() {
        if (arl.isEmpty() || arlExpired) throw ClientException.LoginRequired()
    }

    private val arl: String get() = credentials.arl
    private val arlExpired: Boolean get() = utils.arlExpired
    private val credentials: DeezerCredentials get() = DeezerCredentialsHolder.credentials ?: throw IllegalStateException(LOGIN_REQUIRED_MESSAGE)
    private val utils: DeezerUtils get() = DeezerUtils
    private val quality: String get() = settings?.getString("audio_quality") ?: DEFAULT_QUALITY
    private val log: Boolean get() = settings?.getBoolean("log") ?: false
    private val history: Boolean get() = settings?.getBoolean("history") ?: true

    companion object {
        private const val DEFAULT_QUALITY = "320"
        private const val LOGIN_REQUIRED_MESSAGE = "DeezerCredentials not initialized"
    }
}