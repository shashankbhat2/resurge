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
  return `Goals: ${goals}. Daily targets: ${kcal} kcal eaten, ${water} ml water.`;
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

export function formatEvents(events: ActivityEvent[]): string {
  if (events.length === 0) return "None.";
  return [...events].sort((a, b) => a.startMs - b.startMs).map((e) => `- ${formatEvent(e)}`).join("\n");
}
