import { describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/firebase", () => ({ db: {}, userDoc: () => ({}), DATABASE: "resurge", messaging: () => ({}) }));
import { toResponse } from "../src/food/schema";
import { emptyNutrients } from "../src/food/nutrients";
import { mealSummary } from "../src/lib/data";
import { formatMeals, formatWorkoutLogs } from "../src/lib/format";

describe("analyzeMeal response", () => {
  it("keeps the flat per-100 g macros older app versions read, and adds nutrients and source", () => {
    const per100g = { ...emptyNutrients(), kcal: 130, protein: 2.7, carbs: 28, fat: 0.3, fiber: 0.4, iron: 0.2 };
    const res = toResponse({
      title: "Dal rice",
      question: "",
      options: [],
      notes: "",
      items: [{ name: "Rice", grams: 180.4, per100g, confidence: 0.912, sourceId: "cofid:11-858", sourceName: "CoFID 2021", sourceDetail: "Rice, white, basmati, boiled", verified: true }],
    });
    expect(res.items[0]).toMatchObject({ name: "Rice", grams: 180, kcal: 130, protein: 2.7, carbs: 28, fat: 0.3, fiber: 0.4, confidence: 0.91, verified: true });
    expect(res.items[0].nutrients.iron).toBe(0.2);
    expect(res.items[0].nutrients.vitC).toBeNull();
  });
});

describe("coach food and workout formatting", () => {
  it("groups meals and water by day with fibre, micros and a clearly predicted GI/GL", () => {
    const dal = mealSummary({
      localDate: "2026-10-14", type: "lunch", title: "Dal rice", loggedAtMs: Date.parse("2026-10-14T07:45:00Z"),
      items: [{ name: "Dal rice", grams: 100, kcal: 600, protein: 22, carbs: 100, fat: 15, fiber: 8, micros: { sodium: 300, iron: 4 } }],
    }, "Asia/Kolkata");
    const apple = mealSummary({
      localDate: "2026-10-14", type: "snack", title: "Apple", loggedAtMs: Date.parse("2026-10-14T10:00:00Z"),
      items: [{ name: "Apple", grams: 150, kcal: 52, protein: 0.3, carbs: 14, fat: 0.2, fiber: 2.4, gi: 36 }],
    }, "Asia/Kolkata");
    expect(dal).toMatchObject({ time: "13:15", fiber: 8, micros: { sodium: 300, iron: 4 }, glycemic: { gi: 58, gl: 53, band: "high", basis: "predicted" } });
    expect(apple.glycemic).toMatchObject({ gi: 36, basis: "assistant" });
    const text = formatMeals([apple, dal], { "2026-10-14": 1500, "2026-10-13": 750 });
    const lines = text.split("\n");
    expect(lines[0]).toBe("2026-10-14: 678 kcal, protein 22 g, carbs 121 g, fat 15 g, fibre 12 g · water 1.50 L · predicted day GL ~59 (low)");
    expect(lines[1]).toBe("  micronutrients: sodium 300 mg, iron 4 mg");
    expect(text).toContain("lunch 13:15: Dal rice — 600 kcal, P22 C100 F15 fibre 8 g, predicted GI ~58 / GL ~53 (high)");
    expect(text).toContain("(GI 36 from assistant)");
    expect(text).toContain("2026-10-13: no meals logged · water 0.75 L");
    expect(text).toMatch(/Note: GI\/GL are PREDICTED, not measured/);
    expect(formatMeals([])).toBe("No meals or water logged in this period.");
  });

  it("lists logged workouts with strength sets and skips hidden ones", () => {
    const text = formatWorkoutLogs([
      { date: "2026-10-14", activity: "weights", title: null, durationMin: 50, source: "manual", deleted: false, metrics: { kcal: 280, exercises: [{ name: "Bench", sets: [{ reps: 8, weightKg: 60 }, { reps: 10, weightKg: null }] }] }, notes: null },
      { date: "2026-10-13", activity: "run", title: null, durationMin: 30, source: "health_connect", deleted: true, metrics: {}, notes: null },
    ]);
    expect(text).toBe("2026-10-14 weights (logged manually): 50 min, 280 kcal — Bench: 8×60kg, 10");
  });
});
