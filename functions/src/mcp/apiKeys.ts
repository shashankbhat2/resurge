import { createHash, randomBytes } from "node:crypto";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { ENFORCE_APP_CHECK } from "../coach/chat";
import { db } from "../lib/firebase";

/*
 * Personal API keys for the MCP server, for agents that can't hold a short-lived Firebase ID token.
 *
 * A key is "rsg_" + 32 random bytes (base64url), shown to the person once. Only its SHA-256 (hex) is
 * stored, as the doc id of the top-level collection mcpApiKeys/{hash}, so a request's key is one doc
 * read. The collection is closed to clients in firestore.rules; the app manages keys through the
 * callables below. Revoked keys keep their doc (revoked: true) and stop working at once.
 */

export const KEY_PREFIX = "rsg_";
export const MAX_KEYS_PER_USER = 10;
export const MAX_KEY_NAME = 40;
/** lastUsedAtMs is refreshed at most this often, so a busy agent doesn't write on every call. */
export const LAST_USED_RESOLUTION_MS = 60_000;
export const COLLECTION = "mcpApiKeys";

const KEY_RE = /^rsg_[A-Za-z0-9_-]{43}$/;

/** What's stored at mcpApiKeys/{sha256(key)}. */
export interface StoredApiKey {
  uid: string;
  name: string;
  /** Last 4 characters of the key, for telling keys apart in the app. */
  last4: string;
  createdAtMs: number;
  lastUsedAtMs: number | null;
  revoked: boolean;
  revokedAtMs?: number;
}

/** Metadata the app sees; never the key or anything that recreates it. */
export interface ApiKeyInfo {
  id: string;
  name: string;
  prefix: string;
  last4: string;
  createdAtMs: number;
  lastUsedAtMs: number | null;
}

export function generateApiKey(): string {
  return KEY_PREFIX + randomBytes(32).toString("base64url");
}

export function hashApiKey(key: string): string {
  return createHash("sha256").update(key, "utf8").digest("hex");
}

/** Whether a bearer token is shaped like one of our keys (anything else is tried as a Firebase ID token). */
export function looksLikeApiKey(token: string): boolean {
  return token.startsWith(KEY_PREFIX);
}

/** Firestore operations, injectable for tests. */
export interface KeyStore {
  get(id: string): Promise<StoredApiKey | null>;
  /** Writes the doc unless the user already has [max] active keys; returns false if they do. */
  createIfUnderLimit(id: string, data: StoredApiKey, max: number): Promise<boolean>;
  listActive(uid: string): Promise<(StoredApiKey & { id: string })[]>;
  /** Marks the key revoked if it belongs to [uid]; returns false if it doesn't exist or isn't theirs. */
  revoke(uid: string, id: string, atMs: number): Promise<boolean>;
  touch(id: string, atMs: number): Promise<void>;
}

const keys = () => db.collection(COLLECTION);

export const firestoreKeyStore: KeyStore = {
  async get(id) {
    const snap = await keys().doc(id).get();
    return snap.exists ? (snap.data() as StoredApiKey) : null;
  },
  async createIfUnderLimit(id, data, max) {
    const active = keys().where("uid", "==", data.uid).where("revoked", "==", false);
    return db.runTransaction(async (tx) => {
      const existing = await tx.get(active);
      if (existing.size >= max) return false;
      tx.create(keys().doc(id), data);
      return true;
    });
  },
  async listActive(uid) {
    const snap = await keys().where("uid", "==", uid).where("revoked", "==", false).get();
    return snap.docs.map((d) => ({ id: d.id, ...(d.data() as StoredApiKey) }));
  },
  async revoke(uid, id, atMs) {
    const ref = keys().doc(id);
    return db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists || snap.get("uid") !== uid) return false;
      if (snap.get("revoked") !== true) tx.update(ref, { revoked: true, revokedAtMs: atMs });
      return true;
    });
  },
  async touch(id, atMs) {
    await keys().doc(id).update({ lastUsedAtMs: atMs });
  },
};

export class KeyLimitError extends Error {}

/** Key management for one signed-in user. */
export function createKeyManager(store: KeyStore = firestoreKeyStore, now: () => number = Date.now) {
  return {
    /** Makes a key and returns it in plain text; this is the only time it exists outside the caller. */
    async create(uid: string, name: string): Promise<{ key: string; info: ApiKeyInfo }> {
      const key = generateApiKey();
      const id = hashApiKey(key);
      const data: StoredApiKey = { uid, name, last4: key.slice(-4), createdAtMs: now(), lastUsedAtMs: null, revoked: false };
      if (!(await store.createIfUnderLimit(id, data, MAX_KEYS_PER_USER))) {
        throw new KeyLimitError(`You can have up to ${MAX_KEYS_PER_USER} keys. Revoke one first.`);
      }
      return { key, info: toInfo(id, data) };
    },

    /** Active keys, newest first. */
    async list(uid: string): Promise<ApiKeyInfo[]> {
      const all = await store.listActive(uid);
      return all.sort((a, b) => b.createdAtMs - a.createdAtMs).map((k) => toInfo(k.id, k));
    },

    async revoke(uid: string, id: string): Promise<boolean> {
      return store.revoke(uid, id, now());
    },

    /** The uid a key belongs to, or null if it's unknown or revoked. Refreshes lastUsedAtMs best-effort. */
    async resolve(key: string): Promise<string | null> {
      if (!KEY_RE.test(key)) return null;
      const id = hashApiKey(key);
      const doc = await store.get(id);
      if (!doc || doc.revoked) return null;
      const t = now();
      if (doc.lastUsedAtMs === null || t - doc.lastUsedAtMs >= LAST_USED_RESOLUTION_MS) {
        // Awaited (at most once a minute per key): work after the response may never run on Cloud Functions.
        await store.touch(id, t).catch((e) => logger.warn("mcp key lastUsed update failed", { error: (e as Error).message }));
      }
      return doc.uid;
    },
  };
}

function toInfo(id: string, k: StoredApiKey): ApiKeyInfo {
  return { id, name: k.name, prefix: KEY_PREFIX, last4: k.last4, createdAtMs: k.createdAtMs, lastUsedAtMs: k.lastUsedAtMs };
}

// --- Callables (the app's settings screen) ---

const CreateInput = z.object({ name: z.string().trim().min(1).max(MAX_KEY_NAME) });
const RevokeInput = z.object({ id: z.string().regex(/^[0-9a-f]{64}$/) });

function requireUid(uid: string | undefined): string {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to manage API keys.");
  return uid;
}

/** Handlers behind the callables, exported for tests. */
export function keyHandlers(manager = createKeyManager()) {
  return {
    async create(uid: string | undefined, data: unknown) {
      const u = requireUid(uid);
      const parsed = CreateInput.safeParse(data);
      if (!parsed.success) throw new HttpsError("invalid-argument", `Give the key a name (up to ${MAX_KEY_NAME} characters).`);
      try {
        return await manager.create(u, parsed.data.name);
      } catch (e) {
        if (e instanceof KeyLimitError) throw new HttpsError("resource-exhausted", e.message);
        throw e;
      }
    },
    async list(uid: string | undefined) {
      return { keys: await manager.list(requireUid(uid)) };
    },
    async revoke(uid: string | undefined, data: unknown) {
      const u = requireUid(uid);
      const parsed = RevokeInput.safeParse(data);
      if (!parsed.success) throw new HttpsError("invalid-argument", "Unknown key.");
      if (!(await manager.revoke(u, parsed.data.id))) throw new HttpsError("not-found", "That key doesn't exist.");
      return { revoked: true };
    },
  };
}

const handlers = keyHandlers();
const opts = { enforceAppCheck: ENFORCE_APP_CHECK, timeoutSeconds: 30, memory: "256MiB" as const };

/** Makes a personal MCP API key: { name } → { key, info }. The key is returned only here. */
export const createMcpApiKey = onCall(opts, (request) => handlers.create(request.auth?.uid, request.data));

/** The signed-in user's active keys (metadata only): → { keys: ApiKeyInfo[] }. */
export const listMcpApiKeys = onCall(opts, (request) => handlers.list(request.auth?.uid));

/** Revokes one of the user's keys: { id } → { revoked: true }. */
export const revokeMcpApiKey = onCall(opts, (request) => handlers.revoke(request.auth?.uid, request.data));
