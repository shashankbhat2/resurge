import { db, userDoc } from "./firebase";
import type { ActivityEvent, DailySummary, UserProfile } from "./types";

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

export function formatInsightList(items: StoredInsight[]): string {
  if (items.length === 0) return "None.";
  return items
    .map((i) => `- ${i.createdAt.toDate().toISOString().slice(0, 10)} [${i.kind}] ${i.title ?? i.headline ?? ""}`)
    .join("\n");
}

export interface HabitSummary {
  name: string;
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
      currentStreak: Number(h.get("currentStreak") ?? 0),
      bestStreak: Number(h.get("bestStreak") ?? 0),
      log,
    };
  });
}

export { db };
