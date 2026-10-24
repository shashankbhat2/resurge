import type Anthropic from "@anthropic-ai/sdk";
import { z } from "zod";
import { formatInsightList, loadDayResults, loadDayScores, loadEvents, loadMeals, loadRecentInsights, loadSummaries, loadWorkoutLogs } from "../lib/data";
import { formatDays, formatEvents, formatMeals, formatSummaries, formatWorkoutLogs } from "../lib/format";
import { addDays } from "../lib/time";

/*
 * Tools over the user's own Firestore data: reads, plus one that offers a plan change as a button
 * (nothing is changed until the person reviews and accepts the new plan in the app). Definitions are a constant list in a fixed
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
  {
    name: "get_days_and_scores",
    description:
      "Per day: whether it was a good (streak) day, and the Recovery, Effort and Fuel scores (0-100) with a sentence on each. Use for questions about the streak, the scores or a longer stretch than the snapshot. At most 90 days per call.",
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
    name: "suggest_plan_change",
    description:
      "Offer the person a change to their weekly plan. The app shows a button; tapping it rebuilds the plan with your instruction and lets them review the new week before using it. Nothing changes until they accept. Use it when they ask to change the plan, or once they agree to a change you proposed. Call it at most once per reply.",
    strict: true,
    input_schema: {
      type: "object",
      additionalProperties: false,
      required: ["change", "label"],
      properties: {
        change: {
          type: "string",
          description: "What to change, written as an instruction to the plan builder, e.g. \"Move Friday's strength session to Thursday and keep Saturday's run easy, under 140 bpm\". Under 300 characters.",
        },
        label: { type: "string", description: "Button text, 2 to 4 words, e.g. \"Move Friday's session\"" },
      },
    },
  },
];

/** A plan change offered as a button under the coach's reply. */
export interface PlanChangeAction {
  type: "plan_change";
  change: string;
  label: string;
}

const SummariesInput = z.object({ from: isoDate, to: isoDate });
const ActivitiesInput = z.object({ type: z.enum(["workout", "sleep", "any"]), days: z.number().int() });
const InsightsInput = z.object({ days: z.number().int() });

const PlanChangeInput = z.object({ change: z.string().trim().min(1), label: z.string().trim().min(1) });

export interface ToolResult {
  content: string;
  isError: boolean;
  /** Set when the tool offers the person something to tap. */
  action?: PlanChangeAction;
}

/** Context a tool needs beyond the user id. */
export interface ToolContext {
  hasPlan: boolean;
}

export async function runTool(uid: string, name: string, input: unknown, ctx: ToolContext = { hasPlan: false }): Promise<ToolResult> {
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
    case "get_days_and_scores": {
      const p = SummariesInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      let { from, to } = p.data;
      if (from > to) [from, to] = [to, from];
      if (addDays(from, 90) < to) from = addDays(to, -90);
      const dates: string[] = [];
      for (let d = from; d <= to; d = addDays(d, 1)) dates.push(d);
      const [results, scores] = await Promise.all([loadDayResults(uid, from, to), loadDayScores(uid, from, to)]);
      return { content: formatDays(dates, results, scores), isError: false };
    }
    case "suggest_plan_change": {
      const p = PlanChangeInput.safeParse(input);
      if (!p.success) return error(`Invalid input: ${p.error.message}`);
      if (!ctx.hasPlan) return error("They don't have a plan yet. Suggest building one from the Fitness tab instead.");
      return {
        content: "Shown as a button under your reply. They'll review the rebuilt week before it replaces the current plan, so don't say it has changed.",
        isError: false,
        action: { type: "plan_change", change: clipText(p.data.change, 300), label: clipText(p.data.label, 40) },
      };
    }
    default:
      return error(`Unknown tool: ${name}`);
  }
}

function clipText(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1).trimEnd()}…`;
}
