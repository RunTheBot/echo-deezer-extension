package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.DeezerParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerHomeFeedClient(private val deezerExtension: DeezerExtension, private val api: DeezerApi, private val parser: DeezerParser) {

    fun loadHomeFeed(shelf: String): Feed<Shelf> = PagedData.Single {
        deezerExtension.handleArlExpiration()
        val homePageResults = api.page("home")["results"]?.jsonObject
        val homeSections = homePageResults?.get("sections")?.jsonArray ?: JsonArray(emptyList())

        supervisorScope {
            homeSections.mapNotNull { section ->
                async(Dispatchers.Default) {
                    val obj = section.jsonObject
                    val id = obj["module_id"]?.jsonPrimitive?.content
                    when (id) {
                        in ITEM_MODULE_IDS -> {
                            parser.run {
                                section.toShelfItemsList(
                                    obj["title"]!!.jsonPrimitive.content
                                )
                            }
                        }
                        CATEGORY_MODULE_ID -> {
                            parser.run {
                                section.toShelfCategoryList(
                                    obj["title"]!!.jsonPrimitive.content,
                                    shelf
                                ) { target ->
                                    deezerExtension.channelFeed(target)
                                }
                            }
                        }
                        else -> null
                    }
                }
            }.mapNotNull { it.await() }
        }
    }.toFeed()

    companion object {
        private val ITEM_MODULE_IDS = setOf(
            "b21892d3-7e9c-4b06-aff6-2c3be3266f68", "348128f5-bed6-4ccb-9a37-8e5f5ed08a62",
            "8d10a320-f130-4dcb-a610-38baf0c57896", "2a7e897f-9bcf-4563-8e11-b93a601766e1",
            "7a65f4ed-71e1-4b6e-97ba-4de792e4af62", "25f9200f-1ce0-45eb-abdc-02aecf7604b2",
            "c320c7ad-95f5-4021-8de1-cef16b053b6d", "b2e8249f-8541-479e-ab90-cf4cf5896cbc",
            "927121fd-ef7b-428e-8214-ae859435e51c"
        )

        private const val CATEGORY_MODULE_ID = "868606eb-4afc-4e1a-b4e4-75b30da34ac8"
    }
}