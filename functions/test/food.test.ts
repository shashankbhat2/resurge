import { describe, expect, it } from "vitest";
import { MEAL_ESTIMATE_JSON_SCHEMA, MealEstimate } from "../src/food/schema";
import { formatMeals, formatWorkoutLogs } from "../src/lib/format";

describe("meal estimate schema", () => {
  const valid = {
    title: "Dal rice",
    items: [{ name: "Rice", grams: 180, kcal: 130, protein: 2.7, carbs: 28, fat: 0.3, fiber: 0.4, confidence: 0.9 }],
    question: "",
    options: [],
    notes: "",
  };

  it("accepts a well-formed estimate and rejects impossible nutrition", () => {
    expect(MealEstimate.safeParse(valid).success).toBe(true);
    const badFat = { ...valid, items: [{ ...valid.items[0], fat: 140 }] };
    expect(MealEstimate.safeParse(badFat).success).toBe(false);
  });

  it("JSON schema requires every property structured outputs needs", () => {
    expect([...MEAL_ESTIMATE_JSON_SCHEMA.required].sort()).toEqual(Object.keys(valid).sort());
    expect([...MEAL_ESTIMATE_JSON_SCHEMA.properties.items.items.required].sort()).toEqual(Object.keys(valid.items[0]).sort());
  });
});

describe("coach food and workout formatting", () => {
  it("groups meals by day with totals", () => {
    const text = formatMeals([
      { date: "2026-10-14", type: "lunch", title: "Dal rice", kcal: 600, protein: 20, carbs: 90, fat: 12, items: ["Rice 180g", "Dal 150g"] },
      { date: "2026-10-14", type: "breakfast", title: "Oats", kcal: 300, protein: 10, carbs: 50, fat: 6, items: ["Oats 60g"] },
    ]);
    expect(text.split("\n")[0]).toBe("2026-10-14: 900 kcal, protein 30 g, carbs 140 g, fat 18 g");
    expect(text).toContain("lunch: Dal rice — 600 kcal");
    expect(formatMeals([])).toBe("No meals logged in this period.");
  });

  it("lists logged workouts with strength sets and skips hidden ones", () => {
    const text = formatWorkoutLogs([
      { date: "2026-10-14", activity: "weights", title: null, durationMin: 50, source: "manual", deleted: false, metrics: { kcal: 280, exercises: [{ name: "Bench", sets: [{ reps: 8, weightKg: 60 }, { reps: 10, weightKg: null }] }] }, notes: null },
      { date: "2026-10-13", activity: "run", title: null, durationMin: 30, source: "health_connect", deleted: true, metrics: {}, notes: null },
    ]);
    expect(text).toBe("2026-10-14 weights (logged manually): 50 min, 280 kcal — Bench: 8×60kg, 10");
  });
});
