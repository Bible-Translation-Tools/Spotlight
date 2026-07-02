package org.bibletranslationtools.glossary.domain.persistence

import org.bibletranslationtools.glossary.GlossaryDatabase
import org.bibletranslationtools.glossary.ResourceEntity

interface ResourceDataSource {
    suspend fun getAll(): List<ResourceEntity>
    suspend fun getById(id: Long): ResourceEntity?
    suspend fun getByLang(lang: String): List<ResourceEntity>
    suspend fun getByLangType(lang: String, type: String): ResourceEntity?
    suspend fun insert(resource: ResourceEntity)
    fun insertInTransaction(resource: ResourceEntity)
    fun transaction(block: () -> Unit)
    suspend fun delete(id: Long)
}

class ResourceDataSourceImpl(db: GlossaryDatabase): ResourceDataSource {
    private val queries = db.resourceQueries

    override suspend fun getAll() = queries.getAll().executeAsList()

    override suspend fun getById(id: Long): ResourceEntity? {
        return queries.getById(id).executeAsOneOrNull()
    }

    override suspend fun getByLang(lang: String): List<ResourceEntity> {
        return queries.getByLang(lang).executeAsList()
    }

    override suspend fun getByLangType(lang: String, type: String): ResourceEntity? {
        return queries.getByLangType(lang, type).executeAsOneOrNull()
    }

    override suspend fun insert(resource: ResourceEntity) {
        insertInTransaction(resource)
    }

    override fun insertInTransaction(resource: ResourceEntity) {
        queries.insert(
            lang = resource.lang,
            type = resource.type,
            version = resource.version,
            format = resource.format,
            url = resource.url,
            filename = resource.filename,
            createdAt = resource.createdAt,
            modifiedAt = resource.modifiedAt
        ).executeAsOne()
    }

    override fun transaction(block: () -> Unit) {
        queries.transaction {
            block()
        }
    }

    override suspend fun delete(id: Long) {
        queries.delete(id)
    }
}