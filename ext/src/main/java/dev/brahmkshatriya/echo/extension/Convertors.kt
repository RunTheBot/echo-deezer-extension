package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.Date

suspend fun JsonElement.toMediaItemsContainer(
    name: String?
): MediaItemsContainer {
    val itemsArray = jsonObject["items"]!!.jsonArray
    return MediaItemsContainer.Category(
        title = name ?: "Unknown",
        list = itemsArray.mapNotNull { item ->
            item.jsonObject.toEchoMediaItem()
        }
    )
}
suspend fun JsonObject.toMediaItemsContainer(
    name: String?
): MediaItemsContainer {
    return MediaItemsContainer.Category(
        title = name ?: "Unknown",
        list = listOf(jsonObject.toEchoMediaItem() ?: emptyList<EchoMediaItem>().first())
    )
}

suspend fun JsonArray.toMediaItemsContainer(
    name: String?
): MediaItemsContainer {
    val itemsArray = jsonArray
    return MediaItemsContainer.Category(
        title = name ?: "Unknown",
        list = itemsArray.mapNotNull { item ->
            item.jsonObject.toEchoMediaItem()
        }
    )
}

suspend fun JsonElement.toEchoMediaItem(): EchoMediaItem? {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject
    val type = data["__TYPE__"]!!.jsonPrimitive.content
    return when {
        type.contains("playlist") -> EchoMediaItem.Lists.PlaylistItem(toPlaylist())
        type.contains("album") -> EchoMediaItem.Lists.AlbumItem(toAlbum())
        type.contains("song") -> EchoMediaItem.TrackItem(toTrack())
        type.contains("artist") -> EchoMediaItem.Profile.ArtistItem(toArtist())
        type.contains("show") -> EchoMediaItem.Lists.AlbumItem(toShow())
        type.contains("episode") -> EchoMediaItem.TrackItem(toEpisode())
        else -> null
    }
}

fun JsonElement.toShow(): Album {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject["DATA"]?.jsonObject ?: jsonObject
    val md5 = data["SHOW_ART_MD5"]?.jsonPrimitive?.content ?: ""
    return Album(
        id = data["SHOW_ID"]?.jsonPrimitive?.content ?: "",
        title = data["SHOW_NAME"]?.jsonPrimitive?.content ?: "",
        cover = getCover(md5, "talk"),
        tracks = jsonObject["EPISODES"]?.jsonObject?.get("total")?.jsonPrimitive?.int,
        artists = listOf(
            Artist(
                id = "",
                name = "",
            )
        ),
        description = data["SHOW_DESCRIPTION"]?.jsonPrimitive?.content ?: "",
        extras = mapOf(
            "__TYPE__" to "show"
        )
    )
}

fun JsonElement.toEpisode(): Track {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject["DATA"]?.jsonObject ?: jsonObject
    val md5 = data["SHOW_ART_MD5"]?.jsonPrimitive?.content ?: ""
    return Track(
        id = data["EPISODE_ID"]?.jsonPrimitive?.content ?: "",
        title = data["EPISODE_TITLE"]?.jsonPrimitive?.content ?: "",
        cover = getCover(md5, "talk"),
        duration = data["DURATION"]?.jsonPrimitive?.content?.toLong()?.times(1000),
        //releaseDate = Date.from(Instant.ofEpochSecond(data["EPISODE_PUBLISHED_TIMESTAMP"]?.jsonPrimitive?.content?.toLong() ?: 0)).toString(),
        audioStreamables = listOf(
            Streamable(
              id = data["EPISODE_DIRECT_STREAM_URL"]?.jsonPrimitive?.content ?: "",
              quality = 1
            )
        ),
        extras = mapOf(
            "TRACK_TOKEN" to (data["TRACK_TOKEN"]?.jsonPrimitive?.content ?: ""),
            "FILESIZE_MP3_MISC" to (data["FILESIZE_MP3_MISC"]?.jsonPrimitive?.content ?: "0"),
            "__TYPE__" to "show"
        )
    )
}

fun JsonElement.toAlbum(): Album {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject["DATA"]?.jsonObject ?: jsonObject
    val md5 = data["ALB_PICTURE"]?.jsonPrimitive?.content ?: ""
    val artistObject = data["ARTISTS"]?.jsonArray?.first()?.jsonObject
    val artistMd5 = artistObject?.get("ART_PICTURE")?.jsonPrimitive?.content ?: ""
    return Album(
        id = data["ALB_ID"]?.jsonPrimitive?.content ?: "",
        title = data["ALB_TITLE"]?.jsonPrimitive?.content ?: "",
        cover = getCover(md5, "cover"),
        tracks = jsonObject["SONGS"]?.jsonObject?.get("total")?.jsonPrimitive?.int,
        artists = listOf(
            Artist(
                id = artistObject?.get("ART_ID")?.jsonPrimitive?.content ?: "",
                name = artistObject?.get("ART_NAME")?.jsonPrimitive?.content ?: "",
                cover = getCover(artistMd5, "artist")
            )
        ),
        description = jsonObject["description"]?.jsonPrimitive?.content ?: "",
        subtitle = jsonObject["subtitle"]?.jsonPrimitive?.content ?: "",
    )
}

suspend fun JsonElement.toArtist(): Artist {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject
    val md5 = data["ART_PICTURE"]?.jsonPrimitive?.content ?: ""
    val id = data["ART_ID"]?.jsonPrimitive?.content ?: ""
    val isFollowing = DeezerExtension().isFollowingArtist(id)
    return Artist(
        id = id,
        name = data["ART_NAME"]?.jsonPrimitive?.content ?: "",
        cover = getCover(md5, "artist"),
        description = jsonObject["description"]?.jsonPrimitive?.content ?: "",
        subtitle = jsonObject["subtitle"]?.jsonPrimitive?.content ?: "",
        isFollowing = isFollowing
    )
}

@Suppress("NewApi")
fun JsonElement.toTrack(): Track {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject
    val md5 = data["ALB_PICTURE"]?.jsonPrimitive?.content ?: ""
    val artistObject = data["ARTISTS"]?.jsonArray?.first()?.jsonObject
    val artistMd5 = artistObject?.get("ART_PICTURE")?.jsonPrimitive?.content ?: ""
    return Track(
        id = data["SNG_ID"]!!.jsonPrimitive.content,
        title = data["SNG_TITLE"]!!.jsonPrimitive.content,
        cover = getCover(md5, "cover"),
        duration = data["DURATION"]?.jsonPrimitive?.content?.toLong()?.times(1000),
        releaseDate = Date.from(Instant.ofEpochSecond(data["DATE_ADD"]?.jsonPrimitive?.content?.toLong() ?: 0)).toString(),
        artists = listOf(
            Artist(
                id = artistObject?.get("ART_ID")?.jsonPrimitive?.content ?: "",
                name = artistObject?.get("ART_NAME")?.jsonPrimitive?.content ?: "",
                cover = getCover(artistMd5, "artist")
            )
        ),
        extras = mapOf(
            "TRACK_TOKEN" to (data["TRACK_TOKEN"]?.jsonPrimitive?.content ?: ""),
            "FILESIZE_MP3_MISC" to (data["FILESIZE_MP3_MISC"]?.jsonPrimitive?.content ?: "0")
        )
    )
}

fun JsonElement.toPlaylist(): Playlist {
    val data = jsonObject["data"]?.jsonObject ?: jsonObject["DATA"]?.jsonObject ?: jsonObject
    val type = data["PICTURE_TYPE"]?.jsonPrimitive?.content ?: ""
    val md5 = data["PLAYLIST_PICTURE"]?.jsonPrimitive?.content ?: ""
    return Playlist(
        id = data["PLAYLIST_ID"]?.jsonPrimitive?.content ?: "",
        title = data["TITLE"]?.jsonPrimitive?.content ?: "",
        cover = getCover(md5, type),
        description = data["DESCRIPTION"]?.jsonPrimitive?.content ?: "",
        subtitle = jsonObject["subtitle"]?.jsonPrimitive?.content ?: "",
        isEditable = data["PARENT_USER_ID"]!!.jsonPrimitive.content == DeezerCredentialsHolder.credentials?.userId,
        tracks = data["NB_SONG"]?.jsonPrimitive?.int ?: 0,
    )
}

fun getCover(md5: String, type: String): ImageHolder {
    val url = "https://e-cdns-images.dzcdn.net/images/$type/$md5/1200x1200-000000-80-0-0.jpg".toImageHolder()
    return url
}