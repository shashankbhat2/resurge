import { db, userDoc } from "./firebase";
import { mealGlycemic, type GlycemicEstimate } from "./glycemic";
import { localParts } from "./time";
import type { ActivityEvent, DailySummary, UserProfile } from "./types";
import type { StoredWeightGoal, WeighInRow } from "./weight";

/** Firestore reads used to build prompts and serve tools. All scoped to one user. */

export async function loadProfile(uid: string): Promise<UserProfile> {
  return ((await userDoc(uid).get()).data() ?? {}) as UserProfile;
}

/** Daily summaries with from <= date <= to (yyyy-MM-dd; doc IDs are dates). */
export async function loadSummaries(uid: string, from: string, to: string): Promise<DailySummary[]> {
  const snap = await userDoc(uid)
    .collection("dailySummaries")
    .where("date", ">=", from)
    .where("date", "<=", to)
    .get();
  return snap.docs.map((d) => d.data() as DailySummary);
}

export async function loadEvents(
  uid: string,
  sinceMs: number,
  type?: ActivityEvent["type"],
  limit = 50,
): Promise<(ActivityEvent & { id: string })[]> {
  let q = userDoc(uid).collection("events").where("endMs", ">=", sinceMs).orderBy("endMs", "desc").limit(limit);
  if (type) q = q.where("type", "==", type);
  const snap = await q.get();
  return snap.docs.map((d) => ({ id: d.id, ...(d.data() as ActivityEvent) }));
}

export interface StoredInsight {
  kind: "activity" | "sleep" | "weekly";
  title?: string;
  headline?: string;
  body?: string;
  summary?: string;
  createdAt: FirebaseFirestore.Timestamp;
}

export async function loadRecentInsights(uid: string, sinceMs: number, limit = 20): Promise<StoredInsight[]> {
  const snap = await userDoc(uid)
    .collection("insights")
    .where("createdAt", ">=", new Date(sinceMs))
    .orderBy("createdAt", "desc")
    .limit(limit)
    .get();
  return snap.docs.map((d) => d.data() as StoredInsight);
}

/** Titles only by default; [full] adds each insight's text (body for activity/sleep, summary for weekly). */
export function formatInsightList(items: StoredInsight[], full = false): string {
  if (items.length === 0) return "None.";
  return items
    .map((i) => {
      const head = `- ${i.createdAt.toDate().toISOString().slice(0, 10)} [${i.kind}] ${i.title ?? i.headline ?? ""}`;
      const text = i.body ?? i.summary;
      return full && text ? `${head}\n  ${text.replace(/\n+/g, " ")}` : head;
    })
    .join("\n");
}

export interface HabitSummary {
  /** For a private habit, its cover name: the real one is encrypted on the phone and never sent. */
  name: string;
  private: boolean;
  currentStreak: number;
  bestStreak: number;
  /** Habit day (yyyy-MM-dd) -> "done" | "missed" over the requested window. */
  log: Record<string, string>;
}

/** Active habits with cached streaks (written by the app) and their recent check-ins. */
export async function loadHabits(uid: string, from: string): Promise<HabitSummary[]> {
  const [habits, days] = await Promise.all([
    userDoc(uid).collection("habits").where("archived", "==", false).get(),
    userDoc(uid).collection("habitDays").where("date", ">=", from).get(),
  ]);
  return habits.docs.map((h) => {
    const log: Record<string, string> = {};
    for (const d of days.docs) {
      const status = (d.get("entries") ?? {})[h.id];
      if (typeof status === "string") log[d.id] = status;
    }
    return {
      name: String(h.get("name") ?? ""),
      private: h.get("private") === true,
      currentStreak: Number(h.get("currentStreak") ?? 0),
      bestStreak: Number(h.get("bestStreak") ?? 0),
      log,
    };
  });
}

export interface MealSummary {
  date: string;
  type: string;
  title: string;
  /** Local time eaten, HH:mm (empty when unknown). */
  time: string;
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  fiber: number;
  /** Micronutrient totals keyed by the app's wire names (sugar, sodium, iron, ...); only those reported. */
  micros: Record<string, number>;
  items: string[];
  glycemic: GlycemicEstimate;
}

/** Logged meals from [from] (yyyy-MM-dd), with totals computed from per-100 g item values. */
export async function loadMeals(uid: string, from: string, limit = 120, timeZone = "UTC"): Promise<MealSummary[]> {
  const snap = await userDoc(uid).collection("meals").where("localDate", ">=", from).limit(limit).get();
  return snap.docs
    .map((d) => mealSummary(d.data(), timeZone))
    .sort((a, b) => (a.date === b.date ? (a.time < b.time ? 1 : -1) : a.date < b.date ? 1 : -1));
}

/** Pure: one stored meal document to its summary (exported for tests). */
export function mealSummary(data: Record<string, unknown>, timeZone = "UTC"): MealSummary {
  const items = (data.items ?? []) as Array<Record<string, unknown>>;
  const num = (v: unknown) => Number(v ?? 0) || 0;
  const sum = (k: string) => Math.round(items.reduce((acc, i) => acc + (num(i[k]) * num(i.grams)) / 100, 0));
  const micros: Record<string, number> = {};
  for (const i of items) {
    for (const [k, v] of Object.entries((i.micros ?? {}) as Record<string, unknown>)) {
      if (typeof v === "number") micros[k] = (micros[k] ?? 0) + (v * num(i.grams)) / 100;
    }
  }
  for (const k of Object.keys(micros)) micros[k] = Math.round(micros[k] * 10) / 10;
  const lp = typeof data.loggedAtMs === "number" ? localParts(timeZone, new Date(data.loggedAtMs)) : null;
  return {
    date: String(data.localDate ?? ""),
    type: String(data.type ?? "snack"),
    title: String(data.title ?? "Meal"),
    time: lp ? `${String(lp.hour).padStart(2, "0")}:${String(lp.minute).padStart(2, "0")}` : "",
    kcal: sum("kcal"),
    protein: sum("protein"),
    carbs: sum("carbs"),
    fat: sum("fat"),
    fiber: sum("fiber"),
    micros,
    items: items.map((i) => `${String(i.name ?? "")} ${Math.round(num(i.grams))}g${typeof i.gi === "number" ? ` (GI ${i.gi} from assistant)` : ""}`),
    glycemic: mealGlycemic(items.map((i) => ({
      grams: num(i.grams), carbs: num(i.carbs), protein: num(i.protein), fat: num(i.fat), fiber: num(i.fiber),
      gi: typeof i.gi === "number" ? i.gi : null,
    }))),
  };
}

/** Water drunk per day (ml) from [from]. */
export async function loadWater(uid: string, from: string): Promise<Record<string, number>> {
  const snap = await userDoc(uid).collection("waterDays").where("date", ">=", from).get();
  return Object.fromEntries(snap.docs.map((d) => [String(d.get("date") ?? d.id), Number(d.get("ml") ?? 0)]));
}

/** Every weigh-in, oldest first (the trend needs the full history). */
export async function loadWeighIns(uid: string, limit = 3000): Promise<WeighInRow[]> {
  const snap = await userDoc(uid).collection("weights").orderBy("atMs", "asc").limit(limit).get();
  return snap.docs
    .map((d) => ({
      kg: Number(d.get("kg")), atMs: Number(d.get("atMs")), localDate: String(d.get("localDate") ?? ""),
      source: (d.get("source") as string | undefined) ?? "manual", note: (d.get("note") as string | undefined) ?? null,
    }))
    .filter((w) => Number.isFinite(w.kg) && Number.isFinite(w.atMs) && /^\d{4}-\d{2}-\d{2}$/.test(w.localDate));
}

export async function loadWeightGoal(uid: string): Promise<StoredWeightGoal | null> {
  const d = await userDoc(uid).collection("weightGoal").doc("current").get();
  if (!d.exists) return null;
  const startKg = d.get("startKg"), goalKg = d.get("goalKg"), startDate = d.get("startDate");
  if (typeof startKg !== "number" || typeof goalKg !== "number" || typeof startDate !== "string") return null;
  return {
    startKg, goalKg, startDate,
    paceKgPerWeek: typeof d.get("paceKgPerWeek") === "number" ? d.get("paceKgPerWeek") : 0.5,
    mode: d.get("mode") === "maintain" ? "maintain" : "change",
    milestonesCelebrated: Number(d.get("milestonesCelebrated") ?? 0),
    reachedOn: (d.get("reachedOn") as string | undefined) ?? null,
  };
}

/** Settled days for the day streak (dayResults/{date}: met, done, total) from [from]. */
export async function loadDayResults(uid: string, from: string): Promise<{ date: string; met: boolean; done: number; total: number }[]> {
  const snap = await userDoc(uid).collection("dayResults").where("date", ">=", from).get();
  return snap.docs
    .map((d) => ({ date: String(d.get("date") ?? d.id), met: d.get("met") === true, done: Number(d.get("done") ?? 0), total: Number(d.get("total") ?? 0) }))
    .sort((a, b) => a.date.localeCompare(b.date));
}

export interface CoachThreadSummary {
  title: string;
  updatedAt: string | null;
  turns: { role: string; text: string }[];
}

/** The latest coach chats with their visible messages (tool calls and hidden notices left out). */
export async function loadCoachHistory(uid: string, threads = 3, turnsPerThread = 40): Promise<CoachThreadSummary[]> {
  const snap = await userDoc(uid).collection("coachThreads").orderBy("updatedAt", "desc").limit(threads).get();
  return Promise.all(snap.docs.map(async (t) => {
    const turns = await t.ref.collection("turns").orderBy("seq", "desc").limit(turnsPerThread).get();
    const updated = t.get("updatedAt");
    return {
      title: String(t.get("title") ?? "Chat"),
      updatedAt: updated && typeof updated.toDate === "function" ? updated.toDate().toISOString() : null,
      turns: turns.docs
        .map((d) => d.data())
        .filter((d) => d.excluded !== true && typeof d.display === "string" && (d.role === "user" || d.role === "assistant"))
        .reverse()
        .map((d) => ({ role: String(d.role), text: String(d.display) })),
    };
  }));
}

export interface ProgressSummary {
  photos: { date: string; uploaded: boolean }[];
  reels: { kind: string; from: string; to: string; frames: number; watched: boolean; milestoneKg: number | null }[];
  settings: Record<string, unknown>;
}

/** Progress photo and reel metadata. The photos themselves are end-to-end encrypted and never read. */
export async function loadProgress(uid: string): Promise<ProgressSummary> {
  const [photos, reels, settings] = await Promise.all([
    userDoc(uid).collection("progressPhotos").orderBy("takenAtMs", "asc").limit(500).get(),
    userDoc(uid).collection("progressReels").orderBy("createdAtMs", "desc").limit(50).get(),
    userDoc(uid).collection("progressSettings").doc("current").get(),
  ]);
  const s = settings.data() ?? {};
  delete s.updatedAt;
  return {
    photos: photos.docs.map((d) => ({ date: String(d.get("localDate") ?? ""), uploaded: d.get("uploaded") === true })),
    reels: reels.docs.map((d) => ({
      kind: String(d.get("kind") ?? "regular"), from: String(d.get("from") ?? ""), to: String(d.get("to") ?? ""),
      frames: ((d.get("frameIds") ?? []) as unknown[]).length, watched: d.get("watched") === true,
      milestoneKg: typeof d.get("milestoneKg") === "number" ? d.get("milestoneKg") : null,
    })),
    settings: s,
  };
}

/** The whole profile document as stored, for get_profile. */
export async function loadProfileRaw(uid: string): Promise<Record<string, unknown>> {
  return (await userDoc(uid).get()).data() ?? {};
}

export interface WorkoutLog {
  date: string;
  activity: string;
  title: string | null;
  durationMin: number;
  source: string;
  deleted: boolean;
  metrics: Record<string, unknown>;
  notes: string | null;
}

/** Workouts logged or edited in the app (Health Connect sessions themselves arrive as events). */
export async function loadWorkoutLogs(uid: string, sinceMs: number, limit = 60): Promise<WorkoutLog[]> {
  const snap = await userDoc(uid).collection("workouts").where("startMs", ">=", sinceMs).limit(limit).get();
  return snap.docs
    .map((d) => ({
      date: String(d.get("localDate") ?? ""),
      activity: String(d.get("activityId") ?? "other"),
      title: (d.get("title") as string | undefined) ?? null,
      durationMin: Math.round((Number(d.get("endMs") ?? 0) - Number(d.get("startMs") ?? 0)) / 60000),
      source: String(d.get("source") ?? "manual"),
      deleted: d.get("deleted") === true,
      metrics: (d.get("metrics") ?? {}) as Record<string, unknown>,
      notes: (d.get("notes") as string | undefined) ?? null,
    }))
    .sort((a, b) => (a.date < b.date ? 1 : -1));
}

export { db };
