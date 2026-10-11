import { z } from "zod";

/*
 * Structured-output schemas. Each has a Zod schema (runtime validation) and the matching JSON
 * schema sent as output_config.format (all properties required, no extras, as structured outputs needs).
 */

export const CATEGORIES = ["SLEEP", "ACTIVITY", "HEART", "RECOVERY", "NUTRITION"] as const;

export const ActivityInsight = z.object({
  category: z.enum(CATEGORIES),
  title: z.string().min(1).max(80),
  body: z.string().min(1).max(800),
  highlights: z.array(z.object({ label: z.string().max(30), value: z.string().max(30) })).max(3),
  suggestion: z.string().max(240),
});
export type ActivityInsight = z.infer<typeof ActivityInsight>;

export const ACTIVITY_INSIGHT_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["category", "title", "body", "highlights", "suggestion"],
  properties: {
    category: { type: "string", enum: [...CATEGORIES] },
    title: { type: "string" },
    body: { type: "string" },
    highlights: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["label", "value"],
        properties: { label: { type: "string" }, value: { type: "string" } },
      },
    },
    suggestion: { type: "string" },
  },
} as const;

const Trend = z.enum(["up", "down", "steady"]);

export const WeeklyInsight = z.object({
  headline: z.string().min(1).max(160),
  summary: z.string().min(1).max(1200),
  wins: z.array(z.string().max(240)).max(3),
  areas: z.array(z.object({ area: z.enum(["sleep", "activity", "heart"]), trend: Trend, note: z.string().max(240) })).max(3),
  focus: z.array(z.string().max(240)).max(2),
  habits: z.string().max(400),
});
export type WeeklyInsight = z.infer<typeof WeeklyInsight>;

export const WEEKLY_INSIGHT_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["headline", "summary", "wins", "areas", "focus", "habits"],
  properties: {
    headline: { type: "string" },
    summary: { type: "string" },
    wins: { type: "array", items: { type: "string" } },
    areas: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["area", "trend", "note"],
        properties: {
          area: { type: "string", enum: ["sleep", "activity", "heart"] },
          trend: { type: "string", enum: ["up", "down", "steady"] },
          note: { type: "string" },
        },
      },
    },
    focus: { type: "array", items: { type: "string" } },
    habits: { type: "string" },
  },
} as const;
