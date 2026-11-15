import { describe, expect, it } from "vitest";
import { dayBand, mealBand, mealGlycemic, predictGi } from "../src/lib/glycemic";
import { goalProgress, trend, trendOn } from "../src/lib/weight";

describe("predicted glycaemic index and load", () => {
  // Shared test vector with the app's GlycemicTest ("Dal rice"): keep both in step.
  it("predicts GI from macros: fibre, protein and fat lower it", () => {
    expect(predictGi({ carbs: 100, protein: 22, fat: 15, fiber: 8 })).toBe(58);
    expect(predictGi({ carbs: 80, protein: 2, fat: 1, fiber: 0 })).toBe(72 - 1); // nearly pure starch
    expect(predictGi({ carbs: 10, protein: 20, fat: 20, fiber: 5 })).toBe(32);
    expect(predictGi({ carbs: 0, protein: 25, fat: 10 })).toBeNull();
  });

  it("weights foods by available carbs and prefers a GI the assistant supplied", () => {
    expect(mealGlycemic([{ grams: 100, carbs: 100, protein: 22, fat: 15, fiber: 8 }])).toEqual({ gi: 58, gl: 53, band: "high", basis: "predicted", availableCarbsG: 92 });
    const mixed = mealGlycemic([
      { grams: 100, carbs: 100, protein: 22, fat: 15, fiber: 8 },
      { grams: 150, carbs: 14, protein: 0.3, fat: 0.2, fiber: 2.4, gi: 36 },
    ]);
    expect(mixed).toMatchObject({ basis: "mixed", availableCarbsG: 109 });
    expect(mixed.gi).toBe(Math.round((58 * 92 + 36 * 17.4) / 109.4));
  });

  it("has no GI for meals with almost no carbs", () => {
    expect(mealGlycemic([{ grams: 200, carbs: 1, protein: 25, fat: 10 }])).toEqual({ gi: null, gl: 0, band: "low", basis: "predicted", availableCarbsG: 2 });
    expect(mealGlycemic([])).toMatchObject({ gi: null, gl: 0 });
  });

  it("bands meals and days", () => {
    expect([mealBand(10), mealBand(11), mealBand(19), mealBand(20)]).toEqual(["low", "medium", "medium", "high"]);
    expect([dayBand(79), dayBand(80), dayBand(120), dayBand(121)]).toEqual(["low", "medium", "medium", "high"]);
  });
});

describe("weight trend", () => {
  const w = (localDate: string, kg: number) => ({ kg, localDate, atMs: Date.parse(`${localDate}T08:00:00Z`) });

  it("matches the app's smoothing: early points weigh in fully, later ones by gap", () => {
    const t = trend([w("2026-11-01", 80), w("2026-11-02", 79), w("2026-11-04", 78)], "2026-11-05");
    expect(t.map((p) => p.date)).toEqual(["2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05"]);
    expect(t[1].kg).toBeCloseTo(79.5); // alpha = 1/2
    expect(t[3].kg).toBeCloseTo(79.5 + (1 / 3) * (78 - 79.5)); // alpha = max(1/3, 1 - 0.9^2)
    expect(trendOn(t, "2026-11-05")).toBeCloseTo(79);
    expect(trend([], "2026-11-05")).toEqual([]);
  });

  it("measures goal progress either way", () => {
    const goal = { startKg: 80, startDate: "2026-11-01", goalKg: 70, paceKgPerWeek: 0.5, mode: "change" as const, milestonesCelebrated: 0, reachedOn: null };
    expect(goalProgress(goal, 75)).toBe(0.5);
    expect(goalProgress(goal, 82)).toBe(0);
    expect(goalProgress({ ...goal, goalKg: 90 }, 85)).toBe(0.5);
  });
});
