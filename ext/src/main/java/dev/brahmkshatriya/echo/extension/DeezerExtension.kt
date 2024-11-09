package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ArtistFollowClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SaveToLibraryClient
import dev.brahmkshatriya.echo.common.clients.SearchClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.TrackLikeClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearch
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
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
import dev.brahmkshatriya.echo.extension.clients.DeezerAlbumClient
import dev.brahmkshatriya.echo.extension.clients.DeezerArtistClient
import dev.brahmkshatriya.echo.extension.clients.DeezerHomeFeedClient
import dev.brahmkshatriya.echo.extension.clients.DeezerLibraryClient
import dev.brahmkshatriya.echo.extension.clients.DeezerLyricsClient
import dev.brahmkshatriya.echo.extension.clients.DeezerPlaylistClient
import dev.brahmkshatriya.echo.extension.clients.DeezerRadioClient
import dev.brahmkshatriya.echo.extension.clients.DeezerSearchClient
import dev.brahmkshatriya.echo.extension.clients.DeezerTrackClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerExtension : HomeFeedClient, TrackClient, TrackLikeClient, RadioClient,
    SearchClient, AlbumClient, ArtistClient, ArtistFollowClient, PlaylistClient, LyricsClient, ShareClient,
    TrackerClient, LoginClient.WebView.Cookie, LoginClient.UsernamePassword, LoginClient.CustomTextInput,
    LibraryClient, PlaylistEditClient, SaveToLibraryClient {

    private val session = DeezerSession.getInstance()
    private val api = DeezerApi(session)
    private val parser = DeezerParser(session)

    override val settingItems: List<Setting>
        get() = listOf(
            SettingList(
                "Use Proxy",
                "proxy",
                "Use proxy to prevent GEO-Blocking",
                mutableListOf("No Proxy", "UK", "RU 1", "RU 2"),
                mutableListOf("", "uk.proxy.murglar.app", "ru1.proxy.murglar.app", "ru2.proxy.murglar.app"),
                0
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
                        240,
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
                        getDefaultLanguageIndex(session.settings)
                    ),
                    SettingList(
                        "Country",
                        "country",
                        "Choose your preferred country for browse recommendations",
                        DeezerCountries.countryEntryTitles,
                        DeezerCountries.countryEntryValues,
                        getDefaultCountryIndex(session.settings)
                    )
                )
            ),
        )

    override fun setSettings(settings: Settings) {
        session.settings = settings
    }

    override suspend fun onExtensionSelected() {
        session.settings?.let { setSettings(it) }
    }

    //<============= HomeTab =============>

    private val deezerHomeFeedClient = DeezerHomeFeedClient(api, parser)

    override suspend fun getHomeTabs(): List<Tab> = listOf()

    override fun getHomeFeed(tab: Tab?): PagedData<Shelf> = deezerHomeFeedClient.getHomeFeed()

    //<============= Library =============>

    private val deezerLibraryClient = DeezerLibraryClient(api, parser)

    override suspend fun getLibraryTabs(): List<Tab> = deezerLibraryClient.getLibraryTabs()

    override fun getLibraryFeed(tab: Tab?): PagedData.Single<Shelf> = deezerLibraryClient.getLibraryFeed(tab)

    override suspend fun addTracksToPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        index: Int,
        new: List<Track>
    ) {
        handleArlExpiration()
        api.addToPlaylist(playlist, new)
    }

    override suspend fun removeTracksFromPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        indexes: List<Int>
    ) {
        handleArlExpiration()
        api.removeFromPlaylist(playlist, tracks, indexes)
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

    override suspend fun editPlaylistMetadata(
        playlist: Playlist,
        title: String,
        description: String?
    ) {
        handleArlExpiration()
        api.updatePlaylist(playlist.id, title, description)
    }

    override suspend fun likeTrack(track: Track, isLiked: Boolean) {
        handleArlExpiration()
        if (isLiked) {
            api.addFavoriteTrack(track.id)
        } else {
            api.removeFavoriteTrack(track.id)
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
            val playlist = parser.run { it.jsonObject.toPlaylist() }
            if (playlist.isEditable) {
                playlistList.add(playlist)
            }

        }
        return playlistList
    }

    override suspend fun moveTrackInPlaylist(
        playlist: Playlist,
        tracks: List<Track>,
        fromIndex: Int,
        toIndex: Int
    ) {
        handleArlExpiration()
        val idArray = tracks.map { it.id }.toMutableList()
        idArray.add(toIndex, idArray.removeAt(fromIndex))
        api.updatePlaylistOrder(playlist.id, idArray)
    }

    override suspend fun isSavedToLibrary(mediaItem: EchoMediaItem): Boolean {
        suspend fun isItemSaved(
            getItems: suspend () -> JsonObject,
            idKey: String,
            itemId: String
        ): Boolean {
            val dataArray = getItems()["results"]?.jsonObject
                ?.get("TAB")?.jsonObject
                ?.values?.firstOrNull()?.jsonObject
                ?.get("data")?.jsonArray ?: return false

            return dataArray.any { item ->
                val id = item.jsonObject[idKey]?.jsonPrimitive?.content
                id == itemId
            }
        }

        return when (mediaItem) {
            is EchoMediaItem.Lists.AlbumItem -> {
                isItemSaved(api::getAlbums, "ALB_ID", mediaItem.album.id)
            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                isItemSaved(api::getPlaylists, "PLAYLIST_ID", mediaItem.playlist.id)
            }

            else -> false
        }
    }

    override suspend fun removeFromLibrary(mediaItem: EchoMediaItem) {
        when (mediaItem) {
            is EchoMediaItem.Lists.AlbumItem -> {
                api.removeFavoriteAlbum(mediaItem.album.id)
            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                api.removeFavoritePlaylist(mediaItem.playlist.id)
            }

            else -> {}
        }
    }

    override suspend fun saveToLibrary(mediaItem: EchoMediaItem) {
        when (mediaItem) {
            is EchoMediaItem.Lists.AlbumItem -> {
                api.addFavoriteAlbum(mediaItem.album.id)
            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                api.addFavoritePlaylist(mediaItem.playlist.id)
            }

            else -> {}
        }
    }

    //<============= Search =============>

    private val deezerSearchClient = DeezerSearchClient(api, history, parser)

    override suspend fun quickSearch(query: String?): List<QuickSearch.QueryItem> = deezerSearchClient.quickSearch(query)

    override fun searchFeed(query: String?, tab: Tab?): PagedData.Single<Shelf> = deezerSearchClient.searchFeed(query, tab)

    override suspend fun searchTabs(query: String?): List<Tab> = deezerSearchClient.searchTabs(query)

    override suspend fun deleteSearchHistory(query: QuickSearch.QueryItem) {
        api.deleteSearchHistory()
    }

    suspend fun channelFeed(target: String): List<Shelf> {
        val jsonObject = api.page(target.substringAfter("/"))
        val channelPageResults = jsonObject["results"]!!.jsonObject
        val channelSections = channelPageResults["sections"]!!.jsonArray
        return coroutineScope {
            channelSections.map { section ->
                async(Dispatchers.IO) {
                    parser.run {
                        section.toShelfItemsList(section.jsonObject["title"]!!.jsonPrimitive.content)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    //<============= Play =============>

    private val deezerTrackClient = DeezerTrackClient(api, parser)

    override suspend fun getStreamableMedia(streamable: Streamable): Streamable.Media = deezerTrackClient.getStreamableMedia(streamable)

    override suspend fun loadTrack(track: Track): Track = deezerTrackClient.loadTrack(track, quality)

    override fun getShelves(track: Track): PagedData<Shelf> = getShelves(track.artists.first())

    //<============= Radio =============>

    private val deezerRadioClient = DeezerRadioClient(api, parser)

    override fun loadTracks(radio: Radio): PagedData<Track> = deezerRadioClient.loadTracks(radio)

    override suspend fun radio(track: Track, context: EchoMediaItem?): Radio = deezerRadioClient.radio(track, context)

    override suspend fun radio(album: Album): Radio = deezerRadioClient.radio(album)

    override suspend fun radio(playlist: Playlist): Radio = deezerRadioClient.radio(playlist)

    override suspend fun radio(artist: Artist): Radio = deezerRadioClient.radio(artist)

    override suspend fun radio(user: User): Radio {
        TODO("Not Planned")
    }

    //<============= Lyrics =============>

    private val deezerLyricsClient = DeezerLyricsClient(api)

    override suspend fun loadLyrics(small: Lyrics): Lyrics = small

    override fun searchTrackLyrics(clientId: String, track: Track): PagedData.Single<Lyrics> = deezerLyricsClient.searchTrackLyrics(track)

    //<============= Album =============>

    private val deezerAlbumClient = DeezerAlbumClient(api, parser)

    override fun getShelves(album: Album): PagedData.Single<Shelf> = getShelves(album.artists.first())

    override suspend fun loadAlbum(album: Album): Album = deezerAlbumClient.loadAlbum(album)

    override fun loadTracks(album: Album): PagedData<Track> = deezerAlbumClient.loadTracks(album)

    //<============= Playlist =============>

    private val deezerPlaylistClient = DeezerPlaylistClient(api, parser)

    override fun getShelves(playlist: Playlist): PagedData.Single<Shelf> = deezerPlaylistClient.getShelves(playlist)

    override suspend fun loadPlaylist(playlist: Playlist): Playlist = deezerPlaylistClient.loadPlaylist(playlist)

    override fun loadTracks(playlist: Playlist): PagedData<Track> = deezerPlaylistClient.loadTracks(playlist)

    //<============= Artist =============>

    private val deezerArtistClient = DeezerArtistClient(api, parser)

    override fun getShelves(artist: Artist): PagedData.Single<Shelf> = deezerArtistClient.getShelves(artist)

    override suspend fun loadArtist(small: Artist): Artist = deezerArtistClient.loadArtist(small)

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

    override val loginWebViewInitialUrl =
        "https://www.deezer.com/login?redirect_type=page&redirect_link=%2Faccount%2F".toRequest(
            mapOf(
                Pair(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                )
            )
        )

    override val loginWebViewStopUrlRegex = "https://www\\.deezer\\.com/account/.*".toRegex()

    override suspend fun onLoginWebviewStop(url: String, data: String): List<User> {
        val arl = extractCookieValue(data, "arl")
        val sid = extractCookieValue(data, "sid")
        if (arl != null && sid != null) {
            session.updateCredentials(arl = arl, sid = sid)
            return api.makeUser()
        } else {
            throw Exception("Failed to retrieve ARL and SID from cookies")
        }
    }

    private fun extractCookieValue(data: String, key: String): String? {
        return data.substringAfter("$key=").substringBefore(";").takeIf { it.isNotEmpty() }
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
        session.updateCredentials(arl = data["arl"] ?: "")
        api.getSid()
        val userList = api.makeUser()
        return userList
    }

    override suspend fun onLogin(username: String, password: String): List<User> {
        // Set shared credentials
        session.updateCredentials(email = username, pass = password)

        api.getArlByEmail(username, password)
        val userList = api.makeUser(username, password)
        return userList
    }

    override suspend fun onSetLoginUser(user: User?) {
        if (user != null) {
            session.updateCredentials(
                arl = user.extras["arl"] ?: "",
                sid = user.extras["sid"] ?: "",
                token = user.extras["token"] ?: "",
                userId = user.extras["user_id"] ?: "",
                licenseToken = user.extras["license_token"] ?: "",
                email = user.extras["email"] ?: "",
                pass = user.extras["pass"] ?: ""
            )
        }
    }

    //<============= Share =============>

    override suspend fun onShare(item: EchoMediaItem): String {
        return when (item) {
            is EchoMediaItem.TrackItem -> "https://www.deezer.com/track/${item.id}"
            is EchoMediaItem.Profile.ArtistItem -> "https://www.deezer.com/artist/${item.id}"
            is EchoMediaItem.Profile.UserItem -> "https://www.deezer.com/profile/${item.id}"
            is EchoMediaItem.Lists.AlbumItem -> "https://www.deezer.com/album/${item.id}"
            is EchoMediaItem.Lists.PlaylistItem -> "https://www.deezer.com/playlist/${item.id}"
            is EchoMediaItem.Lists.RadioItem -> TODO()
        }
    }

    //<============= Tracking =============>

    override suspend fun onMarkAsPlayed(clientId: String, context: EchoMediaItem?, track: Track) {
        return
    }

    override suspend fun onStartedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        if (log) {
            api.log(track)
        }
    }

    override suspend fun onStoppedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        return
    }

    //<============= Utils =============>

    suspend fun handleArlExpiration() {
        if (session.credentials?.arl?.isEmpty() == true || session.arlExpired) {
            if (session.credentials?.email?.isEmpty() == true || session.credentials?.pass?.isEmpty() == true) {
                throw ClientException.LoginRequired()
            } else {
                api.makeUser()
            }
        }
    }

    private val quality: String get() = session.settings?.getString("audio_quality") ?: DEFAULT_QUALITY
    private val log: Boolean get() = session.settings?.getBoolean("log") ?: false
    private val history: Boolean get() = session.settings?.getBoolean("history") ?: true

    companion object {
        private const val DEFAULT_QUALITY = "320"
    }
}