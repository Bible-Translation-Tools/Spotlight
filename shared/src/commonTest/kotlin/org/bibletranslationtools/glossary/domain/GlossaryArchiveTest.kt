package org.bibletranslationtools.glossary.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlossaryArchiveTest {

    @Test
    fun testIsManifestEntry() {
        assertTrue(GlossaryArchive.isManifestEntry("manifest.yaml"))
        assertTrue(GlossaryArchive.isManifestEntry("es_glossary/manifest.yaml"))

        assertFalse(GlossaryArchive.isManifestEntry("es_glossary/content/manifest.yaml"))
        assertFalse(GlossaryArchive.isManifestEntry("es_glossary/manifest.yml"))
        assertFalse(GlossaryArchive.isManifestEntry("es_glossary/old_manifest.yaml"))
    }

    @Test
    fun testRootDirOf() {
        assertEquals("", GlossaryArchive.rootDirOf("manifest.yaml"))
        assertEquals("es_glossary", GlossaryArchive.rootDirOf("es_glossary/manifest.yaml"))
    }
}
