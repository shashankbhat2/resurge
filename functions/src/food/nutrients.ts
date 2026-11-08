/**
 * Nutrients tracked per food, always per 100 g edible portion (ml for drinks, treated as grams).
 * Units: g unless noted. null means the source doesn't report it, which is different from 0.
 */
export const NUTRIENT_KEYS = [
  "kcal",
  "protein",
  "carbs",
  "fat",
  "fiber",
  "sugar",
  "satFat",
  "cholesterol", // mg
  "sodium", // mg
  "potassium", // mg
  "calcium", // mg
  "iron", // mg
  "magnesium", // mg
  "zinc", // mg
  "vitA", // µg retinol activity equivalents
  "vitC", // mg
  "vitD", // µg
  "vitB12", // µg
  "folate", // µg
] as const;

export type NutrientKey = (typeof NUTRIENT_KEYS)[number];
export type Nutrients = Record<NutrientKey, number | null>;

export const CORE_KEYS = ["kcal", "protein", "carbs", "fat", "fiber"] as const satisfies readonly NutrientKey[];

export function emptyNutrients(): Nutrients {
  return Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, null])) as Nutrients;
}

export function nutrientsFrom(values: readonly (number | null)[]): Nutrients {
  return Object.fromEntries(NUTRIENT_KEYS.map((k, i) => [k, values[i] ?? null])) as Nutrients;
}

/** Rounds for storage: a few significant digits are all any source supports. */
export function rounded(n: Nutrients): Nutrients {
  const out = emptyNutrients();
  for (const k of NUTRIENT_KEYS) {
    const v = n[k];
    out[k] = v == null ? null : Number(v.toPrecision(4));
  }
  return out;
}

/**
 * Per-100 g values of a dish as served, from its ingredients ([parts], each per 100 g with its grams
 * as it goes in) and the served weight. Dividing by the served weight, not the ingredients' total,
 * accounts for water gained in cooking (dal, rice) or lost (frying, roasting). A nutrient stays null
 * if any part lacks it, except the core macros where a missing value counts as 0.
 */
export function dishPer100g(parts: { per100g: Nutrients; grams: number }[], servedGrams: number): Nutrients {
  const out = emptyNutrients();
  if (servedGrams <= 0) return out;
  for (const k of NUTRIENT_KEYS) {
    const core = (CORE_KEYS as readonly string[]).includes(k);
    let sum = 0;
    let missing = false;
    for (const p of parts) {
      const v = p.per100g[k];
      if (v == null) {
        if (!core) missing = true;
        continue;
      }
      sum += (v * p.grams) / 100;
    }
    out[k] = missing ? null : (sum * 100) / servedGrams;
  }
  return out;
}

/** One-line summary for the model: the values it needs to judge a match, nothing more. */
export function brief(n: Nutrients): string {
  const f = (v: number | null, unit = "g") => (v == null ? "?" : `${Math.round(v * 10) / 10}${unit}`);
  return `${n.kcal == null ? "?" : Math.round(n.kcal)} kcal, P ${f(n.protein)}, C ${f(n.carbs)}, F ${f(n.fat)}`;
}
