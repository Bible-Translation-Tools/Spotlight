package org.bibletranslationtools.glossary.domain

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import org.bibletranslationtools.glossary.Utils.JsonLenient

internal fun createHttpClient(
    engine: HttpClientEngine
): HttpClient {
    val configuration:  HttpClientConfig<*>.() -> Unit = {
        install(HttpRequestRetry) {
            // Only GET is safe to repeat; retrying POST can apply an upload several times
            retryIf(maxRetries = 5) { request, response ->
                request.method == HttpMethod.Get && response.status.value in 500..599
            }
            exponentialDelay()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }
        install(ContentNegotiation) {
            register(
                ContentType.Application.Json,
                KotlinxSerializationConverter(JsonLenient)
            )
        }
    }
    return HttpClient(engine, configuration)
}
