package org.bibletranslationtools.glossary.domain

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import org.bibletranslationtools.glossary.Utils
import spotlight.shared.generated.resources.Res

@Serializable
data class Catalog(
    val languages: List<CatalogLanguage>
)

@Serializable
data class CatalogLanguage(
    val identifier: String,
    val resources: List<CatalogResource>
)

@Serializable
data class CatalogResource(
    val identifier: String,
    val issued: String,
    val modified: String,
    val version: String? = "v1",
    val subject: String,
    val formats: List<CatalogFormat>
)

@Serializable
data class CatalogFormat(
    val format: String,
    val url: String
)

interface CatalogApi {
    suspend fun getCatalog(): NetworkResult<Catalog>
    suspend fun getCatalog(asset: String): Catalog
    suspend fun downloadResource(url: String): NetworkResult<ByteArray>
}

class CatalogApiImpl(private val httpClient: HttpClient): CatalogApi {

    companion object {
        const val CATALOG_URL = "https://api.bibletranslationtools.org/v3/catalog.json"
    }

    override suspend fun getCatalog(): NetworkResult<Catalog> {
        return ApiHelper.callApi {
            httpClient.get(CATALOG_URL).body()
        }
    }

    override suspend fun getCatalog(asset: String): Catalog {
        val bytes = Res.readBytes(asset)
        val json = String(bytes)
        return Utils.JsonLenient.decodeFromString<Catalog>(json)
    }

    override suspend fun downloadResource(url: String): NetworkResult<ByteArray> {
        return ApiHelper.callApi {
            httpClient.get(url).body()
        }
    }
}