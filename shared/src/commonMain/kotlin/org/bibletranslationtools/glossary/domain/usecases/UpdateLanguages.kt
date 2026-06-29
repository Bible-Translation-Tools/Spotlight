package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.toEntity
import org.bibletranslationtools.glossary.domain.persistence.LanguageDataSource

class UpdateLanguages(
    private val languageDataSource: LanguageDataSource,
    private val httpClient: HttpClient
) {
    companion object {
        const val LANGNAMES_URL = "https://langnames.bibleineverylanguage.org/langnames.json"
    }

    suspend fun fromUrl(): Int {
        val bytes = httpClient.get(LANGNAMES_URL).bodyAsBytes()
        return replaceAll(parse(bytes))
    }

    suspend fun fromFile(file: PlatformFile): Int {
        return replaceAll(parse(file.readBytes()))
    }

    private fun parse(bytes: ByteArray): List<Language> {
        return Utils.JsonLenient.decodeFromString<List<Language>>(String(bytes))
    }

    private suspend fun replaceAll(languages: List<Language>): Int {
        val newSlugs = languages.map { it.slug }.toSet()
        // In-use languages are referenced by glossaries/resources via FK and
        // can't be deleted. Prune only unreferenced languages absent from the
        // new set; upsert the rest (update existing, insert new).
        val existingSlugs = languageDataSource.getAll().map { it.slug }.toSet()
        val keepSlugs = newSlugs + languageDataSource.getReferencedSlugs()
        val toDelete = existingSlugs.filter { it !in keepSlugs }
        val addedCount = newSlugs.count { it !in existingSlugs }

        languageDataSource.transaction {
            toDelete.forEach { slug ->
                languageDataSource.deleteBySlugInTransaction(slug)
            }
            languages.forEach { language ->
                languageDataSource.insertInTransaction(language.toEntity())
            }
        }
        return addedCount
    }
}
