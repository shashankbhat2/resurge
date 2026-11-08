import { NUTRIENT_KEYS } from "./nutrients";
import type { GroundedMeal } from "./agent";

/**
 * analyzeMeal's response. Each item keeps the flat per-100 g macros older app versions read
 * (kcal, protein, carbs, fat, fiber) and adds every nutrient plus where the numbers came from.
 */
export interface MealResponseItem {
  name: string;
  grams: number;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  confidence: number;
  /** Per 100 g; null where the source doesn't report a nutrient. */
  nutrients: Record<string, number | null>;
  sourceId: string;
  sourceName: string;
  sourceDetail: string;
  verified: boolean;
}

export interface MealResponse {
  title: string;
  items: MealResponseItem[];
  question: string;
  options: string[];
  notes: string;
}

export function toResponse(meal: GroundedMeal): MealResponse {
  return {
    title: meal.title,
    question: meal.question,
    options: meal.options,
    notes: meal.notes,
    items: meal.items.map((i) => ({
      name: i.name,
      grams: Math.round(i.grams),
      kcal: i.per100g.kcal ?? 0,
      protein: i.per100g.protein ?? 0,
      carbs: i.per100g.carbs ?? 0,
      fat: i.per100g.fat ?? 0,
      fiber: i.per100g.fiber ?? 0,
      confidence: Math.round(i.confidence * 100) / 100,
      nutrients: Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, i.per100g[k]])),
      sourceId: i.sourceId,
      sourceName: i.sourceName,
      sourceDetail: i.sourceDetail,
      verified: i.verified,
    })),
  };
}
