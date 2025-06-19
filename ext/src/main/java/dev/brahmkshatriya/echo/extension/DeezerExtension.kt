package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ArtistFollowClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.PlaylistEditClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.clients.SaveToLibraryClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.TrackLikeClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Request
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Tab
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
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
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerExtension : HomeFeedClient, TrackClient, TrackLikeClient, RadioClient,
    SearchFeedClient, AlbumClient, ArtistClient, ArtistFollowClient, PlaylistClient, LyricsClient, ShareClient,
    TrackerClient, LoginClient.WebView, LoginClient.CustomInput,
    LibraryFeedClient, PlaylistEditClient, SaveToLibraryClient {

    private val session by lazy { DeezerSession.getInstance() }
    private val api by lazy { DeezerApi(session) }
    private val parser by lazy { DeezerParser(session) }

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
                    SettingSlider(
                        "Image Quality",
                        "image_quality",
                        "Choose your preferred image quality (Can impact loading times)",
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
                        DeezerCountries.languages.map { it.name },
                        DeezerCountries.languages.map { it.code },
                        getDefaultLanguageIndex(session.settings)
                    ),
                    SettingList(
                        "Country",
                        "country",
                        "Choose your preferred country for browse recommendations",
                        DeezerCountries.countries.map { it.name },
                        DeezerCountries.countries.map { it.code },
                        getDefaultCountryIndex(session.settings)
                    )
                )
            ),
            SettingCategory(
                "Appearance",
                "appearance",
                mutableListOf(
                    SettingList(
                        "Shelf Type",
                        "shelf",
                        "Choose your preferred shelf type",
                        mutableListOf("Grid", "Linear"),
                        mutableListOf("grid", "linear"),
                        0
                    )
                )
            )
        )

    override fun setSettings(settings: Settings) {
        session.settings = settings
    }

    override suspend fun onExtensionSelected() {
        session.settings?.let { setSettings(it) }
    }

    //<============= HomeTab =============>

    private val deezerHomeFeedClient = DeezerHomeFeedClient(this, api, parser)

    override suspend fun getHomeTabs(): List<Tab> = listOf()

    override fun getHomeFeed(tab: Tab?): Feed = deezerHomeFeedClient.getHomeFeed(shelf)

    //<============= Library =============>

    private val deezerLibraryClient = DeezerLibraryClient(this, api, parser)

    override suspend fun getLibraryTabs(): List<Tab> = deezerLibraryClient.getLibraryTabs()

    override fun getLibraryFeed(tab: Tab?): Feed = deezerLibraryClient.getLibraryFeed(tab)

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

    override suspend fun listEditablePlaylists(track: Track?): List<Pair<Playlist, Boolean>> {
        handleArlExpiration()
        val playlistList = mutableListOf<Pair<Playlist, Boolean>>()
        val jsonObject = api.getPlaylists()
        val resultObject = jsonObject["results"]!!.jsonObject
        val tabObject = resultObject["TAB"]!!.jsonObject
        val playlistObject = tabObject["playlists"]!!.jsonObject
        val dataArray = playlistObject["data"]!!.jsonArray
        dataArray.map {
            val playlist = parser.run { it.jsonObject.toPlaylist() }
            if (playlist.isEditable) {
                playlistList.add(Pair(playlist, false))
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

    override suspend fun saveToLibrary(mediaItem: EchoMediaItem, save: Boolean) {
        when (mediaItem) {
            is EchoMediaItem.Lists.AlbumItem -> {
                if (save) api.addFavoriteAlbum(mediaItem.album.id) else api.removeFavoriteAlbum(mediaItem.album.id)

            }

            is EchoMediaItem.Lists.PlaylistItem -> {
                if (save) api.addFavoritePlaylist(mediaItem.playlist.id) else api.removeFavoritePlaylist(mediaItem.playlist.id)
            }

            else -> {}
        }
    }

    //<============= Search =============>

    private val deezerSearchClient = DeezerSearchClient(this, api, history, parser)

    override suspend fun quickSearch(query: String): List<QuickSearchItem.Query> = deezerSearchClient.quickSearch(query)

    override fun searchFeed(query: String, tab: Tab?): Feed = deezerSearchClient.searchFeed(query, tab, shelf)

    override suspend fun searchTabs(query: String): List<Tab> = deezerSearchClient.searchTabs(query)

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        api.deleteSearchHistory()
    }

    suspend fun channelFeed(target: String): List<Shelf> {
        val jsonObject = api.page(target.substringAfter("/"))
        val channelPageResults = jsonObject["results"]!!.jsonObject
        val channelSections = channelPageResults["sections"]!!.jsonArray
        return supervisorScope {
            channelSections.map { section ->
                async(Dispatchers.Default) {
                    parser.run {
                        section.jsonObject["title"]?.jsonPrimitive?.content
                            ?.let { section.toShelfItemsList(it) }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    //<============= Play =============>

    private val deezerTrackClient = DeezerTrackClient(this, api)

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media = deezerTrackClient.loadStreamableMedia(streamable, isDownload)

    override suspend fun loadTrack(track: Track): Track = deezerTrackClient.loadTrack(track)

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

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics

    override fun searchTrackLyrics(clientId: String, track: Track): PagedData.Single<Lyrics> = deezerLyricsClient.searchTrackLyrics(track)

    //<============= Album =============>

    private val deezerAlbumClient = DeezerAlbumClient(this, api, parser)

    override fun getShelves(album: Album): PagedData.Single<Shelf> = getShelves(album.artists.first())

    override suspend fun loadAlbum(album: Album): Album = deezerAlbumClient.loadAlbum(album)

    override fun loadTracks(album: Album): PagedData<Track> = deezerAlbumClient.loadTracks(album)

    //<============= Playlist =============>

    private val deezerPlaylistClient = DeezerPlaylistClient(this, api, parser)

    override fun getShelves(playlist: Playlist): PagedData.Single<Shelf> = deezerPlaylistClient.getShelves(playlist)

    override suspend fun loadPlaylist(playlist: Playlist): Playlist = deezerPlaylistClient.loadPlaylist(playlist)

    override fun loadTracks(playlist: Playlist): PagedData<Track> = deezerPlaylistClient.loadTracks(playlist)

    //<============= Artist =============>

    private val deezerArtistClient = DeezerArtistClient(this, api, parser)

    override fun getShelves(artist: Artist): PagedData.Single<Shelf> = deezerArtistClient.getShelves(artist)

    override suspend fun loadArtist(artist: Artist): Artist = deezerArtistClient.loadArtist(artist)

    override suspend fun followArtist(artist: Artist, follow: Boolean) {
        if (follow) api.followArtist(artist.id) else api.unfollowArtist(artist.id)
    }

    //<============= Login =============>

    override suspend fun getCurrentUser(): User {
        val userList = api.makeUser()
        return userList.first()
    }

    override val webViewRequest = object : WebViewRequest.Cookie<List<User>> {
        override suspend fun onStop(url: Request, cookie: String): List<User> {
            val arl = extractCookieValue(cookie, "arl")
            val sid = extractCookieValue(cookie, "sid")
            if (arl != null && sid != null) {
                session.updateCredentials(arl = arl, sid = sid)
                return api.makeUser()
            } else if (cookie.isEmpty()) {
                throw Exception("Ignore this")
            } else {
                throw Exception("Failed to retrieve ARL and SID from cookies")
            }
        }

        override val initialUrl = "https://www.deezer.com/login?redirect_type=page&redirect_link=%2Faccount%2F".toRequest(
            mapOf(
                Pair(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
                )
            )
        )

        override val stopUrlRegex = "https://www\\.deezer\\.com/account/.*".toRegex()

        private fun extractCookieValue(data: String, key: String): String? {
            return data.substringAfter("$key=").substringBefore(";").takeIf { it.isNotEmpty() }
        }
    }

    override val forms: List<LoginClient.Form> = listOf(
        LoginClient.Form(
            key = "userPass",
            label = "E-Mail and Password",
            icon = LoginClient.InputField.Type.Email,
            inputFields = listOf(
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Email,
                    key = "email",
                    label = "E-Mail",
                    isRequired = true,
                ),
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Password,
                    key = "pass",
                    label = "Password",
                    isRequired = true
                )
            )
        ),
        LoginClient.Form(
            key = "manual",
            label = "ARL",
            icon = LoginClient.InputField.Type.Misc,
            inputFields = listOf(
                LoginClient.InputField(
                    type = LoginClient.InputField.Type.Misc,
                    key = "arl",
                    label = "ARL",
                    isRequired = false,
                )

            )
        )
    )

    override suspend fun onLogin(key: String, data: Map<String, String?>): List<User> {
        if(data["email"] != null && data["pass"] != null) {
            val email = data["email"]!!
            val password = data["pass"]!!

            session.updateCredentials(email = email, pass = password)

            api.getArlByEmail(email, password, 3)
            val userList = api.makeUser(email, password)
            return userList
        } else {
            session.updateCredentials(arl = data["arl"] ?: "")
            api.getSid()
            val userList = api.makeUser()
            return userList
        }
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
        } else {
            session.updateCredentials(
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

    //<============= Share =============>

    override suspend fun onShare(item: EchoMediaItem): String {
        return when (item) {
            is EchoMediaItem.TrackItem -> "https://www.deezer.com/track/${item.id}"
            is EchoMediaItem.Profile.ArtistItem -> "https://www.deezer.com/artist/${item.id}"
            is EchoMediaItem.Profile.UserItem -> "https://www.deezer.com/profile/${item.id}"
            is EchoMediaItem.Lists.AlbumItem -> "https://www.deezer.com/album/${item.id}"
            is EchoMediaItem.Lists.PlaylistItem -> "https://www.deezer.com/playlist/${item.id}"
            is EchoMediaItem.Lists.RadioItem -> TODO("Does not exist")
        }
    }

    //<============= Tracking =============>

    override suspend fun onMarkAsPlayed(details: TrackDetails) {}

    override suspend fun onTrackChanged(details: TrackDetails?) {
        if (details != null) {
            if (log) {
                api.log(details.track)
            }
        }
    }

    //<============= Utils =============>

    suspend fun handleArlExpiration() {
        if (session.credentials.arl.isEmpty() || session.arlExpired) {
            if (session.credentials.email.isEmpty() || session.credentials.pass.isEmpty()) {
                throw ClientException.LoginRequired()
            } else {
                api.makeUser()
            }
        }
    }

    private val shelf: String get() = session.settings?.getString("shelf") ?: DEFAULT_TYPE
    private val log: Boolean get() = session.settings?.getBoolean("log") ?: false
    private val history: Boolean get() = session.settings?.getBoolean("history") ?: true

    companion object {
        private const val DEFAULT_TYPE = "grid"
    }
}