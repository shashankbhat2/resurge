import { FieldValue } from "firebase-admin/firestore";
import { userDoc } from "../lib/firebase";
import { addDays, isValidTimeZone, localParts } from "../lib/time";

/*
 * Logging writes for MCP. Each one stores exactly the document shape the Android app writes
 * (core/data/.../{Meals,Workouts,Weight,Habits}Repository.kt) and passes the same checks as
 * firestore.rules. The rules use hasOnly() and closed `source` enums, and the app later edits
 * these documents, so we can't add a source like "mcp" or any extra field. MCP entries are stored
 * as "manual", like a hand-entered log.
 */

/** Activity ids from ActivityCatalog (core/model/.../Fitness.kt). */
export const ACTIVITY_IDS = [
  "run", "treadmill", "walk", "hike", "cycle", "indoor_cycle", "row", "elliptical", "stairs", "wheelchair",
  "skate", "ski", "paddle", "swim_pool", "swim_open", "weights", "bodyweight", "crossfit", "powerlifting",
  "hiit", "yoga", "pilates", "stretch", "dance", "martial_arts", "boxing", "football", "cricket", "basketball",
  "tennis", "badminton", "table_tennis", "volleyball", "golf", "climbing", "other", "jump_rope", "burpees",
  "pushups", "squats", "situps",
] as const;

export const MEAL_TYPES = ["breakfast", "lunch", "dinner", "snack"] as const;
export const MAX_MEAL_ITEMS = 20; // MealsRepository.MAX_ITEMS / rules
export const WATER_MAX_ML = 20000; // rules: validWaterDay

export interface MealItemIn { name: string; grams: number; kcal: number; protein: number; carbs: number; fat: number; fiber?: number }

/** Firestore operations, injectable for tests. Paths are relative to users/{uid}. */
export interface WriteStore {
  timezone(uid: string): Promise<string>;
  habitResetTime(uid: string): Promise<string>;
  create(uid: string, collection: string, data: Record<string, unknown>): Promise<string>;
  merge(uid: string, collection: string, id: string, data: Record<string, unknown>): Promise<void>;
  activeHabits(uid: string): Promise<{ id: string; name: string }[]>;
  /** Adds ml to a water day inside a transaction; returns the new total, or null if it would exceed max. */
  addWater(uid: string, date: string, ml: number, max: number): Promise<number | null>;
}

export const firestoreStore: WriteStore = {
  async timezone(uid) {
    const tz = (await userDoc(uid).get()).get("timezone");
    return isValidTimeZone(tz) ? (tz as string) : "UTC";
  },
  async habitResetTime(uid) {
    const t = (await userDoc(uid).get()).get("habitResetTime");
    return typeof t === "string" && /^\d{2}:\d{2}$/.test(t) ? t : "04:00";
  },
  async create(uid, collection, data) {
    const ref = userDoc(uid).collection(collection).doc();
    await ref.set({ ...data, updatedAt: FieldValue.serverTimestamp() });
    return ref.id;
  },
  async merge(uid, collection, id, data) {
    await userDoc(uid).collection(collection).doc(id).set({ ...data, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  },
  async activeHabits(uid) {
    const snap = await userDoc(uid).collection("habits").where("archived", "==", false).get();
    return snap.docs.map((d) => ({ id: d.id, name: String(d.get("name") ?? "") }));
  },
  async addWater(uid, date, ml, max) {
    const ref = userDoc(uid).collection("waterDays").doc(date);
    return ref.firestore.runTransaction(async (tx) => {
      const current = Number((await tx.get(ref)).get("ml") ?? 0);
      const next = current + ml;
      if (next < 0 || next > max) return null;
      tx.set(ref, { date, ml: next, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      return next;
    });
  },
};

/** Resolves an optional ISO time to epoch ms and the user's local date. */
async function when(store: WriteStore, uid: string, at: string | undefined, nowMs: number) {
  const ms = at ? Date.parse(at) : nowMs;
  if (!Number.isFinite(ms)) throw new WriteError("Invalid time; use ISO 8601 such as 2026-11-05T13:30:00+05:30.");
  if (ms > nowMs + 5 * 60_000) throw new WriteError("Time can't be in the future.");
  if (ms < nowMs - 366 * 86_400_000) throw new WriteError("Time is more than a year ago.");
  return { ms, localDate: localParts(await store.timezone(uid), new Date(ms)).date };
}

export class WriteError extends Error {}

export function createWriter(uid: string, store: WriteStore = firestoreStore, now: () => number = Date.now) {
  return {
    /** users/{uid}/meals/{auto}: same shape as MealsRepository.save (items carry nutrition per 100 g). */
    async logMeal(a: { title?: string; type: (typeof MEAL_TYPES)[number]; at?: string; items?: MealItemIn[];
      kcal?: number; protein?: number; carbs?: number; fat?: number; fiber?: number }) {
      const { ms, localDate } = await when(store, uid, a.at, now());
      let items: MealItemIn[];
      if (a.items && a.items.length > 0) {
        items = a.items;
      } else {
        if (a.kcal === undefined) throw new WriteError("Give items, or totals with at least kcal.");
        // Totals only: one 100 g item whose per-100 g values are the totals, so totals read back exactly.
        items = [{ name: (a.title ?? "Meal").trim() || "Meal", grams: 100, kcal: a.kcal, protein: a.protein ?? 0,
          carbs: a.carbs ?? 0, fat: a.fat ?? 0, fiber: a.fiber ?? 0 }];
      }
      if (items.length > MAX_MEAL_ITEMS) throw new WriteError(`At most ${MAX_MEAL_ITEMS} items.`);
      const title = ((a.title ?? "").trim() || items[0].name).slice(0, 80);
      const id = await store.create(uid, "meals", {
        type: a.type,
        loggedAtMs: ms,
        localDate,
        source: "manual",
        title,
        items: items.map((i) => ({
          name: i.name.slice(0, 60), grams: i.grams, kcal: i.kcal, protein: i.protein, carbs: i.carbs,
          fat: i.fat, fiber: i.fiber ?? 0, confidence: null,
        })),
      });
      const total = (k: keyof MealItemIn) => Math.round(items.reduce((s, i) => s + (Number(i[k] ?? 0) * i.grams) / 100, 0));
      return { id, path: `users/${uid}/meals/${id}`, localDate, title, kcal: total("kcal"), protein: total("protein"), carbs: total("carbs"), fat: total("fat") };
    },

    /** users/{uid}/workouts/{auto}: same shape as WorkoutsRepository.save with source "manual". */
    async logWorkout(a: { activityId: (typeof ACTIVITY_IDS)[number]; start?: string; durationMin: number; title?: string; notes?: string;
      metrics?: { distanceM?: number; elevationM?: number; avgHr?: number; kcal?: number; laps?: number; poolLengthM?: number;
        reps?: number; rpe?: number; exercises?: { name: string; sets: { reps: number; weightKg?: number }[] }[] } }) {
      const { ms, localDate } = await when(store, uid, a.start, now());
      const metrics: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(a.metrics ?? {})) {
        if (v === undefined) continue;
        if (k === "exercises") {
          const ex = v as { name: string; sets: { reps: number; weightKg?: number }[] }[];
          if (ex.length) metrics.exercises = ex.map((e) => ({ name: e.name, sets: e.sets.map((s) => ({ reps: s.reps, weightKg: s.weightKg ?? null })) }));
        } else metrics[k] = v;
      }
      const id = await store.create(uid, "workouts", {
        source: "manual",
        activityId: a.activityId,
        title: a.title?.trim() || null,
        startMs: ms,
        endMs: ms + a.durationMin * 60_000,
        localDate,
        metrics,
        notes: a.notes?.trim() || null,
        deleted: false,
      });
      return { id, path: `users/${uid}/workouts/${id}`, localDate };
    },

    /** users/{uid}/weights/{auto}: same shape as a manual WeightRepository.save (no Health Connect mirror). */
    async logWeight(a: { kg: number; at?: string; note?: string }) {
      const { ms, localDate } = await when(store, uid, a.at, now());
      const id = await store.create(uid, "weights", {
        kg: Math.round(a.kg * 100) / 100,
        atMs: ms,
        localDate,
        note: a.note?.trim().slice(0, 120) || null,
        source: "manual",
      });
      return { id, path: `users/${uid}/weights/${id}`, localDate };
    },

    /** users/{uid}/waterDays/{date}: like MealsRepository.addWater, bounded to the rules' 0..20000 ml. */
    async logWater(a: { ml: number; date?: string }) {
      const today = localParts(await store.timezone(uid), new Date(now())).date;
      const date = a.date ?? today;
      if (date > today || date < addDays(today, -7)) throw new WriteError("Water can be logged for the last 7 days only.");
      const total = await store.addWater(uid, date, a.ml, WATER_MAX_ML);
      if (total === null) throw new WriteError(`That day's water would go outside 0–${WATER_MAX_ML} ml.`);
      return { path: `users/${uid}/waterDays/${date}`, date, totalMl: total };
    },

    /**
     * users/{uid}/habitDays/{date}: like HabitsRepository.setStatus. The habit is matched by id or
     * name (case-insensitive; private habits by cover name). Dates are limited to the current habit
     * day or the one before (yesterday's grace), inside the rules' window.
     */
    async setHabitStatus(a: { habit: string; status: "done" | "missed" | "clear"; date?: string }) {
      const [tz, reset, habits] = await Promise.all([store.timezone(uid), store.habitResetTime(uid), store.activeHabits(uid)]);
      const p = localParts(tz, new Date(now()));
      const habitDay = `${String(p.hour).padStart(2, "0")}:${String(p.minute).padStart(2, "0")}` < reset ? addDays(p.date, -1) : p.date;
      const date = a.date ?? habitDay;
      if (date !== habitDay && date !== addDays(habitDay, -1)) throw new WriteError(`Only ${habitDay} or ${addDays(habitDay, -1)} can be changed.`);
      const key = a.habit.trim().toLowerCase();
      const match = habits.filter((h) => h.id === a.habit || h.name.trim().toLowerCase() === key);
      if (match.length !== 1) {
        throw new WriteError(match.length ? "More than one habit has that name; pass its id." :
          `No active habit "${a.habit}". Active: ${habits.map((h) => h.name).join(", ") || "none"}.`);
      }
      const h = match[0];
      await store.merge(uid, "habitDays", date, { date, entries: { [h.id]: a.status === "clear" ? FieldValue.delete() : a.status } });
      return { path: `users/${uid}/habitDays/${date}`, date, habitId: h.id, habit: h.name, status: a.status };
    },
  };
}
