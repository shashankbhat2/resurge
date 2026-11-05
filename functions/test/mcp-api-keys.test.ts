import { describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/firebase", () => ({ db: {}, userDoc: () => ({}), DATABASE: "resurge", messaging: () => ({}) }));

import { createHash } from "node:crypto";
import {
  KEY_PREFIX, LAST_USED_RESOLUTION_MS, MAX_KEYS_PER_USER, createKeyManager, generateApiKey, hashApiKey, keyHandlers,
  looksLikeApiKey, type KeyStore, type StoredApiKey,
} from "../src/mcp/apiKeys";
import { resolveUid, type AuthDeps } from "../src/mcp/http";

function memKeys() {
  const docs: Record<string, StoredApiKey> = {};
  const touched: string[] = [];
  const store: KeyStore = {
    get: async (id) => docs[id] ?? null,
    createIfUnderLimit: async (id, data, max) => {
      if (Object.values(docs).filter((d) => d.uid === data.uid && !d.revoked).length >= max) return false;
      docs[id] = data;
      return true;
    },
    listActive: async (uid) => Object.entries(docs).filter(([, d]) => d.uid === uid && !d.revoked).map(([id, d]) => ({ id, ...d })),
    revoke: async (uid, id, atMs) => {
      const d = docs[id];
      if (!d || d.uid !== uid) return false;
      d.revoked = true;
      d.revokedAtMs = atMs;
      return true;
    },
    touch: async (id, atMs) => { touched.push(id); docs[id].lastUsedAtMs = atMs; },
  };
  return { store, docs, touched };
}

describe("mcp api keys: generation", () => {
  it("makes prefixed, unique, high-entropy keys", () => {
    const a = generateApiKey();
    const b = generateApiKey();
    expect(a).toMatch(/^rsg_[A-Za-z0-9_-]{43}$/); // 32 bytes base64url
    expect(a).not.toBe(b);
    expect(looksLikeApiKey(a)).toBe(true);
    expect(looksLikeApiKey("eyJhbGciOi.x.y")).toBe(false);
  });

  it("hashes with SHA-256 hex", () => {
    const k = "rsg_test";
    expect(hashApiKey(k)).toBe(createHash("sha256").update(k).digest("hex"));
    expect(hashApiKey(k)).toMatch(/^[0-9a-f]{64}$/);
  });
});

describe("mcp api keys: create / list / revoke", () => {
  it("stores only the hash and metadata, and returns the key once", async () => {
    const { store, docs } = memKeys();
    const m = createKeyManager(store, () => 1000);
    const { key, info } = await m.create("u1", "Claude");
    expect(Object.keys(docs)).toEqual([hashApiKey(key)]);
    const stored = docs[hashApiKey(key)];
    expect(stored).toEqual({ uid: "u1", name: "Claude", last4: key.slice(-4), createdAtMs: 1000, lastUsedAtMs: null, revoked: false });
    expect(JSON.stringify(stored)).not.toContain(key.slice(KEY_PREFIX.length));
    expect(info).toEqual({ id: hashApiKey(key), name: "Claude", prefix: "rsg_", last4: key.slice(-4), createdAtMs: 1000, lastUsedAtMs: null });
  });

  it("lists only the user's active keys, newest first, without secrets", async () => {
    const { store } = memKeys();
    let t = 0;
    const m = createKeyManager(store, () => ++t);
    const a = await m.create("u1", "A");
    const b = await m.create("u1", "B");
    await m.create("u2", "Other");
    const c = await m.create("u1", "C");
    await m.revoke("u1", c.info.id);
    const list = await m.list("u1");
    expect(list.map((k) => k.name)).toEqual(["B", "A"]);
    expect(JSON.stringify(list)).not.toContain(a.key);
    expect(JSON.stringify(list)).not.toContain(b.key);
  });

  it("caps active keys per user; revoking frees a slot", async () => {
    const m = createKeyManager(memKeys().store);
    const made = [];
    for (let i = 0; i < MAX_KEYS_PER_USER; i++) made.push(await m.create("u1", `k${i}`));
    await expect(m.create("u1", "one more")).rejects.toThrow(/up to 10/);
    await m.create("u2", "other user is fine");
    await m.revoke("u1", made[0].info.id);
    await expect(m.create("u1", "now ok")).resolves.toBeTruthy();
  });

  it("won't revoke another user's key", async () => {
    const m = createKeyManager(memKeys().store);
    const { key, info } = await m.create("u1", "A");
    expect(await m.revoke("u2", info.id)).toBe(false);
    expect(await m.resolve(key)).toBe("u1");
  });

  it("callables need auth and validate input", async () => {
    const h = keyHandlers(createKeyManager(memKeys().store));
    await expect(h.create(undefined, { name: "x" })).rejects.toMatchObject({ code: "unauthenticated" });
    await expect(h.list(undefined)).rejects.toMatchObject({ code: "unauthenticated" });
    await expect(h.create("u1", { name: "  " })).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(h.create("u1", { name: "x".repeat(41) })).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(h.revoke("u1", { id: "nope" })).rejects.toMatchObject({ code: "invalid-argument" });
    await expect(h.revoke("u1", { id: "a".repeat(64) })).rejects.toMatchObject({ code: "not-found" });

    const made = await h.create("u1", { name: " Cursor " });
    expect(made.info.name).toBe("Cursor");
    expect((await h.list("u1")).keys).toHaveLength(1);
    expect(await h.revoke("u1", { id: made.info.id })).toEqual({ revoked: true });
    expect((await h.list("u1")).keys).toHaveLength(0);
  });

  it("callable maps the key limit to resource-exhausted", async () => {
    const h = keyHandlers(createKeyManager(memKeys().store));
    for (let i = 0; i < MAX_KEYS_PER_USER; i++) await h.create("u1", { name: `k${i}` });
    await expect(h.create("u1", { name: "x" })).rejects.toMatchObject({ code: "resource-exhausted" });
  });
});

describe("mcp api keys: resolve", () => {
  it("resolves a valid key and refreshes lastUsedAtMs at most once a minute", async () => {
    const { store, docs, touched } = memKeys();
    let t = 10_000;
    const m = createKeyManager(store, () => t);
    const { key, info } = await m.create("u1", "A");
    expect(await m.resolve(key)).toBe("u1");
    expect(docs[info.id].lastUsedAtMs).toBe(10_000);
    t += 1000;
    expect(await m.resolve(key)).toBe("u1");
    expect(touched).toHaveLength(1);
    t += LAST_USED_RESOLUTION_MS;
    await m.resolve(key);
    expect(touched).toHaveLength(2);
  });

  it("rejects revoked, unknown and malformed keys", async () => {
    const { store } = memKeys();
    const m = createKeyManager(store);
    const { key, info } = await m.create("u1", "A");
    await m.revoke("u1", info.id);
    expect(await m.resolve(key)).toBeNull();
    expect(await m.resolve(generateApiKey())).toBeNull();
    expect(await m.resolve("rsg_short")).toBeNull();
  });

  it("still resolves when the lastUsed write fails", async () => {
    const { store } = memKeys();
    const m = createKeyManager({ ...store, touch: async () => { throw new Error("down"); } });
    const { key } = await m.create("u1", "A");
    expect(await m.resolve(key)).toBe("u1");
  });
});

describe("mcp auth", () => {
  function auth(): AuthDeps & { resolveApiKey: ReturnType<typeof vi.fn>; verifyIdToken: ReturnType<typeof vi.fn> } {
    return {
      resolveApiKey: vi.fn(async (k: string) => (k === "rsg_good" ? "key-user" : null)),
      verifyIdToken: vi.fn(async (t: string) => { if (t === "id.token.ok") return "token-user"; throw new Error("bad token"); }),
    };
  }

  it("routes rsg_ keys to the key lookup", async () => {
    const a = auth();
    expect(await resolveUid("Bearer rsg_good", a)).toBe("key-user");
    expect(await resolveUid("Bearer rsg_revoked", a)).toBeNull();
    expect(a.verifyIdToken).not.toHaveBeenCalled();
  });

  it("keeps the Firebase ID token path", async () => {
    const a = auth();
    expect(await resolveUid("Bearer id.token.ok", a)).toBe("token-user");
    expect(await resolveUid("Bearer id.token.bad", a)).toBeNull();
    expect(a.resolveApiKey).not.toHaveBeenCalled();
  });

  it("rejects a missing header and lookup failures", async () => {
    const a = auth();
    expect(await resolveUid(undefined, a)).toBeNull();
    expect(await resolveUid("Basic abc", a)).toBeNull();
    a.resolveApiKey.mockRejectedValueOnce(new Error("firestore down"));
    expect(await resolveUid("Bearer rsg_good", a)).toBeNull();
  });

  it("works end to end with the key manager", async () => {
    const m = createKeyManager(memKeys().store);
    const { key, info } = await m.create("u9", "A");
    const deps: AuthDeps = { resolveApiKey: (k) => m.resolve(k), verifyIdToken: async () => { throw new Error("no"); } };
    expect(await resolveUid(`Bearer ${key}`, deps)).toBe("u9");
    await m.revoke("u9", info.id);
    expect(await resolveUid(`Bearer ${key}`, deps)).toBeNull();
  });
});
