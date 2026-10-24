import { describe, expect, it } from "vitest";
import { formatPlanRequest } from "../src/plan/generate";
import { assembleWeek, clip, GENERATED_PLAN_JSON_SCHEMA, GeneratedPlan, PlanRequest, planName } from "../src/plan/schema";

const session = (day: "mon" | "wed" | "fri" | "sat", title = "Strength A") => ({
  day,
  kind: "strength" as const,
  activityId: "weights" as const,
  title,
  durationMin: 45,
  focus: "Full body",
  target: "",
  exercises: [{ name: "Goblet squat", sets: 3, reps: "10", load: "RPE 7", restSec: 90 }],
});

const plan = {
  summary: "Two strength days and two easy runs.",
  sessions: [session("mon"), session("wed", "Easy run"), session("fri", "Strength B"), session("sat", "Long run")],
  restDayNote: "Walk 8,000 steps",
  progression: "Add a rep each week.",
  notes: "",
};

describe("plan request", () => {
  const base = { goal: "lose_fat", weeks: 12, days: ["mon", "wed"], location: "gym", sessionMin: 45, experience: "some" };

  it("accepts a valid request and defaults units to metric", () => {
    const r = PlanRequest.parse(base);
    expect(r.units).toBe("metric");
  });

  it("a run goal keeps its runs on their own days", () => {
    const r = PlanRequest.parse({ ...base, goal: "run_further", runs: "none", style: "push_pull_legs" });
    expect(formatPlanRequest(r)).toContain("Runs: on their own days.");
    expect(formatPlanRequest(r)).toContain("Strength split: push / pull / legs.");
  });

  it("rejects one day, seven days and duplicates", () => {
    expect(PlanRequest.safeParse({ ...base, days: ["mon"] }).success).toBe(false);
    expect(PlanRequest.safeParse({ ...base, days: ["mon", "tue", "wed", "thu", "fri", "sat", "sun"] }).success).toBe(false);
    expect(PlanRequest.safeParse({ ...base, days: ["mon", "mon"] }).success).toBe(false);
  });

  it("formats days in week order with rest days and the change request", () => {
    const r = PlanRequest.parse({ ...base, days: ["fri", "mon"], startKg: 84, targetKg: 78, change: "No running" });
    const text = formatPlanRequest(r, []);
    expect(text).toContain("Goal: Lose fat: from 84 kg to 78 kg in 12 weeks.");
    expect(text).toContain("Training days: mon, fri (2 a week). Rest days: tue, wed, thu, sat, sun.");
    expect(text).toContain("Change requested: No running");
    expect(text).toContain("Strength split: your choice");
    expect(text).toContain("Runs: on their own days.");
    expect(formatPlanRequest(r)).not.toContain("Change requested");
  });
});

describe("plan names", () => {
  const base = { goal: "lose_fat", weeks: 12, days: ["mon", "wed", "fri", "sat"], location: "gym", sessionMin: 45, experience: "some" };

  it("are plain words from the goal and schedule", () => {
    expect(planName(PlanRequest.parse(base))).toBe("Lose fat · 4 days a week");
    expect(planName(PlanRequest.parse({ ...base, goal: "build_muscle", location: "home", days: ["mon", "wed", "fri"] }))).toBe(
      "Build muscle at home · 3 days a week",
    );
    expect(planName(PlanRequest.parse({ ...base, goal: "run_further", raceKm: 10, location: "none" }))).toBe(
      "Run a 10K with no equipment · 4 days a week",
    );
  });
});

describe("generated plan", () => {
  it("validates and matches the JSON schema's required keys", () => {
    expect(GeneratedPlan.safeParse(plan).success).toBe(true);
    expect([...GENERATED_PLAN_JSON_SCHEMA.required].sort()).toEqual(Object.keys(plan).sort());
    const s = GENERATED_PLAN_JSON_SCHEMA.properties.sessions.items;
    expect([...s.required].sort()).toEqual(Object.keys(plan.sessions[0]).sort());
    expect([...s.properties.exercises.items.required].sort()).toEqual(Object.keys(plan.sessions[0].exercises[0]).sort());
  });

  it("clips over-long text instead of rejecting the plan", () => {
    const long = "Lift heavy. ".repeat(60);
    const parsed = GeneratedPlan.parse({ ...plan, summary: long, progression: long, notes: long });
    expect(parsed.summary.length).toBeLessThanOrEqual(400);
    expect(parsed.summary.endsWith(".")).toBe(true);
    expect(parsed.progression.length).toBeLessThanOrEqual(600);
    expect(clip("one two three four", 9)).toBe("one two…");
    expect(clip("short", 10)).toBe("short");
  });

  it("fills rest days, drops off-schedule and duplicate sessions", () => {
    const week = assembleWeek(
      { ...plan, sessions: [...plan.sessions, session("mon", "Extra")] },
      ["mon", "wed", "fri"],
    );
    expect(week.map((d) => d.day)).toEqual(["mon", "tue", "wed", "thu", "fri", "sat", "sun"]);
    expect(week.filter((d) => !d.rest).map((d) => d.title)).toEqual(["Strength A", "Easy run", "Strength B"]);
    expect(week[1]).toEqual({ day: "tue", rest: true, title: "Rest", note: "Walk 8,000 steps" });
  });

  it("throws when a training day has no session", () => {
    expect(() => assembleWeek(plan, ["mon", "tue"])).toThrow("tue");
  });
});
