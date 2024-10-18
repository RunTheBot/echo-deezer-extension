package dev.brahmkshatriya.echo.extension.clients

import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.DeezerApi
import dev.brahmkshatriya.echo.extension.DeezerExtension
import dev.brahmkshatriya.echo.extension.toShelfCategoryList
import dev.brahmkshatriya.echo.extension.toShelfItemsList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeezerHomeFeedClient(private val api: DeezerApi) {

    fun getHomeFeed(): PagedData<Shelf> = PagedData.Single {
        DeezerExtension().handleArlExpiration()
        val homePageResults = api.homePage()["results"]?.jsonObject
        val homeSections = homePageResults?.get("sections")?.jsonArray ?: JsonArray(emptyList())

        homeSections.mapNotNull { section ->
            val id = section.jsonObject["module_id"]!!.jsonPrimitive.content
            when (id) {
                "b21892d3-7e9c-4b06-aff6-2c3be3266f68", "348128f5-bed6-4ccb-9a37-8e5f5ed08a62",
                "8d10a320-f130-4dcb-a610-38baf0c57896", "2a7e897f-9bcf-4563-8e11-b93a601766e1",
                "7a65f4ed-71e1-4b6e-97ba-4de792e4af62", "25f9200f-1ce0-45eb-abdc-02aecf7604b2",
                "c320c7ad-95f5-4021-8de1-cef16b053b6d", "b2e8249f-8541-479e-ab90-cf4cf5896cbc",
                "927121fd-ef7b-428e-8214-ae859435e51c" -> {
                    section.toShelfItemsList(section.jsonObject["title"]!!.jsonPrimitive.content)
                }

                "868606eb-4afc-4e1a-b4e4-75b30da34ac8" -> {
                    section.toShelfCategoryList(section.jsonObject["title"]!!.jsonPrimitive.content) { target ->
                        DeezerExtension().channelFeed(target)
                    }
                }

                else -> null
            }
        }
    }
}