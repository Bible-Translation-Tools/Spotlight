package org.bibletranslationtools.glossary

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.yaml.snakeyaml.LoaderOptions
import org.bibletranslationtools.glossary.toLocalDateTime as toLocalDateTimeExt

object CustomLocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CustomLocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val string = decoder.decodeString()
        return string.toLocalDateTimeExt()
    }
}

object Utils {
    val JsonLenient = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true

        serializersModule = SerializersModule {
            contextual(LocalDateTime::class, CustomLocalDateTimeSerializer)
        }
    }

    val Yaml: ObjectMapper = ObjectMapper(
        YAMLFactory.builder()
            // SnakeYAML rejects documents over 3M code points by default,
            // a large glossary's content.yml can exceed that
            .loaderOptions(LoaderOptions().apply { codePointLimit = 100 * 1024 * 1024 })
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            // Keep long descriptions on one line instead of wrapping them
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            // Multi-line descriptions as `|` blocks, readable and editable by hand
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .build()
    )
        .registerKotlinModule()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    fun randomCode(): String {
        val charPool = (('A'..'Z') + ('0'..'9'))
            .filterNot { char ->
                when (char) {
                    'O', '0', // Zero and O
                    'I', '1', // One and I
                    'L',      // L (can look like 1 or I)
                    'S', '5', // Five and S
                    'Z', '2', // Two and Z
                    'B', '8'  // Eight and B
                        -> true
                    else -> false
                }
            }
        return (1..5)
            .map { Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    @OptIn(ExperimentalUuidApi::class)
    fun generateUUID(): String {
        return Uuid.random().toString()
    }

    @OptIn(ExperimentalTime::class)
    fun getCurrentTime() =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    fun slideHorizontally(): StackAnimator {
        return slide(
            animationSpec = customTween(),
            orientation = Orientation.Horizontal
        )
    }

    fun slideVertically(): StackAnimator {
        return slide(
            animationSpec = customTween(),
            orientation = Orientation.Vertical
        )
    }

    private fun <T> customTween(): TweenSpec<T> {
        return tween(
            delayMillis = 20,
            durationMillis = 300,
            easing = EaseIn
        )
    }
}