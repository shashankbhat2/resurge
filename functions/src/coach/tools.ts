import type Anthropic from "@anthropic-ai/sdk";
import { z } from "zod";
import { formatInsightList, loadEvents, loadMeals, loadRecentInsights, loadSummaries, loadWorkoutLogs } from "../lib/data";
import { formatEvents, formatMeals, formatSummaries, formatWorkoutLogs } from "../lib/format";
import { addDays } from "../lib/time";

/*
 * Read-only tools over the user's own Firestore data. Definitions are a constant list in a fixed
 * order (part of the cached prompt prefix). Inputs are validated with Zod before running.
 */

const DAY_MS = 24 * 60 * 60 * 1000;
const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const COACH_TOOLS: Anthropic.Beta.BetaTool[] = [
  {
    name: "get_daily_summaries",
    description:
      "Daily totals (steps, distance, calories, sleep and stages, exercise, heart rate, weight) for a date range, inclusive. Use for trends beyond the snapshot. At most 120 days per call.",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["from", "to"],
      properties: {
        from: { type: "string", description: "Start date, yyyy-MM-dd" },
        to: { type: "string", description: "End date, yyyy-MM-dd" },
      },
    },
  },
  {
    name: "get_activities",
    description:
      "Individual workouts and/or nights of sleep (duration, distance, pace, heart rate, sleep stages, bedtime) over the last N days, newest first.",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["type", "days"],
      properties: {
        type: { type: "string", enum: ["workout", "sleep", "any"] },
        days: { type: "integer", description: "How many days back to look (1-90)" },
      },
    },
  },
  {
    name: "get_insights",
    description: "Titles of insights the app has already shown the user over the last N days (1-60).",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["days"],
      properties: { days: { type: "integer", description: "How many days back (1-60)" } },
    },
  },
  {
    name: "get_meals",
    description:
      "Meals the user logged (scanned or typed) over the last N days (1-60): per-meal calories, protein, carbs, fat and items, with daily totals. Use for nutrition questions.",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["days"],
      properties: { days: { type: "integer", description: "How many days back (1-60)" } },
    },
  },
  {
    name: "get_workout_logs",
    description:
      "Workouts the user logged by hand or edited in the app over the last N days (1-90), including strength sets, reps and weights, effort (RPE) and notes. Device workouts without edits come from get_activities.",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["days"],
      properties: { days: { type: "integer", description: "How many days back (1-90)" } },
    },
  },
];

const SummariesInput = z.object({ from: isoDate, to: isoDate });
const ActivitiesInput = z.object({ type: z.enum(["workout", "sleep", "any"]), days: z.number().int() });
const InsightsInput = z.object({ days: z.number().int() });

export interface ToolResult {
  content: string;
  isError: boolean;
}

export async function runTool(uid: string, name: string, input: unknown): Promise<ToolResult> {
  const error = (content: string): ToolResult => ({ content, isError: true });
  switch (name) {
    case "get_daily_summaries": {
      const p = SummariesInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      let { from, to } = p.data;
      if (from > to) [from, to] = [to, from];
      if (addDays(from, 120) < to) from = addDays(to, -120);
      return { content: formatSummaries(await loadSummaries(uid, from, to)), isError: false };
    }
    case "get_activities": {
      const p = ActivitiesInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      const days = Math.min(Math.max(p.data.days, 1), 90);
      const type = p.data.type === "any" ? undefined : p.data.type;
      return { content: formatEvents(await loadEvents(uid, Date.now() - days * DAY_MS, type, 60)), isError: false };
    }
    case "get_insights": {
      const p = InsightsInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      const days = Math.min(Math.max(p.data.days, 1), 60);
      return { content: formatInsightList(await loadRecentInsights(uid, Date.now() - days * DAY_MS, 40)), isError: false };
    }
    case "get_meals": {
      const p = InsightsInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      const days = Math.min(Math.max(p.data.days, 1), 60);
      const from = new Date(Date.now() - days * DAY_MS).toISOString().slice(0, 10);
      return { content: formatMeals(await loadMeals(uid, from)), isError: false };
    }
    case "get_workout_logs": {
      const p = InsightsInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      const days = Math.min(Math.max(p.data.days, 1), 90);
      return { content: formatWorkoutLogs(await loadWorkoutLogs(uid, Date.now() - days * DAY_MS)), isError: false };
    }
    default:
      return error(`Unknown tool: ${name}`);
  }
}
