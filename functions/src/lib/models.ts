/** The two chat models the user can pick in the composer. */
export type ModelChoice = "fast" | "smart";

export type Effort = "low" | "medium" | "high";

export interface ModelSpec {
  id: string;
  effort: Effort;
  label: string;
}

export const MODELS: Record<ModelChoice, ModelSpec> = {
  fast: { id: "claude-haiku-5-5", effort: "low", label: "Fast" },
  smart: { id: "claude-sonnet-5-5", effort: "high", label: "Smart" },
};

/** Per-activity insights: quick and cheap, but a bit more thought than chat "Fast". */
export const ACTIVITY_INSIGHT_MODEL: ModelSpec = { ...MODELS.fast, effort: "medium" };

/** Meal photo / description analysis: Sonnet with medium thinking for portion estimates. */
export const MEAL_ANALYSIS_MODEL: ModelSpec = { id: "claude-sonnet-5-5", effort: "medium", label: "Meal" };

/** Weekly training plan from onboarding: Sonnet with medium thinking to balance the week. */
export const PLAN_MODEL: ModelSpec = { id: "claude-sonnet-5-5", effort: "medium", label: "Plan" };

/** Saturday weekly report: same model as activity insights, with more thought. */
export const WEEKLY_INSIGHT_MODEL: ModelSpec = { ...MODELS.fast, effort: "high" };

/** Daily per-user limits, counted in users/{uid}/usage/{localDate}. */
export const LIMITS = {
  fastTurns: 40,
  smartTurns: 10,
  activityInsights: 4,
  foodScans: 20,
  plans: 6,
} as const;
