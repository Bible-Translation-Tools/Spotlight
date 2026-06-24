package org.bibletranslationtools.glossary.ui.drawer.settings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.glossary.Utils
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Progress
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.data.api.ErrorDetails
import org.bibletranslationtools.glossary.data.stet.Stet
import org.bibletranslationtools.glossary.domain.CatalogApi
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.NetworkResult
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.logE
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.bibletranslationtools.glossary.ui.drawer.DrawerComponent
import org.bibletranslationtools.glossary.ui.drawer.DrawerContext
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import spotlight.shared.generated.resources.Res
import spotlight.shared.generated.resources.create_glossary_error
import spotlight.shared.generated.resources.downloading_wait
import spotlight.shared.generated.resources.resource_not_found

interface CreateGlossaryComponent : DrawerContext {
    val model: Value<Model>

    data class ResourceRequest(
        val code: String,
        val lang: String,
        val type: String? = null
    )

    data class Model(
        val isSaving: Boolean = false,
        val sourceLanguage: Language? = null,
        val targetLanguage: Language? = null,
        val resourceRequest: ResourceRequest? = null,
        val error: ErrorDetails? = null,
        val progress: Progress? = null
    )

    fun createGlossary(code: String)
    fun downloadResource(request: ResourceRequest)
    fun clearResourceRequest()
    fun onSourceLanguageClick()
    fun onTargetLanguageClick()
}

class DefaultCreateGlossaryComponent(
    componentContext: ComponentContext,
    parentContext: DrawerContext,
    private val sharedState: CreateGlossaryStateKeeper,
    private val onResourceDownloaded: (Resource) -> Unit,
    private val onGlossaryCreated: (Resource, Glossary) -> Unit,
    private val onSelectLanguage: (type: LanguageType) -> Unit
) : DrawerComponent(componentContext, parentContext), CreateGlossaryComponent, KoinComponent {

    private val glossaryRepository: GlossaryRepository by inject()
    private val catalogApi: CatalogApi by inject()
    private val fileSystemProvider: FileSystemProvider by inject()
    private val resourceContainerAccessor: ResourceContainerAccessor by inject()

    override val model: Value<CreateGlossaryComponent.Model> = sharedState.model

    private val componentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        doOnResume {
            setFullscreen(true)
        }
    }

    override fun createGlossary(code: String) {
        componentScope.launch {
            val sourceLang = model.value.sourceLanguage ?: return@launch
            val targetLang = model.value.targetLanguage ?: return@launch

            sharedState.updateIsSaving(true)

            val resource = withContext(Dispatchers.IO) {
                findResource()
            }

            resource?.let { res ->
                val error = createGlossary(
                    code = code,
                    resource = res,
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang
                )
                sharedState.updateError(error)
            } ?: run {
                sharedState.updateResourceRequest(
                    CreateGlossaryComponent.ResourceRequest(
                        code = code,
                        lang = sourceLang.slug
                    )
                )
            }

            sharedState.updateIsSaving(false)
        }
    }

    override fun downloadResource(request: CreateGlossaryComponent.ResourceRequest) {
        componentScope.launch {
            val sourceLang = model.value.sourceLanguage ?: return@launch
            val targetLang = model.value.targetLanguage ?: return@launch

            val progress = Progress(
                value = -1f,
                message = getString(Res.string.downloading_wait)
            )

            sharedState.updateProgress(progress)

            val resources = withContext(Dispatchers.IO) {
                glossaryRepository.getResources(request.lang)
            }

            val resource = resources.find { it.type != "udb" && it.type != "ulb" }
                ?: resources.firstOrNull { it.type == "ulb" }

            var error: ErrorDetails? = null

            if (resource != null) {
                val id = "${resource.lang}_${resource.type}"
                val filename = "$id.zip"
                val result = catalogApi.downloadResource(resource.url)

                if (result is NetworkResult.Success) {
                    val path = fileSystemProvider.saveSource(result.data, filename)
                    val res = resourceContainerAccessor.read(path)!!

                    glossaryRepository.addResource(res)
                    val resDb = glossaryRepository.getResource(res.lang, res.type)!!

                    val resCopy = res.copy(id = resDb.id, url = resDb.url)

                    onResourceDownloaded(resCopy)

                    createGlossary(
                        code = request.code,
                        resource = resCopy,
                        sourceLanguage = sourceLang,
                        targetLanguage = targetLang
                    )
                } else {
                    error = (result as NetworkResult.Error).message
                }
            } else {
                error = ErrorDetails(
                    error = getString(Res.string.resource_not_found)
                )
            }

            sharedState.updateProgress(null)
            sharedState.updateError(error)
        }
    }

    override fun clearResourceRequest() {
        sharedState.updateResourceRequest(null)
    }

    override fun onSourceLanguageClick() {
        onSelectLanguage(LanguageType.SOURCE)
    }

    override fun onTargetLanguageClick() {
        onSelectLanguage(LanguageType.TARGET)
    }

    private suspend fun createGlossary(
        code: String,
        resource: Resource,
        sourceLanguage: Language,
        targetLanguage: Language
    ): ErrorDetails? {
        val glossary = Glossary(
            code = code,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            version = 1,
            resourceId = resource.id
        )

        return runCatching {
            val id = withContext(Dispatchers.IO) {
                glossaryRepository.addGlossary(glossary)
            }
            withContext(Dispatchers.IO) {
                populateStemItems(id, sourceLanguage)
            }
            onGlossaryCreated(resource, glossary.copy(id = id))
            null
        }.getOrElse { e ->
            ErrorDetails(
                error = e.message ?: getString(Res.string.create_glossary_error)
            )
        }
    }

    private suspend fun findResource(): Resource? {
        return model.value.sourceLanguage?.let { language ->
            val dbRes = glossaryRepository.getResources(language.slug)
                .singleOrNull {
                    it.type != "udb" && it.filename.isNotEmpty()
                }
            dbRes?.let {
                resourceContainerAccessor.read(dbRes.filename)
                    ?.copy(id = dbRes.id, url = dbRes.url)
            }
        }
    }

    private suspend fun populateStemItems(glossaryId: String, sourceLanguage: Language) {
        val stetPath = "files/stet/stet_${sourceLanguage.slug}.json"
        val stetBytes = try {
            Res.readBytes(stetPath)
        } catch (e: Exception){
            this.logE("Failed to read stet file: $stetPath", e)
            null
        }
        val stet = stetBytes?.let {
            Utils.JsonLenient.decodeFromString<List<Stet>>(String(it))
        }
        stet?.forEach { item ->
            val words = item.alternatives.ifEmpty { listOf(item.word) }
                .sortedWith(compareBy({ it.lowercase() }, { it }))
                .distinctBy { it.lowercase() }

            words.forEach { word ->
                val phrase = Phrase(
                    phrase = word,
                    spelling = "",
                    description = item.description,
                    glossaryId = glossaryId
                )
                glossaryRepository.addPhrase(phrase)
            }
        }
    }
}