import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { buildSnapshot } from "../coach/snapshot";
import { runTool } from "../coach/tools";
import { loadHabits, loadProfile } from "../lib/data";
import { formatHabits, formatProfile } from "../lib/format";
import { addDays, isValidTimeZone, localParts } from "../lib/time";
import { ACTIVITY_IDS, MAX_MEAL_ITEMS, MEAL_TYPES, WriteError, createWriter, firestoreStore, type WriteStore } from "./writes";

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
 * MCP tools over one signed-in user's Resurge data: reads reuse the coach's loaders and tools
 * (coach/tools.ts); writes (./writes.ts) log meals, workouts, weight, water and habit check-ins.
 */
export function createMcpServer(uid: string, deps: McpDeps = defaultDeps, store: WriteStore = firestoreStore): McpServer {
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

  // ---- Logging writes ----
  const writer = createWriter(uid, store);
  const write = async (fn: () => Promise<unknown>) => {
    try {
      return text(JSON.stringify(await fn()));
    } catch (e) {
      if (e instanceof WriteError) return text(e.message, true);
      throw e;
    }
  };
  const iso = z.string().max(40).describe("ISO 8601 time with offset, e.g. 2026-11-05T13:30:00+05:30; default now");
  const n = (max: number) => z.number().min(0).max(max);

  server.registerTool("log_meal", {
    description: "Log a meal by hand (no AI analysis), like typing it into the app. Give items (nutrition per 100 g plus grams) or just totals (kcal and optional macros in grams).",
    inputSchema: {
      type: z.enum(MEAL_TYPES),
      title: z.string().trim().max(80).optional(),
      at: iso.optional(),
      items: z.array(z.object({
        name: z.string().trim().min(1).max(60),
        grams: z.number().positive().max(5000),
        kcal: n(900).describe("per 100 g"), protein: n(100).describe("per 100 g"), carbs: n(100).describe("per 100 g"),
        fat: n(100).describe("per 100 g"), fiber: n(100).optional().describe("per 100 g"),
      })).max(MAX_MEAL_ITEMS).optional(),
      kcal: n(10000).optional(), protein: n(1000).optional(), carbs: n(1000).optional(), fat: n(1000).optional(), fiber: n(500).optional(),
    },
  }, (a) => write(() => writer.logMeal(a)));

  server.registerTool("log_workout", {
    description: "Log a manual workout. activityId comes from the app's activity catalog.",
    inputSchema: {
      activityId: z.enum(ACTIVITY_IDS),
      durationMin: z.number().int().min(1).max(1440),
      start: iso.optional(),
      title: z.string().trim().max(80).optional(),
      notes: z.string().trim().max(500).optional(),
      metrics: z.object({
        distanceM: n(1_000_000).optional(), elevationM: n(20_000).optional(), avgHr: z.number().int().min(30).max(250).optional(),
        kcal: z.number().int().min(0).max(10_000).optional(), laps: z.number().int().min(0).max(1000).optional(),
        poolLengthM: z.number().int().min(10).max(100).optional(), reps: z.number().int().min(0).max(10_000).optional(),
        rpe: z.number().int().min(1).max(10).optional(),
        exercises: z.array(z.object({
          name: z.string().trim().min(1).max(60),
          sets: z.array(z.object({ reps: z.number().int().min(0).max(1000), weightKg: n(1000).optional() })).max(50),
        })).max(30).optional(),
      }).optional(),
    },
  }, (a) => write(() => writer.logWorkout(a)));

  server.registerTool("log_weight", {
    description: "Log a weigh-in in kg (20-400).",
    inputSchema: { kg: z.number().min(20).max(400), at: iso.optional(), note: z.string().trim().max(120).optional() },
  }, (a) => write(() => writer.logWeight(a)));

  server.registerTool("log_water", {
    description: "Add water drunk (ml, negative to correct) to a day; default today, up to 7 days back.",
    inputSchema: { ml: z.number().int().min(-5000).max(5000).refine((v) => v !== 0, "ml can't be 0"), date: isoDate.optional() },
  }, (a) => write(() => writer.logWater(a)));

  server.registerTool("set_habit_status", {
    description: "Check in a habit as done or missed (or clear it) for the current habit day or the day before. habit is the habit's name (as in get_habits) or id.",
    inputSchema: { habit: z.string().trim().min(1).max(60), status: z.enum(["done", "missed", "clear"]), date: isoDate.optional() },
  }, (a) => write(() => writer.setHabitStatus(a)));

  return server;
}
