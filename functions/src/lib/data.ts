import { db, userDoc } from "./firebase";
import type { ActivityEvent, DailySummary, DayResult, DayScore, StoredPlan, UserProfile } from "./types";

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

/** The plan the person follows, or null without one. */
export async function loadPlan(uid: string, planId: string | null | undefined): Promise<StoredPlan | null> {
  if (!planId) return null;
  const snap = await userDoc(uid).collection("plans").doc(planId).get();
  return snap.exists ? (snap.data() as StoredPlan) : null;
}

/** Streak results with from <= date <= to. */
export async function loadDayResults(uid: string, from: string, to: string): Promise<DayResult[]> {
  const snap = await userDoc(uid).collection("dayResults").where("date", ">=", from).where("date", "<=", to).get();
  return snap.docs.map((d) => d.data() as DayResult);
}

/** Daily scores with from <= date <= to. */
export async function loadDayScores(uid: string, from: string, to: string): Promise<DayScore[]> {
  const snap = await userDoc(uid).collection("dayScores").where("date", ">=", from).where("date", "<=", to).get();
  return snap.docs.map((d) => d.data() as DayScore);
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
  category?: string;
  suggestion?: string;
  highlights?: { label: string; value: string }[];
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

export async function loadInsight(uid: string, id: string): Promise<StoredInsight | null> {
  const snap = await userDoc(uid).collection("insights").doc(id).get();
  return snap.exists ? (snap.data() as StoredInsight) : null;
}

/** One insight in full, for a chat about it. */
export function formatInsight(i: StoredInsight): string {
  const lines = [`Title: ${i.title ?? i.headline ?? ""}`, `Written: ${i.createdAt.toDate().toISOString().slice(0, 10)} (${i.kind})`];
  const body = i.body ?? i.summary;
  if (body) lines.push(body);
  if (i.highlights?.length) lines.push(`Numbers: ${i.highlights.map((h) => `${h.label} ${h.value}`).join("; ")}`);
  if (i.suggestion) lines.push(`Suggested: ${i.suggestion}`);
  return lines.join("\n");
}

export function formatInsightList(items: StoredInsight[]): string {
  if (items.length === 0) return "None.";
  return items
    .map((i) => `- ${i.createdAt.toDate().toISOString().slice(0, 10)} [${i.kind}] ${i.title ?? i.headline ?? ""}`)
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
  kcal: number;
  protein: number;
  carbs: number;
  fat: number;
  items: string[];
}

/** Logged meals from [from] (yyyy-MM-dd), with totals computed from per-100 g item values. */
export async function loadMeals(uid: string, from: string, limit = 120): Promise<MealSummary[]> {
  const snap = await userDoc(uid).collection("meals").where("localDate", ">=", from).limit(limit).get();
  return snap.docs
    .map((d) => {
      const items = (d.get("items") ?? []) as Array<Record<string, unknown>>;
      const sum = (k: string) =>
        Math.round(items.reduce((acc, i) => acc + (Number(i[k] ?? 0) * Number(i.grams ?? 0)) / 100, 0));
      return {
        date: String(d.get("localDate") ?? ""),
        type: String(d.get("type") ?? "snack"),
        title: String(d.get("title") ?? "Meal"),
        kcal: sum("kcal"),
        protein: sum("protein"),
        carbs: sum("carbs"),
        fat: sum("fat"),
        items: items.map((i) => `${String(i.name ?? "")} ${Math.round(Number(i.grams ?? 0))}g`),
      };
    })
    .sort((a, b) => (a.date < b.date ? 1 : -1));
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
