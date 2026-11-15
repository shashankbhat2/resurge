import { addDays } from "./time";

/*
 * Weight trend, ported from the app's WeightMath.trend (core/model/.../Weight.kt) so MCP reports
 * the same trend weight the app shows: daily averages smoothed with an exponential moving average
 * whose weight grows with the gap since the last weigh-in.
 */

export const SMOOTHING = 0.1;

export interface WeighInRow {
  kg: number;
  atMs: number;
  /** yyyy-MM-dd in the user's zone when it was logged. */
  localDate: string;
  source?: string | null;
  note?: string | null;
}

export interface TrendPoint { date: string; kg: number }

const daysBetween = (a: string, b: string) => Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);

/** One trend point per day from the first weigh-in to [through] (inclusive); empty without weigh-ins. */
export function trend(rows: WeighInRow[], through: string): TrendPoint[] {
  if (rows.length === 0) return [];
  const byDay = new Map<string, number[]>();
  for (const r of rows) byDay.set(r.localDate, [...(byDay.get(r.localDate) ?? []), r.kg]);
  const avg = new Map([...byDay].map(([d, v]) => [d, v.reduce((s, x) => s + x, 0) / v.length]));
  const days = [...avg.keys()].sort();
  const first = days[0];
  const end = [through, days[days.length - 1]].sort()[1];
  let t = avg.get(first)!;
  let n = 1;
  let lastDay = first;
  const out: TrendPoint[] = [];
  for (let d = first; d <= end; d = addDays(d, 1)) {
    const kg = avg.get(d);
    if (d !== first && kg !== undefined) {
      n++;
      const alpha = Math.max(1 / n, 1 - Math.pow(1 - SMOOTHING, daysBetween(lastDay, d)));
      t += alpha * (kg - t);
      lastDay = d;
    }
    out.push({ date: d, kg: t });
  }
  return out;
}

/** Trend on [date]: the last point on or before it, else the first one. */
export function trendOn(points: TrendPoint[], date: string): number | null {
  let hit: number | null = null;
  for (const p of points) if (p.date <= date) hit = p.kg;
  return hit ?? points[0]?.kg ?? null;
}

export interface StoredWeightGoal {
  startKg: number;
  startDate: string;
  goalKg: number;
  paceKgPerWeek: number;
  mode: "change" | "maintain";
  milestonesCelebrated: number;
  reachedOn: string | null;
}

/** Progress toward the goal, 0-1 (like WeightGoal.progress). */
export function goalProgress(g: StoredWeightGoal, currentKg: number): number {
  const total = Math.abs(g.startKg - g.goalKg);
  if (total === 0) return 1;
  const done = g.goalKg < g.startKg ? g.startKg - currentKg : currentKg - g.startKg;
  return Math.min(1, Math.max(0, done / total));
}
