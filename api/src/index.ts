import { Hono } from "hono";
import { cors } from "hono/cors";
import DbHelper from "./db/helper";
import type { JwtVariables } from "hono/jwt";
import {
  glossaryTable,
  glossaryUsers,
  pendingPhraseTable,
  phraseReviews,
  phraseTable,
  resourceTable,
  reviewStatusEnum,
  ReviewStatusType,
  roleEnum,
  RoleType,
  usersTable,
} from "./db/schema";
import { and, eq, gt, sql, or, lt, ne } from "drizzle-orm";
import { unzipSync, zipSync } from "fflate";
import {
  dump as dumpYaml,
  load as parseYaml,
  FAILSAFE_SCHEMA,
} from "js-yaml";
import {
  Glossary,
  GlossaryUpdate,
  Phrase,
  PhraseReview,
  GlossaryUser,
  PendingPhrase,
  GlossaryManifest,
} from "./glossary.types";
import { Manifest } from "./resource.types";
import { ErrorDetails, TokenRes, User, UserRes } from "./user.types";
import { jwt, sign } from "hono/jwt";
import validateEmoji, { GLOSSARY_CONTENT, GLOSSARY_MANIFEST } from "./utils";
import { v4 as uuidv4 } from "uuid";

interface AppVariables extends JwtVariables {
  db: DbHelper;
}

const app = new Hono<{
  Bindings: CloudflareBindings;
  Variables: AppVariables;
}>();

app.use("*", cors());

app.use("*", async (c, next) => {
  const dbHelper = new DbHelper(c.env);
  c.set("db", dbHelper);
  try {
    await next();
  } finally {
    c.executionCtx.waitUntil(dbHelper.close());
  }
});

app.use("/private/api/*", async (c, next) => {
  const jwtMiddleware = jwt({
    secret: c.env.JWT_SECRET_KEY,
    alg: "HS256",
  });
  return jwtMiddleware(c, next);
});

app.post("/public/api/user/login", async (c) => {
  const dbHelper = c.get("db");
  const { username, password } = await c.req.json<{
    username: string;
    password: string;
  }>();

  try {
    const wacsUrl = `https://content.bibletranslationtools.org/api/v1/users/${username}/tokens`;
    const b64Creds = btoa(`${username}:${password}`);
    const res = await fetch(wacsUrl, {
      method: "POST",
      headers: {
        authorization: `Basic ${b64Creds}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        name: `glossary-api-token_${Date.now()}`,
        scopes: ["write:repository", "write:user"],
      }),
    });

    if (!res.ok) {
      const resHeadersDbug: Record<string, string> = {};
      for (const pair of res.headers.entries()) {
        const key = pair[0];
        const v = pair[1];
        if (key.toLowerCase() !== "authorization") {
          resHeadersDbug[key] = v;
        }
      }
      throw new Error("Failed to get WACS api token.");
    }

    const token = (await res.json()) as TokenRes;
    const userEndpoint = `https://content.bibletranslationtools.org/api/v1/user`;
    const apiToken = token.sha1;
    const userRes = await fetch(userEndpoint, {
      headers: {
        Authorization: `token ${apiToken}`,
        Accept: "application/json",
      },
    });
    if (!userRes.ok) {
      const body = await userRes.text();
      throw new Error("Failed to get user info.");
    }
    const userData = (await userRes.json()) as UserRes;

    await dbHelper
      .getDb()
      .insert(usersTable)
      .values({
        email: userData.email,
        username: userData.username,
        wacsUserId: userData.id,
      })
      .onConflictDoUpdate({
        target: usersTable.email,
        set: {
          username: sql`EXCLUDED.username`,
          updatedAt: new Date(),
        },
      });

    const userDb = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.username, userData.username),
    });

    if (!userDb) {
      throw new Error("User is not in the database.");
    }

    const jwtPayload = {
      id: userDb.id,
      username: userData.username,
      emoji: userDb.emoji,
      exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30, // expires in 30 days
    };

    const jwtToken = await sign(jwtPayload, c.env.JWT_SECRET_KEY);

    return c.json({
      username: userData.username,
      emoji: userDb.emoji,
      token: jwtToken,
    });
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to login.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.post("/private/api/user/emoji", async (c) => {
  const dbHelper = c.get("db");
  const auth = c.get("jwtPayload");
  const { emoji } = await c.req.json<{
    emoji: string;
  }>();

  try {
    if (!validateEmoji(emoji)) {
      throw new Error("Invalid emoji.");
    }

    await dbHelper
      .getDb()
      .update(usersTable)
      .set({ emoji: emoji })
      .where(eq(usersTable.id, auth.id));

    // generate a new token with updated emoji
    const jwtPayload = {
      id: auth.id,
      username: auth.username,
      emoji: emoji,
      exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24, // expires in 1 day
    };
    const jwtToken = await sign(jwtPayload, c.env.JWT_SECRET_KEY);

    return c.json({
      username: auth.username,
      emoji: emoji,
      token: jwtToken,
    });
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to update emoji.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

// Just to verify if token is not expired
app.get("/private/api/user/verify", async (c) => {
  const auth = c.get("jwtPayload");
  return c.json({
    username: auth.username,
    emoji: auth.emoji,
    token: "noop",
  });
});

app.post("/private/api/glossary", async (c) => {
  const dbHelper = c.get("db");
  const auth = c.get("jwtPayload");

  try {
    const zipArrayBuffer = await c.req.arrayBuffer();

    if (!zipArrayBuffer || zipArrayBuffer.byteLength === 0) {
      return c.json({ error: "Request body is empty." }, 400);
    }

    const decoder = new TextDecoder();

    let glossary: Glossary | null = null;
    let manifest: Manifest | null = null;

    const mainArchive = unzipSync(new Uint8Array(zipArrayBuffer));

    const glossaryManifestFile = mainArchive[GLOSSARY_MANIFEST];
    const glossaryContentFile = mainArchive[GLOSSARY_CONTENT];
    if (glossaryManifestFile && glossaryContentFile) {
      // Failsafe schema reads every value as a string, so a phrase like
      // "no" or "123" can't turn into a boolean or number
      const glossaryManifest = parseYaml(decoder.decode(glossaryManifestFile), {
        schema: FAILSAFE_SCHEMA,
      }) as GlossaryManifest;
      const phrases = (parseYaml(decoder.decode(glossaryContentFile), {
        schema: FAILSAFE_SCHEMA,
      }) ?? []) as Phrase[];
      glossary = { ...glossaryManifest, phrases };
    }

    const resourceZipFilename = Object.keys(mainArchive).find((name) =>
      name.endsWith(".zip"),
    );
    if (resourceZipFilename) {
      const resourceZipFile = mainArchive[resourceZipFilename];
      const resourceArchive = unzipSync(resourceZipFile);

      const manifestPath = Object.keys(resourceArchive).find((path) =>
        path.endsWith("manifest.yaml"),
      );
      if (manifestPath) {
        const manifestFile = resourceArchive[manifestPath];
        if (manifestFile) {
          const manifestString = decoder.decode(manifestFile);
          manifest = parseYaml(manifestString) as Manifest;
        }
      }

      await c.env.R2_BUCKET.put(resourceZipFilename, resourceZipFile, {
        httpMetadata: {
          contentType: "application/zip",
        },
      });
    }

    if (glossary == null || manifest == null) {
      throw new Error(
        `Could not find ${GLOSSARY_MANIFEST}, ${GLOSSARY_CONTENT} or resource.zip/manifest.yaml`,
      );
    }

    if (glossary.id != null) {
      const existentGlossary = await dbHelper
        .getDb()
        .query.glossaryTable.findFirst({
          where: eq(glossaryTable.id, glossary.id),
        });

      if (existentGlossary) {
        throw new Error("Glossary already exists.");
      }
    }

    const CHUNK_SIZE = 50;
    const glossaryData: Glossary = glossary;
    const manifestData: Manifest = manifest;

    // Single transaction, so a dropped connection leaves no partial glossary behind
    const updated = await dbHelper.getDb().transaction(async (tx) => {
      const insertResource = await tx
        .insert(resourceTable)
        .values({
          language: manifestData.dublin_core.language.identifier,
          type: manifestData.dublin_core.identifier,
          version: manifestData.dublin_core.version,
        })
        .onConflictDoUpdate({
          target: [resourceTable.language, resourceTable.type],
          set: {
            version: manifestData.dublin_core.version,
          },
        })
        .returning({ id: resourceTable.id });
      const resourceId = insertResource[0].id;

      const insertGlossary = await tx
        .insert(glossaryTable)
        .values({
          id: uuidv4(),
          code: glossaryData.code,
          sourceLanguage: glossaryData.sourceLanguage,
          targetLanguage: glossaryData.targetLanguage,
          resourceId: resourceId,
        })
        .onConflictDoUpdate({
          target: [
            glossaryTable.code,
            glossaryTable.sourceLanguage,
            glossaryTable.targetLanguage,
          ],
          set: {
            resourceId: resourceId,
          },
        })
        .returning({ id: glossaryTable.id });

      const glossaryId = insertGlossary[0].id;

      await tx
        .insert(glossaryUsers)
        .values({
          glossaryId: glossaryId,
          userId: auth.id,
          role: "owner",
        })
        .onConflictDoUpdate({
          target: [glossaryUsers.glossaryId, glossaryUsers.userId],
          set: {
            role: "owner",
          },
        });

      const phraseValues = glossaryData.phrases.map((phrase) => ({
        id: uuidv4(),
        phrase: phrase.phrase,
        spelling: phrase.spelling,
        description: phrase.description,
        audio: phrase.audio ?? "",
        glossaryId: glossaryId,
      }));

      for (let i = 0; i < phraseValues.length; i += CHUNK_SIZE) {
        const chunk = phraseValues.slice(i, i + CHUNK_SIZE);
        await tx
          .insert(phraseTable)
          .values(chunk)
          .onConflictDoUpdate({
            target: [phraseTable.phrase, phraseTable.glossaryId],
            set: {
              spelling: sql.raw(`excluded.spelling`),
              description: sql.raw(`excluded.description`),
              audio: sql.raw(`excluded.audio`),
            },
          });
      }

      return tx.query.glossaryTable.findFirst({
        where: eq(glossaryTable.id, glossaryId),
      });
    });

    if (!updated) {
      throw new Error("Could not find glossary.");
    }

    return c.json({ id: updated.id, version: updated.version });
  } catch (error: any) {
    console.error("Upload glossary failed", error);
    return c.json<ErrorDetails>(
      {
        error: "Failed to process glossary file.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.post("/private/api/glossary/:id/role", async (c) => {
  const dbHelper = c.get("db");
  const auth = c.get("jwtPayload");
  const id = c.req.param("id");
  const { username, role } = await c.req.json<{
    username: string;
    role: RoleType;
  }>();

  try {
    const userRole = role as RoleType;

    if (!roleEnum.enumValues.includes(userRole) || userRole === "owner") {
      throw new Error("Invalid role.");
    }

    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const glossaryAdmins = glossary.users
      .filter((user) => ["owner", "admin"].includes(user.role))
      .map((user) => user.user.username);

    if (!glossaryAdmins.includes(auth.username)) {
      throw new Error(
        "You don't have permissions to assign roles to this glossary.",
      );
    }

    const user = await dbHelper.getDb().query.usersTable.findFirst({
      where: eq(usersTable.username, username),
    });

    if (!user) {
      throw new Error("Invalid user.");
    }

    await dbHelper
      .getDb()
      .insert(glossaryUsers)
      .values({
        glossaryId: glossary.id,
        userId: user.id,
        role: userRole,
      })
      .onConflictDoUpdate({
        target: [glossaryUsers.glossaryId, glossaryUsers.userId],
        set: {
          role: userRole,
        },
      });

    const updatedGlossary = await dbHelper
      .getDb()
      .query.glossaryTable.findFirst({
        where: eq(glossaryTable.id, id),
        with: {
          users: {
            with: {
              user: true,
            },
          },
        },
      });

    if (!updatedGlossary) {
      throw new Error("Invalid glossary.");
    }

    return c.json<GlossaryUser[]>(
      updatedGlossary.users.map((user) => {
        return {
          user: {
            username: user.user.username,
            emoji: user.user.emoji,
          },
          role: user.role,
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to assign role.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.get("/public/api/glossary/:code", async (c) => {
  const dbHelper = c.get("db");
  const code = c.req.param("code");

  try {
    const encoder = new TextEncoder();

    // TODO There is a chance to have two or more glossaries with the same code but different IDs
    // Should we handle that case and return a list or just the first one?
    const glossary =
      (await dbHelper.getDb().query.glossaryTable.findFirst({
        where: eq(glossaryTable.code, code),
        with: {
          resource: {
            columns: {
              language: true,
              type: true,
              version: true,
            },
          },
          phrases: {
            columns: {
              phrase: true,
              spelling: true,
              description: true,
              audio: true,
              createdAt: true,
              updatedAt: true,
            },
          },
        },
      })) || null;

    if (!glossary) {
      throw new Error("Glossary with this code doesn't exist.");
    }

    const resourceFilename = `${glossary!.resource.language}_${
      glossary!.resource.type
    }.zip`;

    const resourceFile = await c.env.R2_BUCKET.get(resourceFilename);
    if (resourceFile === null) {
      throw new Error(
        `Resource file "${resourceFilename}" not found in R2 bucket.`,
      );
    }
    const resourceBytes = await resourceFile.arrayBuffer();
    const glossaryManifest: GlossaryManifest = {
      id: glossary.id,
      code: glossary.code,
      sourceLanguage: glossary.sourceLanguage,
      targetLanguage: glossary.targetLanguage,
      version: glossary.version,
      createdAt: glossary.createdAt.toISOString(),
      updatedAt: glossary.updatedAt.toISOString(),
      resource: glossary.resource,
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
    const mainZipContents = {
      [GLOSSARY_MANIFEST]: encoder.encode(
        dumpYaml(glossaryManifest, { lineWidth: -1 }),
      ),
      [GLOSSARY_CONTENT]: encoder.encode(dumpYaml(phrases, { lineWidth: -1 })),
      [resourceFilename]: new Uint8Array(resourceBytes),
    };
    const mainZipBytes = zipSync(mainZipContents);
    const filename = `glossary-${code}.zip`;

    return new Response(mainZipBytes, {
      headers: {
        "Content-Type": "application/zip",
        "Content-Disposition": `attachment; filename="${filename}"`,
      },
    });
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to process raw zip file.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.get("/private/api/glossary/:id/pending_phrases", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
        pendingPhrases: {
          where: (pendingPhrases, { eq }) =>
            eq(pendingPhrases.reviewStatus, "unreviewed"),
          with: {
            user: true,
            original: true,
            reviews: {
              with: {
                user: true,
              },
            },
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const glossaryAdmins = glossary.users
      .filter((user) => ["owner", "admin"].includes(user.role))
      .map((user) => user.user.username);

    if (!glossaryAdmins.includes(auth.username)) {
      throw new Error(
        "You don't have permissions to review phrases in this glossary.",
      );
    }

    return c.json<PendingPhrase[]>(
      glossary.pendingPhrases.map((phrase) => {
        const pendingPhrase: Phrase = {
          id: phrase.id,
          phrase: phrase.phrase,
          spelling: phrase.spelling,
          description: phrase.description,
          audio: phrase.audio,
          createdAt: phrase.createdAt.toISOString(),
          updatedAt: phrase.updatedAt.toISOString(),
        };
        const originalPhrase: Phrase | null = phrase.original
          ? {
              id: phrase.original.id,
              phrase: phrase.original.phrase,
              spelling: phrase.original.spelling,
              description: phrase.original.description,
              audio: phrase.original.audio,
              createdAt: phrase.original.createdAt.toISOString(),
              updatedAt: phrase.original.updatedAt.toISOString(),
            }
          : null;

        return {
          phrase: pendingPhrase,
          user: {
            username: phrase.user.username,
            emoji: phrase.user.emoji,
          } satisfies User,
          original: originalPhrase,
          status: phrase.reviewStatus,
          reviews: phrase.reviews.map((review) => {
            return {
              phrase: phrase.phrase,
              status: review.status,
              user: {
                username: review.user.username,
                emoji: review.user.emoji,
              } satisfies User,
            };
          }),
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to get glossary users.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.post("/private/api/glossary/:id/pending_phrases", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  const pendingPhrases = await c.req.json<Phrase[]>();

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const glossaryEditors = glossary.users
      .filter((user) => ["owner", "admin", "editor"].includes(user.role))
      .map((user) => user.user.username);

    if (!glossaryEditors.includes(auth.username)) {
      throw new Error(
        "You don't have permissions to upload pending phrases in this glossary.",
      );
    }

    const phraseValues = pendingPhrases.map((phrase) => ({
      id: phrase.id,
      phrase: phrase.phrase,
      spelling: phrase.spelling,
      description: phrase.description,
      audio: phrase.audio,
      userId: auth.id,
      glossaryId: glossary.id,
    }));

    const CHUNK_SIZE = 50;

    await dbHelper.getDb().transaction(async (tx) => {
      for (let i = 0; i < phraseValues.length; i += CHUNK_SIZE) {
        const chunk = phraseValues.slice(i, i + CHUNK_SIZE);
        await tx
          .insert(pendingPhraseTable)
          .values(chunk)
          .onConflictDoUpdate({
            target: [
              pendingPhraseTable.phrase,
              pendingPhraseTable.glossaryId,
              pendingPhraseTable.userId,
            ],
            set: {
              spelling: sql.raw(`excluded.spelling`),
              description: sql.raw(`excluded.description`),
              audio: sql.raw(`excluded.audio`),
              reviewStatus: "unreviewed",
            },
          });
      }
    });

    return c.json(true);
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to upload pending phrases.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.post("/private/api/glossary/:id/review_phrase", async (c) => {
  const dbHelper = c.get("db");
  const auth = c.get("jwtPayload");
  const id = c.req.param("id");
  const { phrase, status } = await c.req.json<{
    phrase: string;
    status: ReviewStatusType;
  }>();

  try {
    const reviewStatus = status as ReviewStatusType;

    if (!reviewStatusEnum.enumValues.includes(reviewStatus)) {
      throw new Error("Invalid review status.");
    }

    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const glossaryAdmins = glossary.users
      .filter((user) => ["owner", "admin"].includes(user.role))
      .map((user) => user.user.username);

    if (!glossaryAdmins.includes(auth.username)) {
      throw new Error(
        "You don't have permissions to review phrases in this glossary.",
      );
    }

    const dbPhrase = await dbHelper.getDb().query.pendingPhraseTable.findFirst({
      where: and(
        eq(pendingPhraseTable.phrase, phrase),
        eq(pendingPhraseTable.glossaryId, glossary.id),
      ),
    });

    if (!dbPhrase) {
      return c.json([]);
    }

    await dbHelper
      .getDb()
      .insert(phraseReviews)
      .values({
        phraseId: dbPhrase.id,
        userId: auth.id,
        status: reviewStatus,
      })
      .onConflictDoUpdate({
        target: [phraseReviews.phraseId, phraseReviews.userId],
        set: {
          status: reviewStatus,
        },
      });

    const updatedPhrase = await dbHelper
      .getDb()
      .query.pendingPhraseTable.findFirst({
        where: eq(pendingPhraseTable.id, dbPhrase.id),
        with: {
          reviews: {
            with: {
              user: true,
            },
          },
        },
      });

    if (!updatedPhrase) {
      return c.json([]);
    }

    const adminsCount = glossaryAdmins.length;
    const approvedReviews = updatedPhrase.reviews.filter(
      (review) => review.status === "approved",
    ).length;
    const rejectedReviews = updatedPhrase.reviews.filter(
      (review) => review.status === "rejected",
    ).length;

    let finalStatus: ReviewStatusType = "unreviewed";
    if (approvedReviews / adminsCount >= 0.51) {
      finalStatus = "approved";
    } else if (rejectedReviews / adminsCount >= 0.51) {
      finalStatus = "rejected";
    }

    // If majority approved, move to main phrases
    if (finalStatus == "approved") {
      await dbHelper
        .getDb()
        .insert(phraseTable)
        .values({
          id: updatedPhrase.id,
          phrase: updatedPhrase.phrase,
          spelling: updatedPhrase.spelling,
          description: updatedPhrase.description,
          audio: updatedPhrase.audio,
          glossaryId: glossary.id,
        })
        .onConflictDoUpdate({
          target: [phraseTable.phrase, phraseTable.glossaryId],
          set: {
            spelling: sql.raw(`excluded.spelling`),
            description: sql.raw(`excluded.description`),
            audio: sql.raw(`excluded.audio`),
          },
        });
    }

    if (finalStatus != "unreviewed") {
      await dbHelper
        .getDb()
        .update(pendingPhraseTable)
        .set({
          reviewStatus: finalStatus,
          updatedAt: new Date(),
        })
        .where(eq(pendingPhraseTable.id, updatedPhrase.id));

      return c.json([]);
    } // Otherwise, keep pending phrase for more reviews

    return c.json<PhraseReview[]>(
      updatedPhrase.reviews.map((review) => {
        return {
          phrase: updatedPhrase.phrase,
          status: review.status,
          user: {
            username: review.user.username,
            emoji: review.user.emoji,
          } satisfies User,
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to update pending phrase review status.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.get("/private/api/glossary/:id/reviewed_phrases", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
        pendingPhrases: {
          where: (pendingPhrases, { and, ne, eq }) =>
            and(
              ne(pendingPhrases.reviewStatus, "unreviewed"),
              eq(pendingPhrases.userId, auth.id),
            ),
          with: {
            user: true,
            original: true,
            reviews: {
              with: {
                user: true,
              },
            },
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const glossaryAdmins = glossary.users
      .filter((user) => ["owner", "admin", "editor"].includes(user.role))
      .map((user) => user.user.username);

    if (!glossaryAdmins.includes(auth.username)) {
      throw new Error(
        "You don't have permissions to review phrases in this glossary.",
      );
    }

    return c.json<PendingPhrase[]>(
      glossary.pendingPhrases.map((phrase) => {
        const pendingPhrase: Phrase = {
          id: phrase.id,
          phrase: phrase.phrase,
          spelling: phrase.spelling,
          description: phrase.description,
          audio: phrase.audio,
          createdAt: phrase.createdAt.toISOString(),
          updatedAt: phrase.updatedAt.toISOString(),
        };
        const originalPhrase: Phrase | null = phrase.original
          ? {
              id: phrase.original.id,
              phrase: phrase.original.phrase,
              spelling: phrase.original.spelling,
              description: phrase.original.description,
              audio: phrase.original.audio,
              createdAt: phrase.original.createdAt.toISOString(),
              updatedAt: phrase.original.updatedAt.toISOString(),
            }
          : null;

        return {
          phrase: pendingPhrase,
          user: {
            username: phrase.user.username,
            emoji: phrase.user.emoji,
          } satisfies User,
          original: originalPhrase,
          status: phrase.reviewStatus,
          reviews: phrase.reviews.map((review) => {
            return {
              phrase: phrase.phrase,
              status: review.status,
              user: {
                username: review.user.username,
                emoji: review.user.emoji,
              } satisfies User,
            };
          }),
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to get glossary users.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.delete("/private/api/glossary/:id/reviewed_phrases", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    await dbHelper
      .getDb()
      .delete(pendingPhraseTable)
      .where(
        and(
          eq(pendingPhraseTable.glossaryId, glossary.id),
          eq(pendingPhraseTable.userId, auth.id),
          ne(pendingPhraseTable.reviewStatus, "unreviewed"),
        ),
      );

    return c.json(true);
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to delete reviewed phrases.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.get("/private/api/glossary/:id/users", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
      },
    });

    if (!glossary) {
      // If glossary is not in database, return authenticated user as owner
      return c.json<GlossaryUser[]>([
        {
          user: {
            username: auth.username,
            emoji: auth.emoji,
          },
          role: "owner",
        },
      ]);
    }

    return c.json<GlossaryUser[]>(
      glossary.users.map((user) => {
        return {
          user: {
            username: user.user.username,
            emoji: user.user.emoji,
          },
          role: user.role,
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to get glossary users.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.get("/private/api/glossary/:id/join", async (c) => {
  const dbHelper = c.get("db");
  const id = c.req.param("id");
  const auth = c.get("jwtPayload");

  try {
    const glossary = await dbHelper.getDb().query.glossaryTable.findFirst({
      where: eq(glossaryTable.id, id),
      with: {
        users: {
          with: {
            user: true,
          },
        },
      },
    });

    if (!glossary) {
      throw new Error("Invalid glossary.");
    }

    const users = glossary.users.map((user) => user.user.username);

    if (!users.includes(auth.username)) {
      await dbHelper
        .getDb()
        .insert(glossaryUsers)
        .values({
          glossaryId: glossary.id,
          userId: auth.id,
          role: "viewer",
        })
        .onConflictDoUpdate({
          target: [glossaryUsers.glossaryId, glossaryUsers.userId],
          set: {
            role: "viewer",
          },
        });
    }

    const updatedGlossary = await dbHelper
      .getDb()
      .query.glossaryTable.findFirst({
        where: eq(glossaryTable.id, id),
        with: {
          users: {
            with: {
              user: true,
            },
          },
        },
      });

    if (!updatedGlossary) {
      throw new Error("Invalid glossary.");
    }

    return c.json<GlossaryUser[]>(
      updatedGlossary.users.map((user) => {
        return {
          user: {
            username: user.user.username,
            emoji: user.user.emoji,
          },
          role: user.role,
        };
      }),
    );
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to get glossary users.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

app.post("/public/api/glossary/check_updates", async (c) => {
  const dbHelper = c.get("db");
  const glossaries = await c.req.json<GlossaryUpdate[]>();

  if (glossaries.length === 0) return c.json([], 200);

  const conditions = glossaries.map((item) =>
    and(eq(glossaryTable.id, item.id), gt(glossaryTable.version, item.version)),
  );
  const combinedQuery = or(...conditions);

  try {
    const dbResult = await dbHelper
      .getDb()
      .select({
        id: glossaryTable.id,
        version: glossaryTable.version,
        createdAt: glossaryTable.createdAt,
        updatedAt: glossaryTable.updatedAt,
      })
      .from(glossaryTable)
      .where(combinedQuery);

    const updates: GlossaryUpdate[] = dbResult.map((item) => ({
      ...item,
      createdAt: item.createdAt.getTime(),
      updatedAt: item.updatedAt.getTime(),
    }));

    return c.json(updates);
  } catch (error: any) {
    return c.json<ErrorDetails>(
      {
        error: "Failed to check for updates.",
        details: error.message || "Unknown error.",
      },
      400,
    );
  }
});

export default {
  fetch: app.fetch,
  async scheduled(
    controller: ScheduledController,
    env: CloudflareBindings,
    ctx: ExecutionContext,
  ) {
    const dbHelper = new DbHelper(env);
    try {
      // This is just to keep supabase from pausing the database automatically due to inactivity.
      const glossaryCount = await dbHelper
        .getDb()
        .select({ count: sql<number>`count(*)` })
        .from(glossaryTable);
      console.log(`Glossary count: ${glossaryCount[0].count}`);

      await dbHelper
        .getDb()
        .update(usersTable)
        .set({ updatedAt: new Date() })
        .where(eq(usersTable.username, "mXaln"));

      // Delete outdated reviewed pending phrases
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

      await dbHelper
        .getDb()
        .delete(pendingPhraseTable)
        .where(
          and(
            ne(pendingPhraseTable.reviewStatus, "unreviewed"),
            lt(pendingPhraseTable.updatedAt, thirtyDaysAgo),
          ),
        );
    } catch (error) {
      console.error(error);
    } finally {
      ctx.waitUntil(dbHelper.close());
    }
  },
};
