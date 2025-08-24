package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Date as EchoDate
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeezerParser(private val session: DeezerSession) {

    fun JsonElement.toShelfItemsList(name: String = "Unknown"): Shelf? {
        val itemsArray = jsonObject["items"]?.jsonArray ?: return null
        val list = itemsArray.mapObjects { it.toEchoMediaItem() }
        return if (list.isNotEmpty()) {
            Shelf.Lists.Items(
                id = name,
                title = name,
                list = list
            )
        } else {
            null
        }
    }

    fun JsonObject.toShelfItemsList(name: String = "Unknown"): Shelf? {
        val item = toEchoMediaItem() ?: return null
        return Shelf.Lists.Items(
            id = name,
            title = name,
            list = listOf(item)
        )
    }

    fun JsonArray.toShelfItemsList(name: String = "Unknown"): Shelf? {
        val list = mapObjects { it.toEchoMediaItem() }
        return if (list.isNotEmpty()) {
            Shelf.Lists.Items(
                id = name,
                title = name,
                list = list
            )
        } else {
            null
        }
    }

    inline fun JsonElement.toShelfCategoryList(
        name: String = "Unknown",
        shelf: String,
        crossinline block: suspend (String) -> List<Shelf>
    ): Shelf.Lists.Categories {
        val itemsArray = jsonObject["items"]?.jsonArray ?: return Shelf.Lists.Categories(name, name, emptyList())
        val listType = if (shelf.contains("grid")) Shelf.Lists.Type.Grid else Shelf.Lists.Type.Linear
        return Shelf.Lists.Categories(
            id = name,
            title = name,
            list = itemsArray.take(6).mapNotNull { it.jsonObject.toShelfCategory(block) },
            type = listType,
            more = itemsArray.mapNotNull { it.jsonObject.toShelfCategory(block) }.toFeed()
        )
    }

    inline fun JsonObject.toShelfCategory(
        crossinline block: suspend (String) -> List<Shelf>
    ): Shelf.Category? {
        val data = this["data"]?.jsonObject ?: this
        val type = data["__TYPE__"]?.jsonPrimitive?.content ?: return null
        return when {
            "channel" in type -> toChannel(block)
            else -> null
        }
    }

    inline fun JsonObject.toChannel(
        crossinline block: suspend (String) -> List<Shelf>
    ): Shelf.Category {
        val data = this["data"]?.jsonObject ?: this
        val title = data["title"]?.jsonPrimitive?.content.orEmpty()
        val target = this["target"]?.jsonPrimitive?.content.orEmpty()
        return Shelf.Category(
            id = title,
            title = title,
            feed = Feed(emptyList()) {
                block(target).toFeedData()
            }
        )
    }

    fun JsonObject.toEchoMediaItem(): EchoMediaItem? {
        val data = this["data"]?.jsonObject ?: this
        return data["__TYPE__"]?.jsonPrimitive?.content?.let { type ->
            when {
                "playlist" in type -> toPlaylist()
                "album" in type -> toAlbum()
                "song" in type -> toTrack()
                "artist" in type -> toArtist(isShelfItem = true)
                "show" in type -> toShow()
                "episode" in type -> toEpisode()
                "flow" in type -> toRadio()
                else -> null
            }
        }
    }

    fun JsonObject.toShow(): Album = unwrapAnd { data ->
        val md5 = data["SHOW_ART_MD5"]?.jsonPrimitive?.content.orEmpty()
        return Album(
            id = data["SHOW_ID"]?.jsonPrimitive?.content.orEmpty(),
            type = Album.Type.Show,
            title = data["SHOW_NAME"]?.jsonPrimitive?.content.orEmpty(),
            cover = getCover(md5, "talk", ),
            trackCount = this["EPISODES"]?.jsonObject?.get("total")?.jsonPrimitive?.int?.toLong(),
            artists = emptyList(),
            description = data["SHOW_DESCRIPTION"]?.jsonPrimitive?.content.orEmpty(),
            extras = mapOf("__TYPE__" to "show")
        )
    }

    fun JsonObject.toEpisode(bookmark: Map<String?, Long?> = mapOf()): Track = unwrapAnd { data ->
        val md5 = data["SHOW_ART_MD5"]?.jsonPrimitive?.content.orEmpty()
        val title = data["EPISODE_TITLE"]?.jsonPrimitive?.content.orEmpty()
        val id = data["EPISODE_ID"]?.jsonPrimitive?.content.orEmpty()
        return Track(
            id = id,
            title = title,
            type = Track.Type.Podcast,
            cover = getCover(md5, "talk"),
            duration = data["DURATION"]?.jsonPrimitive?.content?.toLongOrNull()?.times(1000),
            playedDuration = bookmark[id]?.times(1000),
            streamables = listOf(
                Streamable.server(
                    id = data["EPISODE_DIRECT_STREAM_URL"]?.jsonPrimitive?.content.orEmpty(),
                    title = title,
                    quality = 12
                )
            ),
            extras = mapOf(
                "TRACK_TOKEN" to data["TRACK_TOKEN"]?.jsonPrimitive?.content.orEmpty(),
                "FILESIZE_MP3_MISC" to (data["FILESIZE_MP3_MISC"]?.jsonPrimitive?.content ?: "0"),
                "MD5" to md5,
                "TYPE" to "talk",
                "__TYPE__" to "show"
            )
        )
    }

    fun JsonObject.toAlbum(): Album = unwrapAnd { data ->
        val md5 = data["ALB_PICTURE"]?.jsonPrimitive?.content.orEmpty()
        val artistArray = data["ARTISTS"]?.jsonArray.orEmpty()
        val tracks = this["SONGS"]?.jsonObject?.get("total")?.jsonPrimitive?.int
        val rd = data["ORIGINAL_RELEASE_DATE"]?.jsonPrimitive?.content?.toDate() ?: data["PHYSICAL_RELEASE_DATE"]?.jsonPrimitive?.content?.toDate()
        return Album(
            id = data["ALB_ID"]?.jsonPrimitive?.content.orEmpty(),
            title = data["ALB_TITLE"]?.jsonPrimitive?.content.orEmpty(),
            cover = getCover(md5, "cover"),
            trackCount = tracks?.toLong(),
            artists = artistArray.map { artistObject ->
                Artist(
                    id = artistObject.jsonObject["ART_ID"]?.jsonPrimitive?.content.orEmpty(),
                    name = artistObject.jsonObject["ART_NAME"]?.jsonPrimitive?.content.orEmpty(),
                    cover = getCover(artistObject.jsonObject["ART_PICTURE"]?.jsonPrimitive?.content.orEmpty(), "artist")
                )
            },
            releaseDate = rd,
            description = this["description"]?.jsonPrimitive?.content.orEmpty(),
            subtitle = this["subtitle"]?.jsonPrimitive?.content ?: if (tracks != null && rd != null) "$tracks Songs • $rd" else if(tracks != null) "$tracks Songs" else rd?.toString()
        )
    }

    fun JsonObject.toArtist(isShelfItem: Boolean = false): Artist {
        val artistData = when {
            isShelfItem && this["data"]?.jsonObject == null -> this
            this["DATA"]?.jsonObject?.get("ART_BANNER") == null -> this["DATA"]?.jsonObject ?: this["data"]?.jsonObject ?: this
            else -> this["data"]?.jsonObject ?: this
        }
        val md5 = artistData["ART_PICTURE"]?.jsonPrimitive?.content.orEmpty()
        val description = if (this["BIO"] is JsonObject) {
            val bio = this["BIO"]!!.jsonObject
            (bio["BIO"]?.jsonPrimitive?.content.orEmpty().replace("<br />", "").replace("\\n", "")) +
                    (bio["RESUME"]?.jsonPrimitive?.content.orEmpty().replace("<p>", "").replace("</p>", ""))
        } else ""
        return Artist(
            id = artistData["ART_ID"]?.jsonPrimitive?.content.orEmpty(),
            name = artistData["ART_NAME"]?.jsonPrimitive?.content.orEmpty(),
            cover = getCover(md5, "artist"),
            bio = description,
            subtitle = this["subtitle"]?.jsonPrimitive?.content,
            extras = mapOf(
                "followers" to artistData["NB_FAN"]?.jsonPrimitive?.int.toString()
            )
        )
    }

    fun JsonObject.toTrack(): Track {
        val data = this["data"]?.jsonObject ?: this
        val md5 = data["ALB_PICTURE"]?.jsonPrimitive?.content
        val artistArray = data["ARTISTS"]?.jsonArray
        val version = data["VERSION"]?.jsonPrimitive?.content
        return Track(
            id = data["SNG_ID"]?.jsonPrimitive?.content.orEmpty(),
            title = buildString {
                append(data["SNG_TITLE"]?.jsonPrimitive?.content.orEmpty())
                if (!version.isNullOrEmpty()) {
                    append(" ")
                    append(version)
                }
            },
            cover = getCover(md5, "cover"),
            duration = data["DURATION"]?.jsonPrimitive?.content?.toLongOrNull()?.times(1000),
            releaseDate = data["DATE_ADD"]?.jsonPrimitive?.content?.let { parseDate(it) },
            artists = parseArtists(artistArray, data),
            album = Album(
                id = data["ALB_ID"]?.jsonPrimitive?.content.orEmpty(),
                title = data["ALB_TITLE"]?.jsonPrimitive?.content.orEmpty(),
                cover = getCover(md5, "cover")
            ),
            albumOrderNumber = data["TRACK_NUMBER"]?.jsonPrimitive?.content?.toLongOrNull(),
            albumDiscNumber = data["DISK_NUMBER"]?.jsonPrimitive?.content?.toLongOrNull(),
            isrc = data["ISRC"]?.jsonPrimitive?.content,
            isExplicit = data["EXPLICIT_LYRICS"]?.jsonPrimitive?.content == "1",
            extras = buildMap {
                put("FALLBACK_ID", data["FALLBACK"]?.jsonObject?.get("SNG_ID")?.jsonPrimitive?.content.orEmpty())
                put("TRACK_TOKEN", data["TRACK_TOKEN"]?.jsonPrimitive?.content.orEmpty())
                put("FILESIZE_MP3_MISC", data["FILESIZE_MP3_MISC"]?.jsonPrimitive?.content ?: "0")
                put("MD5", md5.orEmpty())
                put("TYPE", "cover")
            }
        )
    }

    fun JsonObject.toPlaylist(): Playlist = unwrapAnd { data ->
        val type = data["PICTURE_TYPE"]?.jsonPrimitive?.content.orEmpty()
        val md5 = data["PLAYLIST_PICTURE"]?.jsonPrimitive?.content.orEmpty()
        val userID = data["PARENT_USER_ID"]?.jsonPrimitive?.content
        val userSID = session.credentials.userId
        val tracks = this["SONGS"]?.jsonObject?.get("total")?.jsonPrimitive?.int
        val cd = data["DATE_ADD"]?.jsonPrimitive?.content?.toDate()
        return Playlist(
            id = data["PLAYLIST_ID"]?.jsonPrimitive?.content.orEmpty(),
            title = data["TITLE"]?.jsonPrimitive?.content.orEmpty(),
            cover = getCover(md5, type),
            description = data["DESCRIPTION"]?.jsonPrimitive?.content.orEmpty(),
            subtitle = this["subtitle"]?.jsonPrimitive?.content ?: if (tracks != null && cd != null) "$tracks Songs • $cd" else if(tracks != null) "$tracks Songs" else cd?.toString(),
            isEditable = userID?.contains(userSID) == true,
            trackCount = tracks?.toLong(),
            creationDate = cd,
        )
    }

    private fun JsonObject.toRadio(): Radio {
        val data = this["data"]?.jsonObject ?: this
        val imageObject = this["pictures"]?.jsonArray?.firstOrNull()?.jsonObject.orEmpty()
        val md5 = imageObject["md5"]?.jsonPrimitive?.content.orEmpty()
        val type = imageObject["type"]?.jsonPrimitive?.content.orEmpty()
        return Radio(
            id = data["id"]?.jsonPrimitive?.content.orEmpty(),
            title = data["title"]?.jsonPrimitive?.content.orEmpty(),
            cover = getCover(md5, type),
            extras = mapOf(
                "radio" to "flow"
            )
        )
    }

    private fun getCover(md5: String?, type: String?): ImageHolder? {
        if (md5.isNullOrEmpty() || type.isNullOrEmpty()) {
            return null
        }
        val size = session.settings?.getInt("image_quality") ?: 240
        return "https://cdn-images.dzcdn.net/images/$type/$md5/${size}x${size}-000000-80-0-0.jpg"
            .toImageHolder()
    }

    private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    private fun String.toDate(): EchoDate = EchoDate(
        year = substringBefore("-").toInt(),
        month = substringAfter("-").substringBeforeLast("-").toInt(),
        day = substringAfterLast("-").substringBefore(" ").toInt()
    )

    private fun Long.toDate(): EchoDate {
        val date = Date(this * 1000)
        return simpleDateFormat.format(date).toDate()
    }

    private fun parseDate(dateStr: String): EchoDate? {
        return if (dateStr.contains("-")) {
            dateStr.toDate()
        } else {
            dateStr.toLongOrNull()?.toDate()
        }
    }

    private fun parseArtists(artistArray: JsonArray?, data: JsonObject): List<Artist> {
        return if (!artistArray.isNullOrEmpty()) {
            artistArray.mapNotNull { element ->
                val obj = element.jsonObject
                Artist(
                    id = obj["ART_ID"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["ART_NAME"]?.jsonPrimitive?.content.orEmpty(),
                    cover = getCover(
                        obj["ART_PICTURE"]?.jsonPrimitive?.content,
                        "artist"
                    )
                )
            }
        } else {
            listOf(
                Artist(
                    id = data["ART_ID"]?.jsonPrimitive?.content.orEmpty(),
                    name = data["ART_NAME"]?.jsonPrimitive?.content.orEmpty()
                )
            )
        }
    }

    private inline fun <T> JsonArray.mapObjects(transform: (JsonObject) -> T?): List<T> =
        mapNotNull { it.jsonObject.let(transform) }

    private inline fun <R> JsonObject.unwrapAnd(transform: (JsonObject) -> R): R =
        (this["data"]?.jsonObject ?: this["DATA"]?.jsonObject ?: this).let(transform)
}