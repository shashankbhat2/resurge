import type { MealSummary } from "./data";
import { dayBand, GLYCEMIC_NOTE, type GlycemicEstimate } from "./glycemic";
import { addDays } from "./time";
import type { ActivityEvent, DailySummary, UserProfile } from "./types";
import { goalProgress, trend, trendOn, type StoredWeightGoal, type WeighInRow } from "./weight";

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
    "date | steps | km | kcal total/active | sleep in bed/asleep (deep/rem/light/awake min) | exercise min (sessions: type start min) | HR rest/avg/min-max | weight kg | cardio pts";
  const lines = [...rows]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((r) => {
      const st = r.sleepStages;
      const stages = st ? ` (${n(st.deep)}/${n(st.rem)}/${n(st.light)}/${n(st.awake)})` : "";
      const sleep = r.sleepMin == null && r.asleepMin == null ? "-" : `${hours(r.sleepMin)}/${hours(r.asleepMin)}${stages}`;
      return [
        r.date,
        n(r.steps),
        r.distanceM == null ? "-" : (r.distanceM / 1000).toFixed(1),
        `${n(r.kcalTotal)}/${n(r.kcalActive)}`,
        sleep,
        `${n(r.exerciseMin)}${exerciseDetail(r)}`,
        `${n(r.restingHr)}/${n(r.hrAvg)}/${n(r.hrMin)}-${n(r.hrMax)}`,
        n(r.weightKg, 1),
        r.cardioPoints == null ? "-" : `${n(r.cardioPoints)}${r.cardioEstimated ? " (incl. estimates)" : ""}`,
      ].join(" | ");
    });
  return [header, ...lines].join("\n");
}

/** Per-session detail when the app sent it; older summaries only carry the day's activity types. */
function exerciseDetail(r: DailySummary): string {
  if (r.exerciseSessions?.length) {
    const sessions = r.exerciseSessions.map((x) =>
      [x.title ? `${x.type} "${x.title}"` : x.type, x.start, n(x.durationMin)].filter(Boolean).join(" "));
    return ` (${sessions.join(", ")})`;
  }
  return r.exerciseTypes?.length ? ` (${r.exerciseTypes.join(", ")})` : "";
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

/** Micronutrient labels and units, as in the app's Micro enum (core/model/.../Food.kt). */
export const MICROS: Record<string, { label: string; unit: string }> = {
  sugar: { label: "sugar", unit: "g" }, satFat: { label: "saturated fat", unit: "g" }, sodium: { label: "sodium", unit: "mg" },
  cholesterol: { label: "cholesterol", unit: "mg" }, potassium: { label: "potassium", unit: "mg" }, calcium: { label: "calcium", unit: "mg" },
  iron: { label: "iron", unit: "mg" }, magnesium: { label: "magnesium", unit: "mg" }, zinc: { label: "zinc", unit: "mg" },
  vitA: { label: "vitamin A", unit: "µg" }, vitC: { label: "vitamin C", unit: "mg" }, vitD: { label: "vitamin D", unit: "µg" },
  folate: { label: "folate", unit: "µg" },
};

const glText = (g: GlycemicEstimate) =>
  g.gi === null ? "predicted GL ~0 (too few carbs)" : `predicted GI ~${g.gi} / GL ~${g.gl} (${g.band}${g.basis === "predicted" ? "" : `, ${g.basis === "assistant" ? "GI from assistant" : "partly from assistant"}`})`;

/**
 * Logged meals and water by day, newest first, with daily totals, fibre, micronutrients and the
 * PREDICTED glycaemic index / load (see lib/glycemic.ts).
 */
export function formatMeals(meals: MealSummary[], water: Record<string, number> = {}): string {
  const days = [...new Set([...meals.map((m) => m.date), ...Object.keys(water).filter((d) => water[d] > 0)])].sort().reverse();
  if (days.length === 0) return "No meals or water logged in this period.";
  const lines: string[] = [];
  for (const date of days) {
    const list = meals.filter((m) => m.date === date);
    const ml = water[date];
    const waterText = ml ? ` · water ${(ml / 1000).toFixed(2)} L` : "";
    if (list.length === 0) {
      lines.push(`${date}: no meals logged${waterText}`);
      continue;
    }
    const t = list.reduce((a, m) => ({ kcal: a.kcal + m.kcal, p: a.p + m.protein, c: a.c + m.carbs, f: a.f + m.fat, fb: a.fb + m.fiber }), { kcal: 0, p: 0, c: 0, f: 0, fb: 0 });
    const gl = list.reduce((a, m) => a + m.glycemic.gl, 0);
    lines.push(`${date}: ${t.kcal} kcal, protein ${t.p} g, carbs ${t.c} g, fat ${t.f} g, fibre ${t.fb} g${waterText} · predicted day GL ~${gl} (${dayBand(gl)})`);
    const micros: Record<string, number> = {};
    for (const m of list) for (const [k, v] of Object.entries(m.micros)) micros[k] = (micros[k] ?? 0) + v;
    const microText = Object.keys(MICROS).filter((k) => micros[k] !== undefined)
      .map((k) => `${MICROS[k].label} ${Math.round(micros[k] * 10) / 10} ${MICROS[k].unit}`).join(", ");
    if (microText) lines.push(`  micronutrients: ${microText}`);
    for (const m of list) {
      lines.push(`  - ${m.type}${m.time ? ` ${m.time}` : ""}: ${m.title} — ${m.kcal} kcal, P${m.protein} C${m.carbs} F${m.fat} fibre ${m.fiber} g, ${glText(m.glycemic)} (${m.items.join(", ")})`);
    }
  }
  if (meals.length > 0) lines.push(`Note: ${GLYCEMIC_NOTE}`);
  return lines.join("\n");
}

/** Weigh-ins in the window, the smoothed trend the app shows, and the goal. */
export function formatWeight(rows: WeighInRow[], goal: StoredWeightGoal | null, from: string, today: string): string {
  const points = trend(rows, today);
  const now = trendOn(points, today);
  const weekAgo = trendOn(points, addDays(today, -7));
  const shown = rows.filter((r) => r.localDate >= from);
  const out: string[] = [];
  if (now === null) out.push("No weigh-ins yet.");
  else {
    out.push(`Trend weight ${now.toFixed(1)} kg on ${today}` + (weekAgo !== null && rows.some((r) => r.localDate <= addDays(today, -7)) ? `, ${now - weekAgo >= 0 ? "+" : ""}${(now - weekAgo).toFixed(1)} kg over 7 days` : "") + ".");
    out.push(shown.length ? `Weigh-ins since ${from} (kg):` : `No weigh-ins since ${from}.`);
    for (const r of shown) out.push(`- ${r.localDate} ${r.kg.toFixed(1)}${r.source && r.source !== "manual" ? ` (${r.source.replace("_", " ")})` : ""}${r.note ? ` — ${r.note}` : ""}`);
  }
  if (!goal) out.push("Goal: none set.");
  else if (goal.mode === "maintain") out.push(`Goal: maintain ${goal.goalKg.toFixed(1)} kg (since ${goal.startDate}).`);
  else {
    const pct = now === null ? null : Math.round(goalProgress(goal, now) * 100);
    out.push(`Goal: ${goal.goalKg.toFixed(1)} kg from ${goal.startKg.toFixed(1)} kg (started ${goal.startDate}) at ${goal.paceKgPerWeek} kg/week` +
      (pct === null ? "" : `, ${pct}% of the way`) + (goal.reachedOn ? `, reached on ${goal.reachedOn}` : "") + ".");
  }
  return out.join("\n");
}

/** Settled days of the day streak, oldest first. */
export function formatDayResults(rows: { date: string; met: boolean; done: number; total: number }[], dayGoals: unknown): string {
  const goals = dayGoals && typeof dayGoals === "object" ? JSON.stringify(dayGoals) : "app defaults";
  if (rows.length === 0) return `No settled days in this period. Day goals: ${goals}.`;
  const met = rows.filter((r) => r.met).length;
  return [`Days met ${met} of ${rows.length}. Day goals: ${goals}.`, ...rows.map((r) => `- ${r.date} ${r.met ? "met" : "missed"} (${r.done}/${r.total} goals)`)].join("\n");
}

export function formatCoachHistory(threads: { title: string; updatedAt: string | null; turns: { role: string; text: string }[] }[]): string {
  if (threads.length === 0) return "No coach chats yet.";
  return threads.map((t) => [
    `## ${t.title}${t.updatedAt ? ` (${t.updatedAt.slice(0, 10)})` : ""}`,
    ...t.turns.map((x) => `${x.role === "user" ? "User" : "Coach"}: ${x.text.length > 600 ? `${x.text.slice(0, 597)}…` : x.text}`),
  ].join("\n")).join("\n\n");
}

export function formatProgress(p: { photos: { date: string; uploaded: boolean }[]; reels: { kind: string; from: string; to: string; frames: number; watched: boolean; milestoneKg: number | null }[]; settings: Record<string, unknown> }): string {
  const out = ["Progress photos are end-to-end encrypted on the phone; only their dates are readable here."];
  out.push(p.photos.length ? `Photos: ${p.photos.length}, from ${p.photos[0].date} to ${p.photos[p.photos.length - 1].date}. Dates: ${p.photos.map((x) => x.date).join(", ")}.` : "Photos: none.");
  out.push(p.reels.length ? `Reels (newest first):\n${p.reels.map((r) => `- ${r.kind} ${r.from}→${r.to}, ${r.frames} frames${r.milestoneKg !== null ? `, milestone ${r.milestoneKg} kg` : ""}${r.watched ? "" : ", not watched"}`).join("\n")}` : "Reels: none.");
  out.push(`Reel settings: ${Object.keys(p.settings).length ? JSON.stringify(p.settings) : "app defaults"}.`);
  return out.join("\n");
}

/** Every profile field the app stores, for get_profile (timestamps as ISO strings). */
export function profileDetails(raw: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(raw)) {
    out[k] = v && typeof v === "object" && typeof (v as { toDate?: unknown }).toDate === "function" ? (v as { toDate: () => Date }).toDate().toISOString() : v;
  }
  return out;
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
