import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { ENFORCE_APP_CHECK } from "../coach/chat";
import { generateStructured, InsightGenerationError } from "../insights/generate";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { userDoc } from "../lib/firebase";
import { LIMITS, PLAN_MODEL } from "../lib/models";
import { consumeOrThrow, recordUsage } from "../lib/quota";
import { isValidTimeZone, localParts } from "../lib/time";
import { PLAN_SYSTEM } from "../prompts/plan";
import { assembleWeek, GENERATED_PLAN_JSON_SCHEMA, GeneratedPlan, type PlanDay, PlanRequest, WEEKDAYS } from "./schema";

const GOAL_LINES: Record<PlanRequest["goal"], (r: PlanRequest) => string> = {
  lose_fat: (r) =>
    r.startKg && r.targetKg
      ? `Lose fat: from ${r.startKg} kg to ${r.targetKg} kg in ${r.weeks} weeks.`
      : `Lose fat over ${r.weeks} weeks.`,
  build_muscle: (r) =>
    r.startKg && r.targetKg
      ? `Build muscle: from ${r.startKg} kg to ${r.targetKg} kg in ${r.weeks} weeks.`
      : `Build muscle over ${r.weeks} weeks.`,
  run_further: (r) => (r.raceKm ? `Run ${r.raceKm} km at the end of ${r.weeks} weeks.` : `Run further over ${r.weeks} weeks.`),
  get_in_shape: (r) => `Get back in shape: train consistently for ${r.weeks} weeks.`,
};

const LOCATION_LABEL = { gym: "a gym", home: "home (dumbbells, band, bench or chair)", none: "anywhere, no equipment" } as const;
const EXPERIENCE_LABEL = { new: "new to training", some: "some experience", years: "years of training" } as const;
const STYLE_LABEL = {
  auto: "your choice, whatever fits the days best",
  full_body: "full body every strength session",
  push_pull_legs: "push / pull / legs",
  upper_lower: "upper / lower",
  body_part: "one muscle group per session (body-part split)",
} as const;
const RUNS_LABEL = {
  separate: "on their own days",
  in_workout: "inside strength sessions, as a warm-up or finisher; no run-only days",
  none: "no running; use other cardio (cycling, rowing, walking) where cardio is needed",
} as const;

/** The user message: the request as plain lines, plus the earlier plan when changing one. Deterministic. */
export function formatPlanRequest(r: PlanRequest, previous?: PlanDay[]): string {
  const days = WEEKDAYS.filter((d) => r.days.includes(d));
  const lines = [
    `Goal: ${GOAL_LINES[r.goal](r)}`,
    `Training days: ${days.join(", ")} (${days.length} a week). Rest days: ${WEEKDAYS.filter((d) => !days.includes(d)).join(", ") || "none"}.`,
    `Session length: ${r.sessionMin} minutes.`,
    `Trains at: ${LOCATION_LABEL[r.location]}.`,
    `Experience: ${EXPERIENCE_LABEL[r.experience]}.`,
    `Strength split: ${STYLE_LABEL[r.style]}.`,
    // A run goal always gets its own run days.
    `Runs: ${RUNS_LABEL[r.goal === "run_further" ? "separate" : r.runs]}.`,
    `Units: ${r.units === "imperial" ? "miles and pounds" : "kilometres and kilograms"}.`,
  ];
  if (previous && r.change) {
    lines.push("", "Earlier plan:", JSON.stringify(previous), "", `Change requested: ${r.change}`);
  }
  return lines.join("\n");
}

/**
 * Builds a weekly training plan from the goal and schedule picked in onboarding. Saves it as a draft
 * in users/{uid}/plans/{id} (server-written only) and returns it; the app makes it the active plan
 * when the person accepts it.
 */
export const generatePlan = onCall(
  {
    secrets: [ANTHROPIC_API_KEY],
    enforceAppCheck: ENFORCE_APP_CHECK,
    timeoutSeconds: 120,
    memory: "512MiB",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to build a plan.");

    const parsed = PlanRequest.safeParse(request.data);
    if (!parsed.success) {
      logger.warn("generatePlan rejected a request", { uid, issues: parsed.error.issues });
      throw new HttpsError("invalid-argument", "Pick a goal and two to six training days.");
    }
    const input = parsed.data;

    const user = (await userDoc(uid).get()).data() ?? {};
    const tz = isValidTimeZone(user.timezone) ? (user.timezone as string) : "UTC";
    const today = localParts(tz).date;
    try {
      await consumeOrThrow(uid, today, "plans", LIMITS.plans);
    } catch (e) {
      logger.warn("generatePlan quota", { uid, error: (e as Error).message });
      throw e;
    }

    let previous: PlanDay[] | undefined;
    if (input.previousPlanId && input.change) {
      const snap = await userDoc(uid).collection("plans").doc(input.previousPlanId).get();
      previous = snap.exists ? (snap.get("days") as PlanDay[] | undefined) : undefined;
    }

    try {
      const { value, usage } = await generateStructured({
        model: PLAN_MODEL,
        system: PLAN_SYSTEM,
        context: formatPlanRequest(input, previous),
        jsonSchema: GENERATED_PLAN_JSON_SCHEMA,
        schema: GeneratedPlan,
      });
      await recordUsage(uid, today, usage);

      const days = assembleWeek(value, input.days);
      const plan = {
        name: value.name,
        summary: value.summary,
        days,
        progression: value.progression,
        notes: value.notes,
      };
      const { change: _change, previousPlanId: _previous, ...settings } = input;
      const ref = await userDoc(uid).collection("plans").add({
        ...plan,
        request: settings,
        status: "draft",
        model: usage.model,
        createdAt: FieldValue.serverTimestamp(),
      });
      return { id: ref.id, ...plan };
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("generatePlan failed", { uid, error: (e as Error).message });
      throw new HttpsError(
        e instanceof InsightGenerationError ? "internal" : "unavailable",
        "Couldn't build your plan. Try again.",
      );
    }
  },
);
