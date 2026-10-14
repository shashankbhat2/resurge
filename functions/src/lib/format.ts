import type { ActivityEvent, DailySummary, UserProfile } from "./types";

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
export function formatHabits(habits: { name: string; currentStreak: number; bestStreak: number; log: Record<string, string> }[], dates: string[]): string {
  if (habits.length === 0) return "No habits set up.";
  const header = `habit | current streak | best | ${dates[0]}→${dates[dates.length - 1]} (✓ done, ✗ not done, · unlogged)`;
  const lines = habits.map((h) => {
    const marks = dates.map((d) => (h.log[d] === "done" ? "✓" : h.log[d] === "missed" ? "✗" : "·")).join("");
    return `${h.name} | ${h.currentStreak} | ${h.bestStreak} | ${marks}`;
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
