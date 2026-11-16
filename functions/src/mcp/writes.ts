import { FieldValue } from "firebase-admin/firestore";
import { userDoc } from "../lib/firebase";
import { loadWeighIns, loadWeightGoal } from "../lib/data";
import { addDays, isValidTimeZone, localParts } from "../lib/time";
import { goalProgress, trend, trendOn, type StoredWeightGoal, type WeighInRow } from "../lib/weight";

/*
 * Logging writes for MCP. Each one stores exactly the document shape the Android app writes
 * (core/data/.../{Meals,Habits}Repository.kt) and passes the same checks as
 * firestore.rules. The rules use hasOnly() and closed `source` enums, and the app later edits
 * these documents, so we can't add a source like "mcp" or any extra field. MCP entries are stored
 * as "manual", like a hand-entered log.
 */

export const MEAL_TYPES = ["breakfast", "lunch", "dinner", "snack"] as const;
export const MAX_MEAL_ITEMS = 20; // MealsRepository.MAX_ITEMS / rules
export const WATER_MAX_ML = 20000; // rules: validWaterDay

/** Nutrition targets on users/{uid}.targets (UserRepository / Targets.kt; rules: validProfile). */
export const TARGET_DEFAULTS = { calories: 2000, waterMl: 2500, sleepMinutes: 7 * 60, steps: 10_000 };
export type StoredTargets = typeof TARGET_DEFAULTS;

/** Fibre guide the app derives from calories (14 g per 1,000 kcal). Not stored. */
export function fiberFor(calories: number) {
  return Math.round((calories * 14) / 1000);
}

/** Macro targets the app derives from calories (MacroTargets.from: 30% protein, 40% carbs, 30% fat). Not stored. */
export function macrosFor(calories: number) {
  return { protein: Math.round((calories * 0.3) / 4), carbs: Math.round((calories * 0.4) / 4), fat: Math.round((calories * 0.3) / 9) };
}

export interface MealItemIn { name: string; grams: number; kcal: number; protein: number; carbs: number; fat: number; fiber?: number; gi?: number }

/** Profile enums, as the app stores them (Goal names, UnitSystem / CoachStyle wire values). */
export const GOALS = ["LOSE_WEIGHT", "BUILD_MUSCLE", "SLEEP_BETTER", "MOVE_MORE", "EAT_BETTER", "HYDRATE"] as const;
export const UNITS = ["metric", "imperial"] as const;
export const COACH_STYLES = ["friend", "commander"] as const;
export interface ProfilePatch { displayName?: string; goals?: (typeof GOALS)[number][]; units?: (typeof UNITS)[number]; coachStyle?: (typeof COACH_STYLES)[number] }

/** Firestore operations, injectable for tests. Paths are relative to users/{uid}. */
export interface WriteStore {
  timezone(uid: string): Promise<string>;
  habitResetTime(uid: string): Promise<string>;
  create(uid: string, collection: string, data: Record<string, unknown>): Promise<string>;
  get(uid: string, collection: string, id: string): Promise<Record<string, unknown> | null>;
  merge(uid: string, collection: string, id: string, data: Record<string, unknown>): Promise<void>;
  remove(uid: string, collection: string, id: string): Promise<void>;
  activeHabits(uid: string): Promise<{ id: string; name: string }[]>;
  /** Current targets with app defaults filled in. */
  targets(uid: string): Promise<StoredTargets>;
  /** Merges a patch into targets in a transaction, writing the full map as the app does; returns it. */
  updateTargets(uid: string, patch: Partial<StoredTargets>): Promise<StoredTargets>;
  /** Adds ml to a water day inside a transaction; returns the new total, or null if it would exceed max. */
  addWater(uid: string, date: string, ml: number, max: number): Promise<number | null>;
  /** Merges fields into users/{uid}. */
  updateProfile(uid: string, patch: Record<string, unknown>): Promise<void>;
  weighIns(uid: string): Promise<WeighInRow[]>;
  weightGoal(uid: string): Promise<StoredWeightGoal | null>;
  /** Replaces weightGoal/current, or deletes it with null. */
  setWeightGoal(uid: string, goal: StoredWeightGoal | null): Promise<void>;
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
  async get(uid, collection, id) {
    const snap = await userDoc(uid).collection(collection).doc(id).get();
    return snap.exists ? (snap.data() as Record<string, unknown>) : null;
  },
  async merge(uid, collection, id, data) {
    await userDoc(uid).collection(collection).doc(id).set({ ...data, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  },
  async remove(uid, collection, id) {
    await userDoc(uid).collection(collection).doc(id).delete();
  },
  async activeHabits(uid) {
    const snap = await userDoc(uid).collection("habits").where("archived", "==", false).get();
    return snap.docs.map((d) => ({ id: d.id, name: String(d.get("name") ?? "") }));
  },
  async targets(uid) {
    return fillTargets((await userDoc(uid).get()).get("targets"));
  },
  async updateTargets(uid, patch) {
    const ref = userDoc(uid);
    return ref.firestore.runTransaction(async (tx) => {
      const next = { ...fillTargets((await tx.get(ref)).get("targets")), ...patch };
      tx.set(ref, { targets: next, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
      return next;
    });
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
  async updateProfile(uid, patch) {
    await userDoc(uid).set({ ...patch, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  },
  weighIns: (uid) => loadWeighIns(uid),
  weightGoal: (uid) => loadWeightGoal(uid),
  async setWeightGoal(uid, goal) {
    const ref = userDoc(uid).collection("weightGoal").doc("current");
    if (goal === null) await ref.delete();
    else await ref.set({ ...goal, updatedAt: FieldValue.serverTimestamp() });
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

/** Like UserRepository.toProfile: missing or non-numeric fields fall back to the app's defaults. */
export function fillTargets(raw: unknown): StoredTargets {
  const t = (raw ?? {}) as Record<string, unknown>;
  const pick = (k: keyof StoredTargets) => (typeof t[k] === "number" ? Math.trunc(t[k] as number) : TARGET_DEFAULTS[k]);
  return { calories: pick("calories"), waterMl: pick("waterMl"), sleepMinutes: pick("sleepMinutes"), steps: pick("steps") };
}

export class WriteError extends Error {}

export type MealWrite = {
  title?: string;
  type?: (typeof MEAL_TYPES)[number];
  at?: string;
  items?: MealItemIn[];
  kcal?: number;
  protein?: number;
  carbs?: number;
  fat?: number;
  fiber?: number;
};

function storedItems(items: MealItemIn[]) {
  if (items.length > MAX_MEAL_ITEMS) throw new WriteError(`At most ${MAX_MEAL_ITEMS} items.`);
  return items.map((i) => ({
    name: i.name.slice(0, 60), grams: i.grams, kcal: i.kcal, protein: i.protein, carbs: i.carbs,
    fat: i.fat, fiber: i.fiber ?? 0, confidence: null,
    // A GI the assistant knows for this food; otherwise the app and MCP predict one from macros.
    ...(i.gi !== undefined ? { gi: i.gi } : {}),
  }));
}

function mealTotals(items: MealItemIn[]) {
  const total = (k: keyof MealItemIn) => Math.round(items.reduce((s, i) => s + (Number(i[k] ?? 0) * i.grams) / 100, 0));
  return { kcal: total("kcal"), protein: total("protein"), carbs: total("carbs"), fat: total("fat") };
}

function totalsItem(title: string, a: { kcal: number; protein?: number; carbs?: number; fat?: number; fiber?: number }): MealItemIn[] {
  return [{ name: (title.trim() || "Meal"), grams: 100, kcal: a.kcal, protein: a.protein ?? 0,
    carbs: a.carbs ?? 0, fat: a.fat ?? 0, fiber: a.fiber ?? 0 }];
}

function storedTotals(data: Record<string, unknown>) {
  const items = (data.items ?? []) as Array<Record<string, unknown>>;
  const num = (v: unknown) => Number(v ?? 0) || 0;
  const sum = (k: string) => Math.round(items.reduce((acc, i) => acc + (num(i[k]) * num(i.grams)) / 100, 0));
  return { kcal: sum("kcal"), protein: sum("protein"), carbs: sum("carbs"), fat: sum("fat"), fiber: sum("fiber") };
}

function itemsForLog(a: MealWrite): MealItemIn[] {
  if (a.items && a.items.length > 0) return a.items;
  if (a.kcal === undefined) throw new WriteError("Give items, or totals with at least kcal.");
  return totalsItem(a.title ?? "Meal", { kcal: a.kcal, protein: a.protein, carbs: a.carbs, fat: a.fat, fiber: a.fiber });
}

export function createWriter(uid: string, store: WriteStore = firestoreStore, now: () => number = Date.now) {
  return {
    /** users/{uid}/meals/{auto}: same shape as MealsRepository.save (items carry nutrition per 100 g). */
    async logMeal(a: MealWrite & { type: (typeof MEAL_TYPES)[number] }) {
      const { ms, localDate } = await when(store, uid, a.at, now());
      const items = itemsForLog(a);
      const title = ((a.title ?? "").trim() || items[0].name).slice(0, 80);
      const id = await store.create(uid, "meals", {
        type: a.type, loggedAtMs: ms, localDate, source: "manual", title, items: storedItems(items),
      });
      return { id, path: `users/${uid}/meals/${id}`, localDate, title, ...mealTotals(items) };
    },

    /**
     * Patch users/{uid}/meals/{id}. Only the fields given change. items replaces the list; kcal/macros
     * without items rewrite as one totals item (like log_meal), filling unspecified macros from the current meal.
     */
    async updateMeal(a: MealWrite & { id: string }) {
      const id = a.id.trim();
      if (!id) throw new WriteError("Give the meal id from get_meals.");
      const existing = await store.get(uid, "meals", id);
      if (!existing) throw new WriteError("That meal doesn't exist.");
      const patch: Record<string, unknown> = {};
      const nutrition = a.kcal !== undefined || a.protein !== undefined || a.carbs !== undefined || a.fat !== undefined || a.fiber !== undefined;
      let items: MealItemIn[] | undefined;
      if (a.items && a.items.length > 0) {
        items = a.items;
        patch.items = storedItems(items);
      } else if (nutrition) {
        const cur = storedTotals(existing);
        items = totalsItem(String(a.title ?? existing.title ?? "Meal"), {
          kcal: a.kcal ?? cur.kcal, protein: a.protein ?? cur.protein, carbs: a.carbs ?? cur.carbs,
          fat: a.fat ?? cur.fat, fiber: a.fiber ?? cur.fiber,
        });
        patch.items = storedItems(items);
      }
      if (a.type !== undefined) patch.type = a.type;
      if (a.title !== undefined) {
        const title = a.title.trim();
        if (!title) throw new WriteError("title can't be empty.");
        patch.title = title.slice(0, 80);
      }
      if (a.at !== undefined) {
        const { ms, localDate } = await when(store, uid, a.at, now());
        patch.loggedAtMs = ms;
        patch.localDate = localDate;
      }
      if (Object.keys(patch).length === 0) throw new WriteError("Give at least one field to change.");
      await store.merge(uid, "meals", id, patch);
      const next = { ...existing, ...patch };
      const t = items ? mealTotals(items) : storedTotals(next);
      return {
        id, path: `users/${uid}/meals/${id}`,
        localDate: String(next.localDate ?? ""),
        title: String(next.title ?? "Meal"),
        kcal: t.kcal, protein: t.protein, carbs: t.carbs, fat: t.fat,
      };
    },

    /** Deletes users/{uid}/meals/{id}. The path is under that user, so another user's meal can't be hit. */
    async deleteMeal(a: { id: string }) {
      const id = a.id.trim();
      if (!id) throw new WriteError("Give the meal id from get_meals.");
      if (!(await store.get(uid, "meals", id))) throw new WriteError("That meal doesn't exist.");
      await store.remove(uid, "meals", id);
      return { id, path: `users/${uid}/meals/${id}`, deleted: true };
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
     * users/{uid} field `targets`: partial update of the calorie and water targets. The app saves the
     * whole map {calories, waterMl, sleepMinutes, steps} and the rules require calories 800-6000 and
     * waterMl 500-8000, so the merged map is written in full. Macros are derived from calories.
     */
    async setNutritionTargets(a: { calories?: number; waterMl?: number; sleepMinutes?: number; steps?: number }) {
      const patch: Partial<StoredTargets> = {};
      if (a.calories !== undefined) patch.calories = a.calories;
      if (a.waterMl !== undefined) patch.waterMl = a.waterMl;
      if (a.sleepMinutes !== undefined) patch.sleepMinutes = a.sleepMinutes;
      if (a.steps !== undefined) patch.steps = a.steps;
      if (Object.keys(patch).length === 0) throw new WriteError("Give at least one of calories, waterMl, sleepMinutes or steps.");
      const t = await store.updateTargets(uid, patch);
      return { path: `users/${uid}`, ...t, derivedMacrosG: macrosFor(t.calories), fiberG: fiberFor(t.calories) };
    },

    async nutritionTargets() {
      const t = await store.targets(uid);
      return { ...t, derivedMacrosG: macrosFor(t.calories), fiberG: fiberFor(t.calories),
        note: "Macros are derived by the app from calories (30% protein, 40% carbs, 30% fat) and fibre at 14 g per 1,000 kcal; other micronutrient guides are fixed adult values." };
    },

    /** users/{uid}: display name, goals, units and coaching style (what Settings and onboarding change). */
    async setProfile(a: ProfilePatch) {
      const patch: Record<string, unknown> = {};
      if (a.displayName !== undefined) {
        const name = a.displayName.trim();
        if (!name) throw new WriteError("displayName can't be empty.");
        patch.displayName = name.slice(0, 60);
      }
      if (a.goals !== undefined) patch.goals = [...new Set(a.goals)];
      if (a.units !== undefined) patch.units = a.units;
      if (a.coachStyle !== undefined) patch.coachStyle = a.coachStyle;
      if (Object.keys(patch).length === 0) throw new WriteError("Give at least one of displayName, goals, units or coachStyle.");
      await store.updateProfile(uid, patch);
      return { path: `users/${uid}`, updated: patch };
    },

    /**
     * users/{uid}/weightGoal/current, like WeightViewModel.setGoal / maintain / clearGoal. Changing the
     * target or pace of a goal in progress (same direction, not reached, not maintaining) keeps its
     * start; otherwise it starts fresh from today's trend weight.
     */
    async setWeightGoal(a: { mode: "change" | "maintain" | "clear"; goalKg?: number; paceKgPerWeek?: number }) {
      if (a.mode === "clear") {
        await store.setWeightGoal(uid, null);
        return { path: `users/${uid}/weightGoal/current`, cleared: true };
      }
      const [tz, rows, existing] = await Promise.all([store.timezone(uid), store.weighIns(uid), store.weightGoal(uid)]);
      const today = localParts(tz, new Date(now())).date;
      const current = trendOn(trend(rows, today), today);
      if (current === null) throw new WriteError("Log a weigh-in in the app first; a goal starts from the current trend weight.");
      let goal: StoredWeightGoal;
      if (a.mode === "maintain") {
        const kg = a.goalKg ?? (existing && existing.mode !== "maintain" ? existing.goalKg : current);
        goal = { startKg: kg, startDate: today, goalKg: kg, paceKgPerWeek: 0.5, mode: "maintain", milestonesCelebrated: 0, reachedOn: null };
      } else {
        if (a.goalKg === undefined) throw new WriteError("Give goalKg.");
        const pace = a.paceKgPerWeek ?? existing?.paceKgPerWeek ?? 0.5;
        const keep = existing && existing.mode !== "maintain" && !existing.reachedOn && goalProgress(existing, current) < 1 &&
          (existing.goalKg < existing.startKg) === (a.goalKg < current);
        goal = keep
          ? { ...existing!, goalKg: a.goalKg, paceKgPerWeek: pace, mode: "change" }
          : { startKg: Math.round(current * 10) / 10, startDate: today, goalKg: a.goalKg, paceKgPerWeek: pace, mode: "change", milestonesCelebrated: 0, reachedOn: null };
      }
      await store.setWeightGoal(uid, goal);
      return { path: `users/${uid}/weightGoal/current`, currentTrendKg: Math.round(current * 10) / 10, goal };
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
