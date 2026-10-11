import { z } from "zod";

/** Nutrition per 100 g, as served. */
const Per100g = {
  kcal: z.number().min(0).max(950),
  protein: z.number().min(0).max(100),
  carbs: z.number().min(0).max(100),
  fat: z.number().min(0).max(100),
  fiber: z.number().min(0).max(100),
};

export const MealEstimate = z.object({
  title: z.string().max(80),
  items: z
    .array(
      z.object({
        name: z.string().min(1).max(60),
        grams: z.number().min(0).max(3000),
        ...Per100g,
        confidence: z.number().min(0).max(1),
      }),
    )
    .max(12),
  question: z.string().max(120),
  options: z.array(z.string().max(40)).max(4),
  notes: z.string().max(200),
});
export type MealEstimate = z.infer<typeof MealEstimate>;

export const MEAL_ESTIMATE_JSON_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["title", "items", "question", "options", "notes"],
  properties: {
    title: { type: "string" },
    items: {
      type: "array",
      items: {
        type: "object",
        additionalProperties: false,
        required: ["name", "grams", "kcal", "protein", "carbs", "fat", "fiber", "confidence"],
        properties: {
          name: { type: "string" },
          grams: { type: "number", description: "Portion weight in grams (ml for drinks)" },
          kcal: { type: "number", description: "kcal per 100 g as served" },
          protein: { type: "number", description: "g per 100 g" },
          carbs: { type: "number", description: "g per 100 g" },
          fat: { type: "number", description: "g per 100 g" },
          fiber: { type: "number", description: "g per 100 g" },
          confidence: { type: "number", description: "0-1" },
        },
      },
    },
    question: { type: "string" },
    options: { type: "array", items: { type: "string" } },
    notes: { type: "string" },
  },
} as const;
