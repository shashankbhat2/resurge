import { describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/firebase", () => ({ db: {}, userDoc: () => ({}), DATABASE: "resurge", messaging: () => ({}) }));

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { bearerToken } from "../src/mcp/http";
import { createMcpServer, type McpDeps } from "../src/mcp/tools";

async function connect(deps: McpDeps) {
  const [a, b] = InMemoryTransport.createLinkedPair();
  const server = createMcpServer("u1", deps);
  const client = new Client({ name: "test", version: "0" });
  await Promise.all([server.connect(a), client.connect(b)]);
  return client;
}

function fakeDeps(): McpDeps {
  return {
    buildSnapshot: vi.fn(async (_uid: string, today: string) => `<health_snapshot today="${today}">`),
    runTool: vi.fn(async (_uid: string, name: string) => ({ content: `ran ${name}`, isError: false })),
    loadProfile: vi.fn(async () => ({ timezone: "UTC" })),
    loadHabits: vi.fn(async () => [{ name: "Walk", private: false, currentStreak: 2, bestStreak: 5, log: {} }]),
  } as unknown as McpDeps;
}

describe("mcp", () => {
  it("lists the read-only tools", async () => {
    const client = await connect(fakeDeps());
    const names = (await client.listTools()).tools.map((t) => t.name).sort();
    expect(names).toEqual([
      "get_activities", "get_daily_summaries", "get_habits", "get_health_snapshot",
      "get_insights", "get_meals", "get_profile", "get_workout_logs",
      "log_meal", "log_water", "set_habit_status",
    ].sort());
  });

  it("routes coach tools for the authenticated user", async () => {
    const deps = fakeDeps();
    const client = await connect(deps);
    const r = await client.callTool({ name: "get_meals", arguments: { days: 7 } });
    expect(deps.runTool).toHaveBeenCalledWith("u1", "get_meals", { days: 7 });
    expect((r.content as { text: string }[])[0].text).toBe("ran get_meals");
  });

  it("rejects bad dates before touching data", async () => {
    const deps = fakeDeps();
    const client = await connect(deps);
    const r = await client.callTool({ name: "get_daily_summaries", arguments: { from: "yesterday", to: "2026-11-05" } });
    expect(r.isError).toBe(true);
    expect(deps.runTool).not.toHaveBeenCalled();
  });

  it("builds the snapshot for the user's local today", async () => {
    const deps = fakeDeps();
    const client = await connect(deps);
    const r = await client.callTool({ name: "get_health_snapshot", arguments: {} });
    expect((r.content as { text: string }[])[0].text).toMatch(/^<health_snapshot today="\d{4}-\d{2}-\d{2}">/);
  });

  it("formats habits", async () => {
    const client = await connect(fakeDeps());
    const r = await client.callTool({ name: "get_habits", arguments: { days: 3 } });
    expect((r.content as { text: string }[])[0].text).toContain("Walk");
  });

  it("parses bearer tokens", () => {
    expect(bearerToken("Bearer abc.def")).toBe("abc.def");
    expect(bearerToken("Basic x")).toBeNull();
    expect(bearerToken(undefined)).toBeNull();
  });
});
