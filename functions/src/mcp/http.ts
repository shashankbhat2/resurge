import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions";
import { onRequest } from "firebase-functions/v2/https";
import "../lib/firebase";
import { createKeyManager, looksLikeApiKey } from "./apiKeys";
import { createMcpServer } from "./tools";

/** The bearer token from `Authorization: Bearer <token>` (an API key or a Firebase ID token), or null. */
export function bearerToken(header: string | undefined): string | null {
  const m = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return m ? m[1].trim() : null;
}

export interface AuthDeps {
  resolveApiKey(key: string): Promise<string | null>;
  verifyIdToken(token: string): Promise<string>;
}

const defaultAuth: AuthDeps = {
  resolveApiKey: (key) => createKeyManager().resolve(key),
  verifyIdToken: async (token) => (await getAuth().verifyIdToken(token)).uid,
};

/**
 * The uid behind an Authorization header, or null. `Bearer rsg_...` is a personal API key (looked
 * up by hash; unknown or revoked keys fail); any other bearer token must be a Firebase ID token.
 */
export async function resolveUid(header: string | undefined, auth: AuthDeps = defaultAuth): Promise<string | null> {
  const token = bearerToken(header);
  if (!token) return null;
  try {
    return looksLikeApiKey(token) ? await auth.resolveApiKey(token) : await auth.verifyIdToken(token);
  } catch {
    return null;
  }
}

/**
 * MCP over Streamable HTTP (stateless, JSON responses). Send a personal API key made in the app
 * (Settings → MCP API keys) or the user's Firebase ID token as a bearer token. Every tool reads or
 * writes only that user's data.
 */
export const mcp = onRequest({ timeoutSeconds: 60, memory: "256MiB" }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).set("Allow", "POST").json({ jsonrpc: "2.0", error: { code: -32000, message: "Method not allowed" }, id: null });
    return;
  }
  const uid = await resolveUid(req.get("authorization"));
  if (!uid) {
    res.status(401).set("WWW-Authenticate", "Bearer").json({ jsonrpc: "2.0", error: { code: -32001, message: "Unauthorized" }, id: null });
    return;
  }
  const server = createMcpServer(uid);
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined, enableJsonResponse: true });
  res.on("close", () => {
    void transport.close();
    void server.close();
  });
  try {
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (e) {
    logger.error("mcp failed", { uid, error: (e as Error).message });
    if (!res.headersSent) res.status(500).json({ jsonrpc: "2.0", error: { code: -32603, message: "Internal error" }, id: null });
  }
});
