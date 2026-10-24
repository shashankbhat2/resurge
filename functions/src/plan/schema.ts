import { z } from "zod";

export const WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] as const;
export type Weekday = (typeof WEEKDAYS)[number];

export const GOALS = ["lose_fat", "build_muscle", "run_further", "get_in_shape"] as const;
export const LOCATIONS = ["gym", "home", "none"] as const;
export const EXPERIENCE = ["new", "some", "years"] as const;
export const STYLES = ["auto", "full_body", "push_pull_legs", "upper_lower", "body_part"] as const;
export const RUN_MODES = ["separate", "in_workout", "none"] as const;
export const SESSION_KINDS = ["strength", "run", "cardio", "mobility"] as const;

/** Activity ids the app's ActivityCatalog knows, so a planned session can be logged as that activity. */
export const PLAN_ACTIVITIES = [
  "weights", "bodyweight", "run", "walk", "cycle", "indoor_cycle", "row", "swim_pool", "elliptical", "hiit", "yoga", "stretch",
] as const;

/**
 * Cuts [s] to at most [max] characters, at the last sentence end (or else word break) that fits.
 * Free text from the model is clipped rather than rejected: a long note shouldn't cost the plan.
 */
export function clip(s: string, max: number): string {
  const t = s.trim();
  if (t.length <= max) return t;
  const cut = t.slice(0, max);
  const sentence = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("! "), cut.lastIndexOf("? "));
  if (sentence >= max * 0.5) return cut.slice(0, sentence + 1);
  const space = cut.lastIndexOf(" ");
  return `${cut.slice(0, space > 0 ? space : max - 1).trimEnd()}…`;
}

/** Model text: clipped to [max]; [min] still rejects empty required fields. */
const text = (max: number, min = 0) => z.string().min(min).transform((s) => clip(s, max));

/** What the app sends: the goal and schedule picked in onboarding (or later in Train). */
export const PlanRequest = z
  .object({
    goal: z.enum(GOALS),
    weeks: z.number().int().min(4).max(52),
    startKg: z.number().min(30).max(300).optional(),
    targetKg: z.number().min(30).max(300).optional(),
    raceKm: z.number().min(1).max(100).optional(),
    days: z.array(z.enum(WEEKDAYS)).min(2).max(6),
    location: z.enum(LOCATIONS),
    sessionMin: z.number().int().min(20).max(90),
    experience: z.enum(EXPERIENCE),
    style: z.enum(STYLES).default("auto"),
    runs: z.enum(RUN_MODES).default("separate"),
    units: z.enum(["metric", "imperial"]).default("metric"),
    /** "Swap a session": what to change, applied to [previousPlanId]. */
    change: z.string().trim().max(200).optional(),
    previousPlanId: z.string().regex(/^[A-Za-z0-9]{1,40}$/).optional(),
  })
  .refine((r) => new Set(r.days).size === r.days.length, { message: "Training days must be unique" });
export type PlanRequest = z.infer<typeof PlanRequest>;

const Exercise = z.object({
  name: text(60, 1),
  sets: z.number().int().min(1).max(10),
  reps: text(20, 1),
  load: text(40),
  restSec: z.number().int().min(0).max(300),
});

const Session = z.object({
  day: z.enum(WEEKDAYS),
  kind: z.enum(SESSION_KINDS),
  activityId: z.enum(PLAN_ACTIVITIES),
  title: text(40, 1),
  durationMin: z.number().int().min(10).max(180),
  focus: text(160),
  target: text(200),
  exercises: z.array(Exercise).max(10),
});
export type PlanSession = z.infer<typeof Session>;

/** The model's reply: one session per training day. Rest days are filled in by [assembleWeek]. */
export const GeneratedPlan = z.object({
  summary: text(400),
  sessions: z.array(Session).min(1).max(7),
  restDayNote: text(100),
  progression: text(600),
  notes: text(400),
});
export type GeneratedPlan = z.infer<typeof GeneratedPlan>;

const exerciseJson = {
  type: "object",
  additionalProperties: false,
  required: ["name", "sets", "reps", "load", "restSec"],
  properties: {
    name: { type: "string", description: "Common exercise name, e.g. Goblet squat" },
    sets: { type: "integer" },
    reps: { type: "string", description: "Reps or time per set: \"8\", \"8-10\", \"40 s\", \"40 m\"" },
    load: { type: "string", description: "How hard, in plain words: \"Moderate: you could do 2-3 more\", \"Bodyweight\"" },
    restSec: { type: "integer", description: "Rest between sets in seconds" },
  },
} as const;

export const GENERATED_PLAN_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["summary", "sessions", "restDayNote", "progression", "notes"],
  properties: {
    summary: { type: "string", description: "One or two sentences (under 300 characters) on how the week is built and why" },
    sessions: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["day", "kind", "activityId", "title", "durationMin", "focus", "target", "exercises"],
        properties: {
          day: { type: "string", enum: [...WEEKDAYS] },
          kind: { type: "string", enum: [...SESSION_KINDS] },
          activityId: { type: "string", enum: [...PLAN_ACTIVITIES] },
          title: { type: "string", description: "What the session works, plain words: \"Full body\", \"Legs and core\", \"Easy run\"" },
          durationMin: { type: "integer" },
          focus: { type: "string", description: "One line: what the session is for" },
          target: { type: "string", description: "Effort guidance for cardio (pace by feel, distance); empty for strength" },
          exercises: { type: "array", items: exerciseJson, description: "Strength and mobility only; empty for cardio" },
        },
      },
    },
    restDayNote: { type: "string", description: "What a rest day asks for, e.g. \"Walk 8,000 steps\"" },
    progression: { type: "string", description: "Two or three sentences (under 400 characters) on how the plan gets harder week to week" },
    notes: { type: "string", description: "One short practical note (under 250 characters), or an empty string" },
  },
} as const;

export type PlanDay = (PlanSession & { rest: false }) | { day: Weekday; rest: true; title: string; note: string };

/**
 * One entry per weekday, Monday first: the model's session on each training day and a rest day
 * everywhere else. Sessions on days the person didn't pick, and second sessions on a day, are
 * dropped. Throws when a training day has no session.
 */
export function assembleWeek(plan: GeneratedPlan, days: readonly Weekday[]): PlanDay[] {
  const byDay = new Map<Weekday, PlanSession>();
  for (const s of plan.sessions) {
    if (days.includes(s.day) && !byDay.has(s.day)) byDay.set(s.day, s);
  }
  const missing = days.filter((d) => !byDay.has(d));
  if (missing.length > 0) throw new Error(`Plan has no session on ${missing.join(", ")}`);
  return WEEKDAYS.map((day) => {
    const s = byDay.get(day);
    return s ? { ...s, rest: false as const } : { day, rest: true as const, title: "Rest", note: plan.restDayNote };
  });
}

const RACE_LABELS: Record<string, string> = { "5": "5K", "10": "10K", "21.1": "Half", "42.2": "Marathon" };

/**
 * The plan's name in plain words from the goal and schedule ("Build muscle at home · 3 days a
 * week"). The app names plans the same way (PlanNames), so old and new plans read alike.
 */
export function planName(r: PlanRequest): string {
  const race = r.raceKm != null ? RACE_LABELS[String(r.raceKm)] : undefined;
  const what = {
    lose_fat: "Lose fat",
    build_muscle: "Build muscle",
    run_further: race ? `Run a ${race}` : "Run further",
    get_in_shape: "Get back in shape",
  }[r.goal];
  const where = { gym: "", home: " at home", none: " with no equipment" }[r.location];
  const days = r.days.length;
  return `${what}${where} · ${days} ${days === 1 ? "day" : "days"} a week`;
}
