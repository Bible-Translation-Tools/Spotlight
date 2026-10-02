package org.bibletranslationtools.glossary.data.api

import com.fasterxml.jackson.annotation.JsonProperty

/** .apps/spotlight/glossary.yaml of a glossary backup: what the RC manifest has no place for. */
data class ManifestGlossary(
    @JsonProperty("format_version")
    val formatVersion: Int? = null,
    val code: String,
    val id: String? = null
)
