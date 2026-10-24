import type { ActivityEvent, DailySummary, DayResult, DayScore, StoredPlan, UserProfile } from "./types";

/*
 * Compact, deterministic text renderings of user data for prompts. Deterministic output keeps
 * prompts stable (good for caching) and testable.
 */

const n = (v: number | null | undefined, digits = 0) =>
  v == null ? "-" : digits === 0 ? Math.round(v).toString() : v.toFixed(digits);

const hours = (min: number | null | undefined) =>
  min == null ? "-" : `${Math.floor(min / 60)}h${String(Math.round(min % 60)).padStart(2, "0")}`;

export function formatProfile(p: UserProfile): string {
  const goals = (p.goals ?? []).map((g) => g.toLowerCase().replace(/_/g, " ")).join(", ") || "none set";
  const kcal = p.targets?.calories ?? "-";
  const water = p.targets?.waterMl ?? "-";
  // Same defaults as the app (Targets) when the user hasn't set these yet.
  const sleep = hours(p.targets?.sleepMinutes ?? 7 * 60);
  const steps = p.targets?.steps ?? 10000;
  return `Goals: ${goals}. Daily targets: ${kcal} kcal eaten, ${water} ml water, ${sleep} asleep, ${steps} steps.`;
}

/** One line per day, oldest first. Columns are described in the header line. */
export function formatSummaries(rows: DailySummary[]): string {
  if (rows.length === 0) return "No daily data synced for this period.";
  const header =
    "date | steps | km | kcal total/active | sleep (deep/rem/light/awake min) | exercise min (types) | HR rest/avg/min-max | weight kg";
  const lines = [...rows]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((r) => {
      const st = r.sleepStages;
      const stages = st ? ` (${n(st.deep)}/${n(st.rem)}/${n(st.light)}/${n(st.awake)})` : "";
      const types = r.exerciseTypes?.length ? ` (${r.exerciseTypes.join(", ")})` : "";
      return [
        r.date,
        n(r.steps),
        r.distanceM == null ? "-" : (r.distanceM / 1000).toFixed(1),
        `${n(r.kcalTotal)}/${n(r.kcalActive)}`,
        `${hours(r.sleepMin)}${stages}`,
        `${n(r.exerciseMin)}${types}`,
        `${n(r.restingHr)}/${n(r.hrAvg)}/${n(r.hrMin)}-${n(r.hrMax)}`,
        n(r.weightKg, 1),
      ].join(" | ");
    });
  return [header, ...lines].join("\n");
}

function pace(secPerKm: number | null | undefined): string {
  if (secPerKm == null || !isFinite(secPerKm) || secPerKm <= 0) return "-";
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, "0")}/km`;
}

export function formatEvent(e: ActivityEvent): string {
  const src = e.source?.device ?? e.source?.app ?? e.source?.kind ?? "unknown source";
  if (e.type === "workout" && e.workout) {
    const w = e.workout;
    return [
      `${e.localDate} workout: ${w.title ? `${w.title} (${w.activity})` : w.activity}`,
      `duration ${n(w.durationMin)} min`,
      w.distanceM != null ? `distance ${(w.distanceM / 1000).toFixed(2)} km` : null,
      w.paceSecPerKm != null ? `pace ${pace(w.paceSecPerKm)}` : null,
      `HR avg ${n(w.avgHr)} max ${n(w.maxHr)}`,
      `kcal active ${n(w.activeKcal)} total ${n(w.totalKcal)}`,
      w.steps != null ? `steps ${n(w.steps)}` : null,
      `source ${src}`,
    ].filter(Boolean).join(", ");
  }
  if (e.type === "sleep" && e.sleep) {
    const s = e.sleep;
    return [
      `${e.localDate} sleep: ${s.bedtime}→${s.wake}`,
      `in bed ${hours(s.durationMin)}, asleep ${hours(s.asleepMin)}`,
      `deep ${n(s.deepMin)} rem ${n(s.remMin)} light ${n(s.lightMin)} awake ${n(s.awakeMin)} min`,
      s.efficiency != null ? `efficiency ${Math.round(s.efficiency * 100)}%` : null,
      `HR avg ${n(s.avgHr)} min ${n(s.minHr)}`,
      `source ${src}`,
    ].filter(Boolean).join(", ");
  }
  return `${e.localDate} ${e.type}`;
}

/** One line per habit: streaks and a compact per-day record (✓ done, ✗ not done, · unlogged). */
export function formatHabits(habits: { name: string; private?: boolean; currentStreak: number; bestStreak: number; log: Record<string, string> }[], dates: string[]): string {
  if (habits.length === 0) return "No habits set up.";
  const header = `habit | current streak | best | ${dates[0]}→${dates[dates.length - 1]} (✓ done, ✗ not done, · unlogged)`;
  const lines = habits.map((h) => {
    const marks = dates.map((d) => (h.log[d] === "done" ? "✓" : h.log[d] === "missed" ? "✗" : "·")).join("");
    // Private habits only ever arrive as a cover name; marking them keeps the coach from guessing.
    return `${h.name}${h.private ? " (private)" : ""} | ${h.currentStreak} | ${h.bestStreak} | ${marks}`;
  });
  return [header, ...lines].join("\n");
}

export function formatEvents(events: ActivityEvent[]): string {
  if (events.length === 0) return "None.";
  return [...events].sort((a, b) => a.startMs - b.startMs).map((e) => `- ${formatEvent(e)}`).join("\n");
}

/** Logged meals grouped by day, with daily totals. */
export function formatMeals(meals: { date: string; type: string; title: string; kcal: number; protein: number; carbs: number; fat: number; items: string[] }[]): string {
  if (meals.length === 0) return "No meals logged in this period.";
  const byDay = new Map<string, typeof meals>();
  for (const m of meals) byDay.set(m.date, [...(byDay.get(m.date) ?? []), m]);
  return [...byDay.entries()]
    .map(([date, list]) => {
      const t = list.reduce((a, m) => ({ kcal: a.kcal + m.kcal, p: a.p + m.protein, c: a.c + m.carbs, f: a.f + m.fat }), { kcal: 0, p: 0, c: 0, f: 0 });
      const rows = list.map((m) => `  - ${m.type}: ${m.title} — ${m.kcal} kcal, P${m.protein} C${m.carbs} F${m.fat} (${m.items.join(", ")})`);
      return `${date}: ${t.kcal} kcal, protein ${t.p} g, carbs ${t.c} g, fat ${t.f} g\n${rows.join("\n")}`;
    })
    .join("\n");
}

/** Workouts logged or edited in the app. */
export function formatWorkoutLogs(logs: { date: string; activity: string; title: string | null; durationMin: number; source: string; deleted: boolean; metrics: Record<string, unknown>; notes: string | null }[]): string {
  const visible = logs.filter((l) => !l.deleted);
  if (visible.length === 0) return "No workouts logged or edited in the app in this period.";
  return visible
    .map((l) => {
      const m = l.metrics;
      const parts = [`${l.durationMin} min`];
      if (typeof m.distanceM === "number") parts.push(`${(m.distanceM / 1000).toFixed(2)} km`);
      if (typeof m.kcal === "number") parts.push(`${m.kcal} kcal`);
      if (typeof m.avgHr === "number") parts.push(`avg HR ${m.avgHr}`);
      if (typeof m.rpe === "number") parts.push(`RPE ${m.rpe}`);
      if (typeof m.reps === "number") parts.push(`${m.reps} reps`);
      if (typeof m.laps === "number") parts.push(`${m.laps} laps`);
      const exercises = Array.isArray(m.exercises)
        ? (m.exercises as Array<{ name?: string; sets?: Array<{ reps?: number; weightKg?: number | null }> }>)
            .map((e) => `${e.name}: ${(e.sets ?? []).map((s) => (s.weightKg ? `${s.reps}×${s.weightKg}kg` : `${s.reps}`)).join(", ")}`)
            .join("; ")
        : "";
      const origin = l.source === "health_connect" ? "edited device workout" : "logged manually";
      return `${l.date} ${l.title ?? l.activity} (${origin}): ${parts.join(", ")}${exercises ? ` — ${exercises}` : ""}${l.notes ? ` — note: ${l.notes}` : ""}`;
    })
    .join("\n");
}

const GOAL_LABELS: Record<string, string> = {
  lose_fat: "Lose fat",
  build_muscle: "Build muscle",
  run_further: "Run further",
  get_in_shape: "Get back in shape",
};

/** The major goal, where they are in it, and the training schedule. */
export function formatGoal(p: UserProfile, today: string): string {
  const g = p.goal;
  if (!g?.type || !g.startDate || !g.weeks) return "No goal set yet.";
  const days = Math.floor((Date.parse(`${today}T00:00:00Z`) - Date.parse(`${g.startDate}T00:00:00Z`)) / 86_400_000);
  const week = Math.min(Math.max(Math.floor(days / 7) + 1, 1), g.weeks);
  const parts = [`${GOAL_LABELS[g.type] ?? g.type} over ${g.weeks} weeks, started ${g.startDate} (now week ${week} of ${g.weeks})`];
  if (g.startKg != null && g.targetKg != null) parts.push(`weight ${g.startKg} kg to ${g.targetKg} kg`);
  if (g.raceKm != null) parts.push(`race ${g.raceKm} km`);
  const t = p.training;
  const schedule = t?.days?.length
    ? `Trains ${t.days.join(", ")} for about ${t.sessionMin ?? "-"} min (${t.location ?? "-"}, experience ${t.experience ?? "-"}, style ${t.style ?? "-"}, runs ${t.runs ?? "-"}).`
    : "";
  const dg = p.dayGoals;
  const day = dg
    ? `A good day needs: ${[dg.sleep !== false ? "sleep goal" : null, dg.steps !== false ? "step goal" : null, dg.meals ? `${dg.meals} meals logged` : null, dg.water ? "water goal" : null, "every habit"].filter(Boolean).join(", ")}. The week is won at ${dg.weeklyDays ?? 5} good days.`
    : "";
  return [parts.join("; ") + ".", schedule, day].filter(Boolean).join("\n");
}

/** The weekly plan, one line per day, Monday first. */
export function formatPlan(plan: StoredPlan | null): string {
  if (!plan?.days?.length) return "No plan yet.";
  const lines = plan.days.map((d) => {
    if (d.rest) return `${d.day}: rest${d.note ? ` (${d.note})` : ""}`;
    const ex = d.exercises?.length ? ` | ${d.exercises.map((e) => `${e.name} ${e.sets}x${e.reps}`).join(", ")}` : "";
    return `${d.day}: ${d.title} (${d.kind ?? "-"}, ${d.durationMin ?? "-"} min)${d.target ? ` target: ${d.target}` : ""}${ex}`;
  });
  return [`${plan.name ?? "Plan"}: ${plan.summary ?? ""}`.trim(), ...lines].join("\n");
}

/**
 * One line per day: whether it was a good (streak) day and its scores. Dates without either are
 * left out. Scores are 0-100; Effort comes with the range that day asked for.
 */
export function formatDays(dates: string[], results: DayResult[], scores: DayScore[]): string {
  const met = new Map(results.map((r) => [r.date, r]));
  const sc = new Map(scores.map((s) => [s.date, s]));
  const lines = dates.flatMap((date) => {
    const r = met.get(date);
    const s = sc.get(date);
    if (!r && !s) return [];
    const day = r ? (r.met ? "good day" : `missed (${r.done ?? "-"}/${r.total ?? "-"} goals)`) : "open";
    if (!s) return [`${date} | ${day}`];
    const band = s.effortLow != null && s.effortHigh != null ? ` (target ${s.effortLow}-${s.effortHigh})` : "";
    const why = [s.recoveryWhy, s.effortWhy, s.fuelWhy].map((w) => w ?? "-").join(" / ");
    return [`${date} | ${day} | recovery ${n(s.recovery)} | effort ${n(s.effort)}${band} | fuel ${n(s.fuel)}${s.soFar ? " (so far)" : ""} | ${why}`];
  });
  if (lines.length === 0) return "No streak days or scores yet.";
  return ["date | streak | recovery | effort | fuel | why (recovery / effort / fuel)", ...lines].join("\n");
}
