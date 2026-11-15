/*
 * Predicted glycaemic index (GI) and glycaemic load (GL) for meals. NOT a measurement: there is no
 * GI table behind it. Each food's GI is estimated from its macros (fibre, protein and fat blunt the
 * glucose response) unless the AI assistant supplied a GI for it via MCP log_meal. Mirrors
 * core/model/.../Glycemic.kt in the app; keep the two in step (shared test vector: "Dal rice").
 */

/** A food as stored on a meal: nutrition per 100 g, plus grams eaten and an optional supplied GI. */
export interface GlycemicItem {
  grams: number;
  carbs: number;
  protein: number;
  fat: number;
  fiber?: number | null;
  gi?: number | null;
}

export type GlycemicBand = "low" | "medium" | "high";
export type GlycemicBasis = "predicted" | "assistant" | "mixed";

export interface GlycemicEstimate {
  /** Carb-weighted GI of the meal (0-100), or null when it has under MIN_CARBS_G of available carbs. */
  gi: number | null;
  /** Glycaemic load: GI x available carbs / 100. */
  gl: number;
  band: GlycemicBand;
  basis: GlycemicBasis;
  availableCarbsG: number;
}

export const MIN_CARBS_G = 5;
export const GLYCEMIC_NOTE =
  "GI/GL are PREDICTED, not measured: estimated from each food's carbs, fibre, protein and fat (or a GI the assistant supplied). They can be well off, especially for single foods, ripeness, cooking and portion; don't use them for medical decisions.";

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

/** GI of one food from its per-100 g macros, or null without carbs. */
export function predictGi(per100g: { carbs: number; protein: number; fat: number; fiber?: number | null }): number | null {
  const carbs = per100g.carbs;
  if (!(carbs > 0)) return null;
  const fibre = per100g.fiber ?? 0;
  const gi = 72 - 25 * Math.min((4 * fibre) / carbs, 1) - 15 * Math.min((per100g.protein + per100g.fat) / carbs, 1);
  return Math.round(clamp(gi, 25, 95));
}

/** Per-meal GL bands (Foster-Powell et al.): low <= 10, medium 11-19, high >= 20. */
export function mealBand(gl: number): GlycemicBand {
  return gl <= 10 ? "low" : gl < 20 ? "medium" : "high";
}

/** Daily GL bands: low < 80, medium 80-120, high > 120. */
export function dayBand(gl: number): GlycemicBand {
  return gl < 80 ? "low" : gl <= 120 ? "medium" : "high";
}

export function mealGlycemic(items: GlycemicItem[]): GlycemicEstimate {
  let avail = 0;
  let weighted = 0;
  let supplied = 0;
  let counted = 0;
  for (const i of items) {
    const f = i.grams / 100;
    const a = Math.max(i.carbs - (i.fiber ?? 0), 0) * f;
    if (a <= 0) continue;
    const given = typeof i.gi === "number" && i.gi >= 0 && i.gi <= 110 ? i.gi : null;
    const gi = given ?? predictGi(i);
    if (gi === null) continue;
    counted++;
    if (given !== null) supplied++;
    avail += a;
    weighted += gi * a;
  }
  const basis: GlycemicBasis = counted > 0 && supplied === counted ? "assistant" : supplied > 0 ? "mixed" : "predicted";
  if (avail < MIN_CARBS_G) return { gi: null, gl: 0, band: "low", basis, availableCarbsG: Math.round(avail) };
  const gi = Math.round(weighted / avail);
  const gl = Math.round((gi * avail) / 100);
  return { gi, gl, band: mealBand(gl), basis, availableCarbsG: Math.round(avail) };
}
