import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { buildSnapshot } from "../coach/snapshot";
import { runTool } from "../coach/tools";
import { loadHabits, loadProfile } from "../lib/data";
import { formatHabits, formatProfile } from "../lib/format";
import { addDays, isValidTimeZone, localParts } from "../lib/time";

/** Data access the MCP tools need, injectable for tests. */
export interface McpDeps {
  buildSnapshot: typeof buildSnapshot;
  runTool: typeof runTool;
  loadProfile: typeof loadProfile;
  loadHabits: typeof loadHabits;
}

export const defaultDeps: McpDeps = { buildSnapshot, runTool, loadProfile, loadHabits };

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).describe("yyyy-MM-dd");
const text = (t: string, isError = false) => ({ content: [{ type: "text" as const, text: t }], isError });

/**
 * Read-only MCP tools over one signed-in user's Resurge data. They reuse the coach's loaders and
 * tools (coach/tools.ts), so agents see exactly what the in-app coach sees.
 */
export function createMcpServer(uid: string, deps: McpDeps = defaultDeps): McpServer {
  const server = new McpServer({ name: "resurge", version: "0.1.0" });
  const today = async () => {
    const tz = (await deps.loadProfile(uid)).timezone;
    return localParts(isValidTimeZone(tz) ? (tz as string) : "UTC").date;
  };
  const coachTool = async (name: string, input: unknown) => {
    const r = await deps.runTool(uid, name, input);
    return text(r.content, r.isError);
  };

  server.registerTool("get_health_snapshot", {
    description: "Profile, last 30 days of daily totals, last 7 days of activities, recent insights and 14 days of habits.",
    inputSchema: {},
  }, async () => text(await deps.buildSnapshot(uid, await today())));

  server.registerTool("get_profile", {
    description: "The user's profile: goals, body stats and preferences.",
    inputSchema: {},
  }, async () => text(formatProfile(await deps.loadProfile(uid))));

  server.registerTool("get_daily_summaries", {
    description: "Daily totals (steps, distance, calories, sleep and stages, exercise, heart rate, weight) for an inclusive date range, at most 120 days.",
    inputSchema: { from: isoDate, to: isoDate },
  }, (a) => coachTool("get_daily_summaries", a));

  server.registerTool("get_activities", {
    description: "Workouts and/or nights of sleep over the last N days (1-90), newest first.",
    inputSchema: { type: z.enum(["workout", "sleep", "any"]), days: z.number().int() },
  }, (a) => coachTool("get_activities", a));

  server.registerTool("get_insights", {
    description: "Titles of insights the app has shown over the last N days (1-60).",
    inputSchema: { days: z.number().int() },
  }, (a) => coachTool("get_insights", a));

  server.registerTool("get_meals", {
    description: "Logged meals over the last N days (1-60) with calories, macros, items and daily totals.",
    inputSchema: { days: z.number().int() },
  }, (a) => coachTool("get_meals", a));

  server.registerTool("get_workout_logs", {
    description: "Workouts logged or edited in the app over the last N days (1-90): sets, reps, weights, RPE, notes.",
    inputSchema: { days: z.number().int() },
  }, (a) => coachTool("get_workout_logs", a));

  server.registerTool("get_habits", {
    description: "Active habits with current/best streaks and a done/missed log for the last N days (1-60). Private habits show only their cover name.",
    inputSchema: { days: z.number().int() },
  }, async ({ days }) => {
    const n = Math.min(Math.max(days, 1), 60);
    const end = await today();
    const from = addDays(end, -(n - 1));
    const dates = Array.from({ length: n }, (_, i) => addDays(from, i));
    return text(formatHabits(await deps.loadHabits(uid, from), dates));
  });

  return server;
}
