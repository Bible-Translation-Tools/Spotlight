package org.bibletranslationtools.glossary.domain.persistence

import org.bibletranslationtools.glossary.GlossaryDatabase
import org.bibletranslationtools.glossary.LanguageEntity

interface LanguageDataSource {
    suspend fun getAll(): List<LanguageEntity>
    suspend fun getBySlug(slug: String): LanguageEntity?
    suspend fun getGatewayLanguages(): List<LanguageEntity>
    suspend fun getTargetLanguages(): List<LanguageEntity>
    suspend fun insert(language: LanguageEntity)
    fun insertInTransaction(language: LanguageEntity)
    fun getReferencedSlugs(): List<String>
    fun deleteBySlugInTransaction(slug: String)
    fun transaction(block: () -> Unit)
}

class LanguageDataSourceImpl(db: GlossaryDatabase): LanguageDataSource {
    private val queries = db.languageQueries

    override suspend fun getBySlug(slug: String): LanguageEntity? {
        return queries.getBySlug(slug).executeAsOneOrNull()
    }

    override suspend fun getAll(): List<LanguageEntity> {
        return queries.getAll().executeAsList()
    }

    override suspend fun getGatewayLanguages() =
        queries.getGatewayLangs().executeAsList()

    override suspend fun getTargetLanguages() =
        queries.getTargetLangs().executeAsList()

    override suspend fun insert(language: LanguageEntity) {
        insertInTransaction(language)
    }

    override fun insertInTransaction(language: LanguageEntity) {
        queries.insert(
            slug = language.slug,
            name = language.name,
            angName = language.angName,
            direction = language.direction,
            gw = language.gw
        )
    }

    override fun getReferencedSlugs(): List<String> {
        return queries.getReferencedSlugs().executeAsList()
    }

    override fun deleteBySlugInTransaction(slug: String) {
        queries.deleteBySlug(slug)
    }

    override fun transaction(block: () -> Unit) {
        queries.transaction {
            block()
        }
    }
}