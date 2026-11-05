import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { getAuth } from "firebase-admin/auth";
import { logger } from "firebase-functions";
import { onRequest } from "firebase-functions/v2/https";
import "../lib/firebase";
import { createMcpServer } from "./tools";

/** Firebase ID token from `Authorization: Bearer <token>`, or null. */
export function bearerToken(header: string | undefined): string | null {
  const m = /^Bearer\s+(.+)$/i.exec(header ?? "");
  return m ? m[1].trim() : null;
}

/**
 * MCP over Streamable HTTP (stateless, JSON responses). It uses the same Firebase Auth as the
 * callables: send the user's Firebase ID token as a bearer token. Every tool reads or writes only
 * that user's data.
 */
export const mcp = onRequest({ timeoutSeconds: 60, memory: "256MiB" }, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).set("Allow", "POST").json({ jsonrpc: "2.0", error: { code: -32000, message: "Method not allowed" }, id: null });
    return;
  }
  const token = bearerToken(req.get("authorization"));
  let uid: string;
  try {
    if (!token) throw new Error("missing token");
    uid = (await getAuth().verifyIdToken(token)).uid;
  } catch {
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
