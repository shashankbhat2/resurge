import { describe, expect, it, vi } from "vitest";

// The tools module imports Firestore loaders; the plan-change tool never touches them.
vi.mock("../src/lib/data", () => ({}));

import { formatDays, formatGoal, formatPlan } from "../src/lib/format";
import { runTool } from "../src/coach/tools";
import { COACH_SYSTEM } from "../src/prompts/coach";

describe("coach context", () => {
  it("says where the person is in their goal and what a good day needs", () => {
    const text = formatGoal(
      {
        goal: { type: "lose_fat", weeks: 12, startDate: "2026-10-01", startKg: 84, targetKg: 78 },
        training: { days: ["mon", "wed", "fri"], location: "gym", sessionMin: 45, experience: "some", style: "full_body", runs: "separate" },
        dayGoals: { sleep: true, steps: false, meals: 2, water: false, weeklyDays: 5 },
      },
      "2026-10-24",
    );
    expect(text).toContain("Lose fat over 12 weeks");
    expect(text).toContain("week 4 of 12");
    expect(text).toContain("84 kg to 78 kg");
    expect(text).toContain("Trains mon, wed, fri");
    expect(text).toContain("sleep goal, 2 meals logged, every habit");
    expect(text).toContain("won at 5 good days");
  });

  it("has no goal before onboarding", () => {
    expect(formatGoal({}, "2026-10-24")).toBe("No goal set yet.");
  });

  it("lists the plan a day per line", () => {
    const text = formatPlan({
      name: "Lose fat · 3 days a week",
      summary: "Full body three times a week.",
      days: [
        { day: "mon", rest: false, title: "Full body", kind: "strength", durationMin: 45, exercises: [{ name: "Goblet squat", sets: 3, reps: "8", load: "Moderate" }] },
        { day: "tue", rest: true, title: "Rest", note: "Walk 8,000 steps" },
      ],
    });
    expect(text.split("\n")).toEqual([
      "Lose fat · 3 days a week: Full body three times a week.",
      "mon: Full body (strength, 45 min) | Goblet squat 3x8",
      "tue: rest (Walk 8,000 steps)",
    ]);
    expect(formatPlan(null)).toBe("No plan yet.");
  });

  it("joins streak days with scores and skips empty days", () => {
    const text = formatDays(
      ["2026-10-22", "2026-10-23", "2026-10-24"],
      [{ date: "2026-10-22", met: true }, { date: "2026-10-23", met: false, done: 3, total: 5 }],
      [{ date: "2026-10-23", recovery: 41, effort: 92, fuel: 60, effortLow: 55, effortHigh: 80, recoveryWhy: "Held back by short sleep." }],
    );
    const lines = text.split("\n");
    expect(lines).toHaveLength(3);
    expect(lines[1]).toBe("2026-10-22 | good day");
    expect(lines[2]).toContain("missed (3/5 goals) | recovery 41 | effort 92 (target 55-80) | fuel 60");
    expect(lines[2]).toContain("Held back by short sleep.");
    expect(formatDays(["2026-10-24"], [], [])).toMatch(/No streak days/);
  });
});

describe("suggest_plan_change", () => {
  it("returns a button action when there is a plan", async () => {
    const r = await runTool("u", "suggest_plan_change", { change: "Move Friday to Thursday", label: "Move Friday" }, { hasPlan: true });
    expect(r.isError).toBe(false);
    expect(r.action).toEqual({ type: "plan_change", change: "Move Friday to Thursday", label: "Move Friday" });
  });

  it("refuses without a plan and clips long text", async () => {
    expect((await runTool("u", "suggest_plan_change", { change: "x", label: "y" }, { hasPlan: false })).isError).toBe(true);
    const r = await runTool("u", "suggest_plan_change", { change: "a".repeat(400), label: "b".repeat(60) }, { hasPlan: true });
    expect(r.action!.change.length).toBe(300);
    expect(r.action!.label.length).toBe(40);
  });
});

describe("coach prompt", () => {
  it("no longer says food logging is missing", () => {
    expect(COACH_SYSTEM).not.toMatch(/logging are not available/);
    expect(COACH_SYSTEM).toContain("suggest_plan_change");
  });
});
