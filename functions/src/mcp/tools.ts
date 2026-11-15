import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { buildSnapshot } from "../coach/snapshot";
import { runTool } from "../coach/tools";
import { loadCoachHistory, loadDayResults, loadHabits, loadProfile, loadProfileRaw, loadProgress, loadWeighIns, loadWeightGoal } from "../lib/data";
import { formatCoachHistory, formatDayResults, formatHabits, formatProfile, formatProgress, formatWeight, profileDetails } from "../lib/format";
import { addDays, isValidTimeZone, localParts } from "../lib/time";
import { COACH_STYLES, GOALS, MAX_MEAL_ITEMS, MEAL_TYPES, UNITS, WriteError, createWriter, firestoreStore, type WriteStore } from "./writes";

/** Data access the MCP tools need, injectable for tests. */
export interface McpDeps {
  buildSnapshot: typeof buildSnapshot;
  runTool: typeof runTool;
  loadProfile: typeof loadProfile;
  loadHabits: typeof loadHabits;
  loadProfileRaw: typeof loadProfileRaw;
  loadWeighIns: typeof loadWeighIns;
  loadWeightGoal: typeof loadWeightGoal;
  loadDayResults: typeof loadDayResults;
  loadCoachHistory: typeof loadCoachHistory;
  loadProgress: typeof loadProgress;
}

export const defaultDeps: McpDeps = {
  buildSnapshot, runTool, loadProfile, loadHabits, loadProfileRaw, loadWeighIns, loadWeightGoal, loadDayResults, loadCoachHistory, loadProgress,
};

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).describe("yyyy-MM-dd");
const text = (t: string, isError = false) => ({ content: [{ type: "text" as const, text: t }], isError });

/**
 * MCP tools over one signed-in user's Resurge data. Everything the app stores is readable (reads reuse
 * the coach's loaders and tools in coach/tools.ts); writes (./writes.ts) log meals, water and habit
 * check-ins, and change targets, profile and the weight goal.
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
    description: "The user's whole profile: name, goals, daily targets (calories, water, sleep, steps), units, coaching style, timezone, habit and meal reminder settings and day-streak goals.",
    inputSchema: {},
  }, async () => {
    const raw = await deps.loadProfileRaw(uid);
    return text(`${formatProfile(raw)}\n\nAll profile fields:\n${JSON.stringify(profileDetails(raw), null, 2)}`);
  });

  server.registerTool("get_daily_summaries", {
    description: "Daily totals from Health Connect for an inclusive date range, at most 120 days: steps, distance, total/active calories, sleep (in bed, asleep, stages), exercise minutes with each session's type, start and length, heart rate (resting/avg/min/max) and weight. Days are the phone's local dates; a night counts on the day it ended.",
    inputSchema: { from: isoDate, to: isoDate },
  }, (a) => coachTool("get_daily_summaries", a));

  server.registerTool("get_activities", {
    description: "Workouts and/or nights of sleep over the last N days (1-90), newest first.",
    inputSchema: { type: z.enum(["workout", "sleep", "any"]), days: z.number().int() },
  }, (a) => coachTool("get_activities", a));

  server.registerTool("get_insights", {
    description: "Insights the app has shown over the last N days (1-60): titles, plus each insight's text with full=true.",
    inputSchema: { days: z.number().int(), full: z.boolean().optional() },
  }, (a) => coachTool("get_insights", a));

  server.registerTool("get_meals", {
    description: "Meals and water over the last N days (1-60): per-meal calories, protein, carbs, fat, fibre and items, daily totals, micronutrients (sugar, sodium, iron...), water drunk, and a PREDICTED glycaemic index/load per meal and day (an estimate, not a measurement).",
    inputSchema: { days: z.number().int() },
  }, (a) => coachTool("get_meals", a));

  server.registerTool("get_weight", {
    description: "Weigh-ins over the last N days (1-365), the smoothed trend weight the app shows with its 7-day change, and the weight goal (target, pace, progress). Kilograms.",
    inputSchema: { days: z.number().int() },
  }, async ({ days }) => {
    const n = Math.min(Math.max(days, 1), 365);
    const [end, rows, goal] = await Promise.all([today(), deps.loadWeighIns(uid), deps.loadWeightGoal(uid)]);
    return text(formatWeight(rows, goal, addDays(end, -(n - 1)), end));
  });

  server.registerTool("get_day_streak", {
    description: "Day-streak results over the last N days (1-120): whether each settled day met the user's day goals, and how many goals were done.",
    inputSchema: { days: z.number().int() },
  }, async ({ days }) => {
    const n = Math.min(Math.max(days, 1), 120);
    const end = await today();
    const [rows, profile] = await Promise.all([deps.loadDayResults(uid, addDays(end, -(n - 1))), deps.loadProfileRaw(uid)]);
    return text(formatDayResults(rows, profile.dayGoals));
  });

  server.registerTool("get_coach_history", {
    description: "The user's latest chats with the in-app coach (1-10 most recent threads), user and coach messages only.",
    inputSchema: { threads: z.number().int().optional() },
  }, async ({ threads }) => text(formatCoachHistory(await deps.loadCoachHistory(uid, Math.min(Math.max(threads ?? 3, 1), 10)))));

  server.registerTool("get_progress", {
    description: "Progress photo and reel metadata (dates, reels made, reel settings). The photos are end-to-end encrypted and can't be read.",
    inputSchema: {},
  }, async () => text(formatProgress(await deps.loadProgress(uid))));

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
        gi: z.number().min(0).max(110).optional().describe("glycaemic index of this food if you know it (glucose = 100); otherwise it's predicted from macros"),
      })).max(MAX_MEAL_ITEMS).optional(),
      kcal: n(10000).optional(), protein: n(1000).optional(), carbs: n(1000).optional(), fat: n(1000).optional(), fiber: n(500).optional(),
    },
  }, (a) => write(() => writer.logMeal(a)));

  server.registerTool("log_water", {
    description: "Add water drunk (ml, negative to correct) to a day; default today, up to 7 days back.",
    inputSchema: { ml: z.number().int().min(-5000).max(5000).refine((v) => v !== 0, "ml can't be 0"), date: isoDate.optional() },
  }, (a) => write(() => writer.logWater(a)));

  server.registerTool("get_nutrition_targets", {
    description: "Current daily targets: calories, water, sleep and steps, plus the protein/carbs/fat and fibre grams the app derives from calories.",
    inputSchema: {},
  }, () => write(() => writer.nutritionTargets()));

  server.registerTool("set_nutrition_targets", {
    description: "Change daily targets: calories (800-6000 kcal), water (500-8000 ml), sleep (240-660 min) and/or steps (2000-30000). Only the fields given change. Macro and fibre targets follow calories automatically.",
    inputSchema: {
      calories: z.number().int().min(800).max(6000).optional(),
      waterMl: z.number().int().min(500).max(8000).optional(),
      sleepMinutes: z.number().int().min(240).max(660).optional(),
      steps: z.number().int().min(2000).max(30000).optional(),
    },
  }, (a) => write(() => writer.setNutritionTargets(a)));

  server.registerTool("set_profile", {
    description: "Change the user's display name, goals (replaces the list), units and/or coaching style. Only the fields given change.",
    inputSchema: {
      displayName: z.string().trim().min(1).max(60).optional(),
      goals: z.array(z.enum(GOALS)).max(GOALS.length).optional(),
      units: z.enum(UNITS).optional(),
      coachStyle: z.enum(COACH_STYLES).optional(),
    },
  }, (a) => write(() => writer.setProfile(a)));

  server.registerTool("set_weight_goal", {
    description: "Set the weight goal: mode 'change' with goalKg (and optional paceKgPerWeek, 0.1-1.0), 'maintain' (holds goalKg, or the current goal or trend weight), or 'clear'. Needs at least one weigh-in.",
    inputSchema: {
      mode: z.enum(["change", "maintain", "clear"]),
      goalKg: z.number().min(20).max(400).optional(),
      paceKgPerWeek: z.number().min(0.1).max(1).optional(),
    },
  }, (a) => write(() => writer.setWeightGoal(a)));

  server.registerTool("set_habit_status", {
    description: "Check in a habit as done or missed (or clear it) for the current habit day or the day before. habit is the habit's name (as in get_habits) or id.",
    inputSchema: { habit: z.string().trim().min(1).max(60), status: z.enum(["done", "missed", "clear"]), date: isoDate.optional() },
  }, (a) => write(() => writer.setHabitStatus(a)));

  return server;
}
