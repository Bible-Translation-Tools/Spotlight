export interface DublinCoreLanguage {
  identifier: string;
  title: string;
  // Empty when unknown, e.g. in glossary backups built by the server
  direction: "ltr" | "rtl" | "";
}

export interface Source {
  identifier: string;
  language: string;
  version: string;
}

export interface DublinCore {
  type: string;
  conformsto: string;
  format: string;
  identifier: string;
  title: string;
  subject: string;
  description: string;
  language: DublinCoreLanguage;
  rights: string;
  creator: string;
  contributor: string[];
  relation: string[];
  source: Source[];
  publisher: string;
  issued: string;
  modified: string;
  version: string;
}

export interface Checking {
  checking_entity: string[];
  checking_level: string;
}

export interface Project {
  title: string;
  versification: string;
  identifier: string;
  sort: number;
  path: string;
  categories: string[];
}

export interface Manifest {
  dublin_core: DublinCore;
  checking: Checking;
  projects: Project[];
}
