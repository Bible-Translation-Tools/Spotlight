package org.bibletranslationtools.glossary.domain.usecases

import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.toEntity
import org.bibletranslationtools.glossary.domain.CatalogApi
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.ResourceDataSource
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.toLocalDateTime
import kotlin.time.ExperimentalTime

class UpdateCatalog(
    private val catalogApi: CatalogApi,
    private val resourceDataSource: ResourceDataSource
) {
    companion object {
        private val EXCLUDED_LANGUAGES = listOf("el-x-koine")
    }

    @OptIn(ExperimentalTime::class)
    suspend fun fromUrl(): Int {
        val catalog = when (val result = catalogApi.getCatalog()) {
            is NetworkResult.Success -> result.data
            is NetworkResult.Error -> throw IllegalStateException(result.message.error)
        }

        val resources = catalog.languages
            .filter { it.identifier !in EXCLUDED_LANGUAGES }
            .flatMap { lang ->
                lang.resources
                    .filter { it.subject.lowercase() == "bible" }
                    .map { res ->
                        Resource(
                            lang = lang.identifier,
                            type = res.identifier,
                            version = res.version ?: "v1",
                            format = res.formats.first().format,
                            url = res.formats.first().url,
                            filename = "",
                            createdAt = res.issued.toLocalDateTime(),
                            modifiedAt = res.modified.toLocalDateTime()
                        )
                    }
            }

        val existingByKey = resourceDataSource.getAll()
            .associateBy { it.lang to it.type }

        // Counted from actual insert outcomes, not a pre-check - some resources
        // (e.g. a catalog language absent from LanguageEntity) fail their FK
        // constraint on every attempt and must never be counted as added.
        var addedCount = 0
        val failed = mutableListOf<String>()

        resourceDataSource.transaction {
            resources.forEach { resource ->
                val key = resource.lang to resource.type
                val existing = existingByKey[key]

                // The catalog never carries a local filename - that's populated
                // separately once a resource's content is actually downloaded
                // (see InitApp.initResources). Preserve whatever is already in
                // the DB so a catalog refresh doesn't wipe out downloaded content.
                val preservedFilename = existing?.filename.orEmpty()

                try {
                    resourceDataSource.insertInTransaction(
                        resource.copy(filename = preservedFilename).toEntity()
                    )
                    if (existing == null) {
                        addedCount++
                    }
                } catch (e: Exception) {
                    failed.add("${resource.lang}/${resource.type}")
                    this@UpdateCatalog.logE(
                        "Failed to insert resource during catalog update: ${resource.lang}/${resource.type}",
                        e
                    )
                }
            }
        }

        if (failed.isNotEmpty()) {
            this@UpdateCatalog.logE(
                "Catalog update skipped ${failed.size} resource(s) whose language is not in the local languages table: ${failed.joinToString(", ")}"
            )
        }

        return addedCount
    }
}
