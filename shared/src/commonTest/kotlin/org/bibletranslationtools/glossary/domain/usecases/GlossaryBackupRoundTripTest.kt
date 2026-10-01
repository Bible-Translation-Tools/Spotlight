package org.bibletranslationtools.glossary.domain.usecases

import io.github.vinceglb.filekit.PlatformFile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.io.files.Path
import org.bibletranslationtools.glossary.BaseTest
import org.bibletranslationtools.glossary.data.Glossary
import org.bibletranslationtools.glossary.data.Language
import org.bibletranslationtools.glossary.data.Phrase
import org.bibletranslationtools.glossary.data.Resource
import org.bibletranslationtools.glossary.domain.FileSystemProvider
import org.bibletranslationtools.glossary.domain.FileSystemProviderImpl
import org.bibletranslationtools.glossary.domain.GlossaryArchive
import org.bibletranslationtools.glossary.domain.persistence.GlossaryRepository
import org.bibletranslationtools.glossary.platform.ResourceContainerAccessor
import org.wycliffeassociates.resourcecontainer.ResourceContainer
import spotlight.shared.generated.resources.Res
import org.wycliffeassociates.resourcecontainer.entity.Source
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlossaryBackupRoundTripTest : BaseTest() {

    private val repository: GlossaryRepository = mockk()
    private val resourceContainerAccessor: ResourceContainerAccessor = mockk()
    private lateinit var fileSystemProvider: FileSystemProvider

    private val english = Language("en", "English", "ltr")
    private val spanish = Language("es", "Spanish", "ltr")
    private val time = LocalDateTime(2024, 1, 1, 0, 0)

    private val resource = Resource(
        id = 1L,
        lang = "en",
        type = "ulb",
        version = "1",
        format = "usfm",
        url = "",
        filename = "en_ulb.zip",
        createdAt = time,
        modifiedAt = time
    )

    private val glossary = Glossary(
        id = "g1",
        code = "G1",
        sourceLanguage = english,
        targetLanguage = spanish,
        version = 3,
        resourceId = 1L,
        createdAt = time,
        updatedAt = time,
        remoteId = "remote-g1"
    )

    private val longDescription = "word ".repeat(100).trim()

    private val phrases = listOf(
        phrase("Holy Spirit", spelling = "Espíritu Santo", description = "line 1\nline 2\n", audio = "a.mp3"),
        phrase("Lord", description = longDescription),
        phrase("LORD"),
        phrase("Святой Дух"),
        phrase("სული წმინდა"),
        phrase("聖靈"),
        phrase("روح القدس"),
        phrase("🙏"),
        // Values a careless YAML writer or reader would turn into other types
        phrase("no", spelling = "yes", description = "null"),
        phrase("123", spelling = "1.0", description = "~"),
        phrase("- dash", spelling = "key: value", description = "# not a comment"),
        phrase("  padded  ", spelling = "\"quoted\"", description = "'single'")
    )
    private val pendingPhrases = listOf(
        phrase("grace", description = "pending")
    )

    @BeforeTest
    override fun setUp() {
        super.setUp()
        fileSystemProvider = FileSystemProviderImpl()
    }

    @Test
    fun testExportWritesYamlFiles() = runTest {
        val target = exportBackup(phrases, pendingPhrases)

        val entries = ZipFile(target.toString()).use { zip ->
            zip.entries().toList().filter { !it.isDirectory }.map { it.name }.toSet()
        }
        assertEquals(
            setOf(
                "es_glossary/manifest.yaml",
                "es_glossary/LICENSE.md",
                "es_glossary/content/phrases.yaml",
                "es_glossary/content/pending_phrases.yaml",
                "es_glossary/en_ulb.zip"
            ),
            entries
        )

        assertEquals(
            Res.readBytes(GlossaryArchive.LICENSE_ASSET).decodeToString(),
            readEntry(target, "es_glossary/LICENSE.md")
        )

        val manifest = readEntry(target, "es_glossary/manifest.yaml")
        assertTrue(manifest.startsWith("dublin_core:\n"), manifest)
        assertTrue(
            manifest.contains("glossary:\n  format_version: 1\n  code: \"G1\"\n  id: \"remote-g1\""),
            manifest
        )

        val content = readEntry(target, "es_glossary/content/phrases.yaml")
        // Multi-line text as a literal block, long text not wrapped
        assertTrue(content.contains("description: |\n    line 1\n    line 2\n"), content)
        assertTrue(content.lines().any { it.endsWith("\"$longDescription\"") }, content)
        // Sorted by phrase
        val order = Regex("^- phrase: \"(.*)\"$", RegexOption.MULTILINE)
            .findAll(content).map { it.groupValues[1] }.toList()
        assertEquals(phrases.map { it.phrase.replace("\"", "\\\"") }.sorted(), order)
    }

    @Test
    fun testExportIsResourceContainer() = runTest {
        val target = exportBackup(phrases, pendingPhrases)

        ResourceContainer.load(File(target.toString())).use { rc ->
            assertEquals("0.2", rc.conformsTo())
            assertEquals("dict", rc.type())
            with(rc.manifest.dublinCore) {
                assertEquals("glossary", identifier)
                assertEquals("text/yaml", format)
                assertEquals("CC BY-SA 4.0", rights)
                assertEquals("es", language.identifier)
                assertEquals("Spanish", language.title)
                assertEquals("ltr", language.direction)
                assertEquals(listOf(Source("ulb", "en", "1")), source)
                assertEquals(listOf("en/ulb"), relation)
                assertEquals("3", version)
                assertEquals("2024-01-01T00:00", issued)
            }
            assertEquals(listOf("glossary"), rc.projectIds())
            assertEquals("./content", rc.project()?.path)
            assertTrue(rc.accessor.fileExists("content/phrases.yaml"))
            assertTrue(rc.accessor.fileExists("LICENSE.md"))
        }
    }

    @Test
    fun testImportRestoresExportedGlossary() = runTest {
        val target = exportBackup(phrases, pendingPhrases)

        val imported = importBackup(target)

        with(imported.glossary) {
            assertEquals("G1", code)
            assertEquals("remote-g1", remoteId)
            assertEquals(3, version)
            assertEquals(english, sourceLanguage)
            assertEquals(spanish, targetLanguage)
        }
        assertEquals(
            phrases.map { it.copy(glossaryId = "g2") }.sortedBy { it.phrase },
            imported.phrases
        )
        assertEquals(pendingPhrases.map { it.copy(glossaryId = "g2") }, imported.pendingPhrases)
    }

    @Test
    fun testImportEmptyGlossary() = runTest {
        val target = exportBackup(emptyList(), emptyList())

        val imported = importBackup(target)

        assertEquals(emptyList(), imported.phrases)
        assertEquals(emptyList(), imported.pendingPhrases)
    }

    @Test
    fun testImportGlossaryLargerThanDefaultYamlLimit() = runTest {
        // SnakeYAML rejects documents over 3M code points unless configured otherwise
        val description = "description ".repeat(40)
        val manyPhrases = (1..8000).map { phrase("phrase $it", description = description) }
        val target = exportBackup(manyPhrases, emptyList())
        assertTrue(readEntry(target, "es_glossary/content/phrases.yaml").length > 3 * 1024 * 1024)

        val imported = importBackup(target)

        assertEquals(manyPhrases.size, imported.phrases.size)
    }

    private fun phrase(
        text: String,
        spelling: String = "",
        description: String = "",
        audio: String? = null
    ) = Phrase(
        phrase = text,
        spelling = spelling,
        description = description,
        audio = audio,
        createdAt = time,
        updatedAt = time
    )

    private suspend fun exportBackup(phrases: List<Phrase>, pendingPhrases: List<Phrase>): Path {
        fileSystemProvider.writeFile("resource", Path(fileSystemProvider.sources, resource.filename))

        coEvery { repository.getResource(1L) } returns resource
        coEvery { repository.getPhrases("g1") } returns phrases
        coEvery { repository.getPendingPhrases("g1") } returns pendingPhrases

        val target = Path(testRootDir, "backup.zip")
        ExportGlossary(repository, fileSystemProvider)(glossary, PlatformFile(target))
        return target
    }

    private data class Imported(
        val glossary: Glossary,
        val phrases: List<Phrase>,
        val pendingPhrases: List<Phrase>
    )

    private suspend fun importBackup(target: Path): Imported {
        val importedGlossary = slot<Glossary>()
        val importedPhrases = slot<List<Phrase>>()
        val importedPendingPhrases = slot<List<Phrase>>()

        every { resourceContainerAccessor.read(any<Path>()) } returns resource
        coEvery { repository.addResource(any()) } returns Unit
        coEvery { repository.getResource("en", "ulb") } returns resource
        coEvery { repository.getLanguage("en") } returns english
        coEvery { repository.getLanguage("es") } returns spanish
        coEvery { repository.addGlossary(capture(importedGlossary)) } returns "g2"
        coEvery { repository.batchAddPhrases(capture(importedPhrases)) } returns Unit
        coEvery { repository.batchAddPendingPhrases(capture(importedPendingPhrases)) } returns Unit
        coEvery { repository.getGlossaries() } returns listOf(glossary)

        val importGlossary = ImportGlossary(repository, fileSystemProvider, resourceContainerAccessor)
        assertEquals(glossary, importGlossary.findExisting(PlatformFile(target)))
        importGlossary(PlatformFile(target))

        return Imported(
            importedGlossary.captured,
            importedPhrases.captured,
            importedPendingPhrases.captured
        )
    }

    private fun readEntry(zipFile: Path, name: String): String {
        return ZipFile(zipFile.toString()).use { zip ->
            zip.getInputStream(zip.getEntry(name)).readBytes().decodeToString()
        }
    }
}
