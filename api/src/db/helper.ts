import * as schema from "./schema";
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { and, eq, exists, inArray, isNull, lte, not, sql } from "drizzle-orm";

export default class DbHelper {
  private client;
  private db;

  constructor(env: CloudflareBindings) {
    // Supabase transaction pooler (port 6543) doesn't support prepared statements.
    // Queries run sequentially per request, so a single connection is enough.
    this.client = postgres(env.DATABASE_URL, { prepare: false, max: 1 });
    this.db = drizzle(this.client, { schema });
  }

  getDb() {
    return this.db;
  }

  close() {
    // end() can hang when the pooler already dropped the socket, so don't wait on it forever
    return Promise.race([
      this.client.end({ timeout: 2 }).catch(() => {}),
      new Promise<void>((resolve) => setTimeout(resolve, 3000)),
    ]);
  }
}
