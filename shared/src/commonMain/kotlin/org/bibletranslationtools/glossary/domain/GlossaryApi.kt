package org.bibletranslationtools.glossary.domain

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.toByteArray
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.api.GlossaryUpdate
import org.bibletranslationtools.glossary.data.api.GlossaryUser
import org.bibletranslationtools.glossary.data.api.GlossaryVersion
import org.bibletranslationtools.glossary.data.api.PendingPhrase
import org.bibletranslationtools.glossary.data.api.PhraseReview
import org.bibletranslationtools.glossary.data.api.User
import org.bibletranslationtools.glossary.data.api.UserRole
import org.bibletranslationtools.glossary.ui.state.UserStateHolder

interface GlossaryApi {

    suspend fun verifyLogin(token: String): NetworkResult<User>
    suspend fun login(username: String, password: String): NetworkResult<User>
    suspend fun updateEmoji(emoji: String): NetworkResult<User>
    suspend fun downloadGlossary(code: String): NetworkResult<ByteArray>
    suspend fun uploadGlossary(file: PlatformFile): NetworkResult<GlossaryVersion>
    suspend fun checkUpdates(glossaries: List<GlossaryUpdate>): NetworkResult<List<GlossaryUpdate>>
    suspend fun getGlossaryUsers(glossaryId: String): NetworkResult<List<GlossaryUser>>
    suspend fun joinGlossary(glossaryId: String): NetworkResult<List<GlossaryUser>>
    suspend fun updateUserRole(
        glossaryId: String,
        username: String,
        role: UserRole
    ): NetworkResult<List<GlossaryUser>>
    suspend fun getPendingPhrases(glossaryId: String): NetworkResult<List<PendingPhrase>>
    suspend fun uploadPendingPhrases(
        glossaryId: String,
        phrases: List<Phrase>
    ): NetworkResult<Boolean>
    suspend fun reviewPendingPhrase(
        glossaryId: String,
        phraseReview: PhraseReview
    ): NetworkResult<List<PhraseReview>>
    suspend fun getReviewedPhrases(glossaryId: String): NetworkResult<List<PendingPhrase>>
    suspend fun deleteReviewedPhrases(glossaryId: String): NetworkResult<Boolean>
}

class GlossaryApiImpl(
    private val httpClient: HttpClient,
    private val userStateHolder: UserStateHolder
) : GlossaryApi {

    val user: User?
        get() = userStateHolder.state.value.user

    val token: String
        get() = user?.token ?: "unauthorized"

    companion object {
        const val BASE_URL = "https://glossary.wycliffe-associates-account.workers.dev"
        const val PUBLIC_API = "$BASE_URL/public/api"
        const val PRIVATE_API = "$BASE_URL/private/api"
    }

    override suspend fun verifyLogin(token: String): NetworkResult<User> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PRIVATE_API/user/verify") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Authentication verification error"
                )
            }
        }
    }

    override suspend fun login(username: String, password: String): NetworkResult<User> {
        return ApiHelper.callApi {
            val response = httpClient.post("$PUBLIC_API/user/login") {
                setBody(mapOf("username" to username, "password" to password))
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Authentication error"
                )
            }
        }
    }

    override suspend fun updateEmoji(emoji: String): NetworkResult<User> {
        return ApiHelper.callApi {
            val response = httpClient.post("$PRIVATE_API/user/emoji") {
                bearerAuth(token)
                setBody(mapOf("emoji" to emoji))
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Failed to update emoji"
                )
            }
        }
    }

    override suspend fun downloadGlossary(code: String): NetworkResult<ByteArray> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PUBLIC_API/glossary/$code")

            if (response.status.value in 200..299) {
                val channel = response.bodyAsChannel()
                channel.toByteArray()
            } else {
                throw ServerResponseException(
                    response,
                    "Error downloading glossary"
                )
            }
        }
    }

    override suspend fun uploadGlossary(file: PlatformFile): NetworkResult<GlossaryVersion> {
        return ApiHelper.callApi {
            val response = httpClient.post("$PRIVATE_API/glossary") {
                bearerAuth(token)
                setBody(file.readBytes())
            }

            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error uploading glossary"
                )
            }
        }
    }

    override suspend fun checkUpdates(
        glossaries: List<GlossaryUpdate>
    ): NetworkResult<List<GlossaryUpdate>> {
        return ApiHelper.callApi {
            val response = httpClient.post("$PUBLIC_API/glossary/check_updates") {
                setBody(glossaries)
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error checking for updates"
                )
            }
        }
    }

    override suspend fun getGlossaryUsers(glossaryId: String): NetworkResult<List<GlossaryUser>> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PRIVATE_API/glossary/$glossaryId/users") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error getting glossary users"
                )
            }
        }
    }

    override suspend fun joinGlossary(glossaryId: String): NetworkResult<List<GlossaryUser>> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PRIVATE_API/glossary/$glossaryId/join") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error joining glossary"
                )
            }
        }
    }

    override suspend fun updateUserRole(
        glossaryId: String,
        username: String,
        role: UserRole
    ): NetworkResult<List<GlossaryUser>> {
        return ApiHelper.callApi {
            val response = httpClient.post("$PRIVATE_API/glossary/$glossaryId/role") {
                bearerAuth(token)
                setBody(mapOf("username" to username, "role" to role.name.lowercase()))
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error updating user role"
                )
            }
        }
    }

    override suspend fun getPendingPhrases(glossaryId: String): NetworkResult<List<PendingPhrase>> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PRIVATE_API/glossary/$glossaryId/pending_phrases") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error getting pending phrases"
                )
            }
        }
    }

    override suspend fun uploadPendingPhrases(
        glossaryId: String,
        phrases: List<Phrase>
    ): NetworkResult<Boolean> {
        return ApiHelper.callApi {
            val response = httpClient.post(
                "$PRIVATE_API/glossary/$glossaryId/pending_phrases"
            ) {
                bearerAuth(token)
                setBody(phrases)
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error uploading pending phrases"
                )
            }
        }
    }

    override suspend fun reviewPendingPhrase(
        glossaryId: String,
        phraseReview: PhraseReview
    ): NetworkResult<List<PhraseReview>> {
        return ApiHelper.callApi {
            val response = httpClient.post(
                "$PRIVATE_API/glossary/$glossaryId/review_phrase"
            ) {
                bearerAuth(token)
                setBody(phraseReview)
                contentType(ContentType.Application.Json)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error sending phrase review"
                )
            }
        }
    }

    override suspend fun getReviewedPhrases(glossaryId: String): NetworkResult<List<PendingPhrase>> {
        return ApiHelper.callApi {
            val response = httpClient.get("$PRIVATE_API/glossary/$glossaryId/reviewed_phrases") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error getting reviewed phrases"
                )
            }
        }
    }

    override suspend fun deleteReviewedPhrases(glossaryId: String): NetworkResult<Boolean> {
        return ApiHelper.callApi {
            val response = httpClient.delete("$PRIVATE_API/glossary/$glossaryId/reviewed_phrases") {
                bearerAuth(token)
            }
            if (response.status.value in 200..299) {
                response.body()
            } else {
                throw ServerResponseException(
                    response,
                    "Error deleting reviewed phrases"
                )
            }
        }
    }
}