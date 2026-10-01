import { ReviewStatusType, RoleType } from "./db/schema";
import { User } from "./user.types";

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

export interface GlossaryManifest {
  id: string | null;
  code: string;
  sourceLanguage: string;
  targetLanguage: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  resource: Resource;
}

export interface Glossary extends Omit<GlossaryManifest, "version"> {
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
