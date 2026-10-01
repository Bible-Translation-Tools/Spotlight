package org.bibletranslationtools.glossary.data.api

import com.fasterxml.jackson.annotation.JsonProperty
import org.wycliffeassociates.resourcecontainer.entity.Checking
import org.wycliffeassociates.resourcecontainer.entity.DublinCore
import org.wycliffeassociates.resourcecontainer.entity.Project

/**
 * manifest.yaml of a glossary backup: a standard RC manifest, built from
 * kotlin-resource-container's entities so the YAML keys match exactly,
 * plus a [glossary] section that RC readers ignore.
 */
data class ManifestGlossary(
    @JsonProperty("dublin_core")
    val dublinCore: DublinCore,
    val checking: Checking = Checking(),
    val projects: List<Project> = emptyList(),
    val glossary: ManifestGlossaryInfo
)

data class ManifestGlossaryInfo(
    @JsonProperty("format_version")
    val formatVersion: Int? = null,
    val code: String,
    val id: String? = null
)
