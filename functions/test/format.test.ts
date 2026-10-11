import { describe, expect, it } from "vitest";
import { formatEvent, formatProfile, formatSummaries } from "../src/lib/format";

describe("format", () => {
  it("renders summaries oldest first with a header", () => {
    const text = formatSummaries([
      { date: "2026-10-10", steps: 8412, distanceM: 5800, kcalTotal: 2380, kcalActive: 540, sleepMin: 454, sleepStages: { deep: 80, rem: 66, light: 290, awake: 18 }, restingHr: 58, hrAvg: 72, hrMin: 52, hrMax: 141 },
      { date: "2026-10-09", steps: 6000 },
    ]);
    const lines = text.split("\n");
    expect(lines).toHaveLength(3);
    expect(lines[1].startsWith("2026-10-09 | 6000")).toBe(true);
    expect(lines[2]).toBe("2026-10-10 | 8412 | 5.8 | 2380/540 | 7h34 (80/66/290/18) | - | 58/72/52-141 | -");
  });

  it("says when there is no data", () => {
    expect(formatSummaries([])).toMatch(/No daily data/);
  });

  it("renders a workout with pace and source", () => {
    const line = formatEvent({
      type: "workout", hcId: "x", startMs: 0, endMs: 0, timezone: "UTC", localDate: "2026-10-10",
      workout: { activity: "Running", durationMin: 26, distanceM: 5000, paceSecPerKm: 312, avgHr: 152, maxHr: 176, activeKcal: 390, totalKcal: 420 },
      source: { kind: "WEARABLE", device: "Pixel Watch 3" },
    });
    expect(line).toContain("Running");
    expect(line).toContain("distance 5.00 km");
    expect(line).toContain("pace 5:12/km");
    expect(line).toContain("source Pixel Watch 3");
  });

  it("renders a night of sleep", () => {
    const line = formatEvent({
      type: "sleep", hcId: "s", startMs: 0, endMs: 0, timezone: "UTC", localDate: "2026-10-10",
      sleep: { durationMin: 450, asleepMin: 432, deepMin: 80, lightMin: 250, remMin: 102, awakeMin: 18, efficiency: 0.96, bedtime: "23:10", wake: "06:40" },
    });
    expect(line).toContain("23:10→06:40");
    expect(line).toContain("efficiency 96%");
  });

  it("humanises goals", () => {
    expect(formatProfile({ goals: ["SLEEP_BETTER", "MOVE_MORE"], targets: { calories: 2200, waterMl: 2500 } }))
      .toBe("Goals: sleep better, move more. Daily targets: 2200 kcal eaten, 2500 ml water.");
  });
});

import { formatHabits } from "../src/lib/format";

describe("formatHabits", () => {
  it("marks done, missed and unlogged days", () => {
    const text = formatHabits(
      [{ name: "Walk 10k", currentStreak: 2, bestStreak: 5, log: { "2026-10-04": "done", "2026-10-05": "missed", "2026-10-07": "done" } }],
      ["2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07"],
    );
    expect(text.split("\n")[1]).toBe("Walk 10k | 2 | 5 | ✓✗·✓");
  });

  it("says when there are no habits", () => {
    expect(formatHabits([], [])).toBe("No habits set up.");
  });
});
