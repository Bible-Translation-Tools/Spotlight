import { ReviewStatusType, RoleType } from "./db/schema";
import { User } from "./user.types";
import { Manifest } from "./resource.types";

export interface Phrase {
  id: string;
  phrase: string;
  spelling: string;
  description: string;
  audio: string;
  createdAt: string;
  updatedAt: string;
}

export interface Resource {
  language: string;
  type: string;
  version: string;
}

/** manifest.yaml of a glossary backup: an RC manifest plus a `glossary` section. */
export interface GlossaryManifest extends Manifest {
  glossary: {
    format_version: number;
    code: string;
    id?: string | null;
  };
}

export interface Glossary {
  id: string | null;
  code: string;
  sourceLanguage: string;
  targetLanguage: string;
  createdAt: string;
  updatedAt: string;
  resource: Resource;
  phrases: Phrase[];
}

export interface GlossaryUpdate {
  id: string;
  version: number;
  createdAt: number;
  updatedAt: number;
}

export interface PhraseReview {
  phrase: string;
  status: ReviewStatusType;
  user: User;
}

export interface GlossaryUser {
  user: User;
  role: RoleType;
}

export interface PendingPhrase {
  phrase: Phrase;
  user: User;
  original: Phrase | null;
  reviews: PhraseReview[];
  status: ReviewStatusType;
}
