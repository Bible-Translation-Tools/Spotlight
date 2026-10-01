import { dump as dumpYaml, FAILSAFE_SCHEMA, load as parseYaml } from "js-yaml";
import { Glossary, GlossaryInfo, Phrase, Resource } from "./glossary.types";
import { Manifest } from "./resource.types";
import GLOSSARY_LICENSE_TEXT from "./assets/LICENSE.md";

export const GLOSSARY_MANIFEST = "manifest.yaml";
export const GLOSSARY_LICENSE = "LICENSE.md";
export const GLOSSARY_CONTENT = "content/phrases.yaml";
export const GLOSSARY_APP_DIR = ".apps/spotlight";
export const GLOSSARY_INFO = `${GLOSSARY_APP_DIR}/glossary.yaml`;
export const GLOSSARY_SOURCE_DIR = `${GLOSSARY_APP_DIR}/source`;
export const GLOSSARY_IDENTIFIER = "glossary";
export const GLOSSARY_SUBJECT = "Glossary";
export const GLOSSARY_RIGHTS = "CC BY-SA 4.0";
export const GLOSSARY_FORMAT_VERSION = 1;

/** A glossary as loaded from the database, with its phrases and resource. */
export interface StoredGlossary {
  id: string;
  code: string;
  targetLanguage: string;
  version: number;
  createdAt: Date;
  updatedAt: Date;
  resource: Resource;
  phrases: {
    phrase: string;
    spelling: string;
    description: string;
    audio: string;
    createdAt: Date;
    updatedAt: Date;
  }[];
}

const decoder = new TextDecoder();
const encoder = new TextEncoder();

/** Reads a glossary from an unzipped backup, or null if it isn't one. */
export function readGlossaryArchive(
  archive: Record<string, Uint8Array>,
): Glossary | null {
  // All files sit at the zip root
  const manifestFile = archive[GLOSSARY_MANIFEST];
  const infoFile = archive[GLOSSARY_INFO];
  const contentFile = archive[GLOSSARY_CONTENT];
  if (!manifestFile || !infoFile || !contentFile) return null;

  // Failsafe schema reads every value as a string, so a phrase like
  // "no" or "123" can't turn into a boolean or number
  const parse = (file: Uint8Array) =>
    parseYaml(decoder.decode(file), { schema: FAILSAFE_SCHEMA });
  const manifest = parse(manifestFile) as Manifest;
  const info = parse(infoFile) as GlossaryInfo;

  // Failsafe schema reads it as a string
  const formatVersion = Number(info?.format_version);
  if (!Number.isInteger(formatVersion) || formatVersion < 1) {
    throw new Error(
      `Glossary format version missing or invalid in ${GLOSSARY_INFO}.`,
    );
  }
  if (formatVersion > GLOSSARY_FORMAT_VERSION) {
    throw new Error(
      `Glossary format ${formatVersion} is newer than supported ${GLOSSARY_FORMAT_VERSION}.`,
    );
  }
  // Migrations from older formats go here once GLOSSARY_FORMAT_VERSION > 1

  const phrases = (parse(contentFile) ?? []) as Phrase[];

  const dublinCore = manifest.dublin_core;
  const source = dublinCore.source?.[0];
  if (!source) {
    throw new Error(`Source text not found in ${GLOSSARY_MANIFEST}.`);
  }

  return {
    id: info.id ?? null,
    code: info.code,
    // The source text is always in the glossary's source language
    sourceLanguage: source.language,
    targetLanguage: dublinCore.language.identifier,
    createdAt: dublinCore.issued,
    updatedAt: dublinCore.modified,
    resource: {
      language: source.language,
      type: source.identifier,
      version: source.version,
    },
    phrases,
  };
}

/** Zip contents of a glossary backup, ready for zipSync. */
export function buildGlossaryArchive(
  glossary: StoredGlossary,
  resourceFilename: string,
  resourceBytes: Uint8Array,
): Record<string, Uint8Array> {
  const { language, type, version } = glossary.resource;
  const manifest: Manifest = {
    dublin_core: {
      conformsto: "rc0.2",
      type: "dict",
      format: "text/yaml",
      identifier: GLOSSARY_IDENTIFIER,
      title: `${GLOSSARY_SUBJECT} ${glossary.code}`,
      subject: GLOSSARY_SUBJECT,
      description: "",
      // The server doesn't know language names or directions
      language: { identifier: glossary.targetLanguage, title: "", direction: "" },
      source: [{ identifier: type, language, version }],
      relation: [`${language}/${type}`],
      rights: GLOSSARY_RIGHTS,
      creator: "",
      contributor: [],
      publisher: "",
      issued: glossary.createdAt.toISOString(),
      modified: glossary.updatedAt.toISOString(),
      version: String(glossary.version),
    },
    checking: { checking_entity: [], checking_level: "" },
    projects: [
      {
        identifier: GLOSSARY_IDENTIFIER,
        title: GLOSSARY_SUBJECT,
        sort: 1,
        path: "./content",
        versification: "",
        categories: [],
      },
    ],
  };
  const info: GlossaryInfo = {
    format_version: GLOSSARY_FORMAT_VERSION,
    code: glossary.code,
    id: glossary.id,
  };

  // Sorted, so the same glossary always produces the same file
  const phrases = glossary.phrases
    .map((phrase) => ({
      ...phrase,
      createdAt: phrase.createdAt.toISOString(),
      updatedAt: phrase.updatedAt.toISOString(),
    }))
    .sort((a, b) => (a.phrase < b.phrase ? -1 : a.phrase > b.phrase ? 1 : 0));

  // lineWidth -1 keeps long descriptions on one line instead of folding them
  return {
    [GLOSSARY_MANIFEST]: encoder.encode(
      dumpYaml(manifest, { lineWidth: -1 }),
    ),
    [GLOSSARY_LICENSE]: encoder.encode(GLOSSARY_LICENSE_TEXT),
    [GLOSSARY_CONTENT]: encoder.encode(
      dumpYaml(phrases, { lineWidth: -1 }),
    ),
    [GLOSSARY_INFO]: encoder.encode(dumpYaml(info)),
    [`${GLOSSARY_SOURCE_DIR}/${resourceFilename}`]: resourceBytes,
  };
}
