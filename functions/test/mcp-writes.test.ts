import { describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/firebase", () => ({ db: {}, userDoc: () => ({}), DATABASE: "resurge", messaging: () => ({}) }));

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createMcpServer, type McpDeps } from "../src/mcp/tools";
import type { StoredWeightGoal, WeighInRow } from "../src/lib/weight";
import { createWriter, fillTargets, type StoredTargets, type WriteStore } from "../src/mcp/writes";

const NOW = Date.parse("2026-11-05T08:00:00Z"); // 13:30 in Asia/Kolkata

function memStore(tz = "Asia/Kolkata") {
  const docs: Record<string, Record<string, unknown>> = {};
  const water: Record<string, number> = {};
  const profile: { targets?: Record<string, unknown>; [k: string]: unknown } = { targets: { calories: 2200, steps: 8000 } };
  const weight: { rows: WeighInRow[]; goal: StoredWeightGoal | null } = { rows: [], goal: null };
  let n = 0;
  const store: WriteStore = {
    timezone: async () => tz,
    habitResetTime: async () => "04:00",
    create: async (uid, col, data) => { const id = `id${++n}`; docs[`users/${uid}/${col}/${id}`] = data; return id; },
    get: async (uid, col, id) => docs[`users/${uid}/${col}/${id}`] ?? null,
    merge: async (uid, col, id, data) => {
      const k = `users/${uid}/${col}/${id}`;
      docs[k] = { ...docs[k], ...data };
    },
    remove: async (uid, col, id) => { delete docs[`users/${uid}/${col}/${id}`]; },
    activeHabits: async () => [{ id: "h1", name: "Walk" }, { id: "h2", name: "Read" }],
    targets: async () => fillTargets(profile.targets),
    updateTargets: async (_u, patch) => { const next: StoredTargets = { ...fillTargets(profile.targets), ...patch }; profile.targets = next; return next; },
    addWater: async (_u, date, ml, max) => { const next = (water[date] ?? 0) + ml; if (next < 0 || next > max) return null; water[date] = next; return next; },
    updateProfile: async (_u, patch) => { Object.assign(profile, patch); },
    weighIns: async () => weight.rows,
    weightGoal: async () => weight.goal,
    setWeightGoal: async (_u, goal) => { weight.goal = goal; },
  };
  return { store, docs, water, profile, weight };
}

const parse = (r: { content: unknown }) => JSON.parse((r.content as { text: string }[])[0].text);

describe("mcp writes", () => {
  it("log_meal from totals stores the app's meal shape", async () => {
    const { store, docs } = memStore();
    const w = createWriter("u1", store, () => NOW);
    const r = await w.logMeal({ type: "lunch", title: "Dal rice", kcal: 650, protein: 22, carbs: 100, fat: 15 });
    expect(r).toMatchObject({ path: "users/u1/meals/id1", localDate: "2026-11-05", kcal: 650, protein: 22 });
    expect(docs["users/u1/meals/id1"]).toEqual({
      type: "lunch", loggedAtMs: NOW, localDate: "2026-11-05", source: "manual", title: "Dal rice",
      items: [{ name: "Dal rice", grams: 100, kcal: 650, protein: 22, carbs: 100, fat: 15, fiber: 0, confidence: null }],
    });
  });

  it("log_meal with items totals per-100 g values", async () => {
    const { store } = memStore();
    const r = await createWriter("u1", store, () => NOW).logMeal({ type: "snack", items: [{ name: "Banana", grams: 120, kcal: 89, protein: 1.1, carbs: 23, fat: 0.3 }] });
    expect(r).toMatchObject({ title: "Banana", kcal: 107, carbs: 28 });
  });

  it("log_meal needs items or kcal, and rejects future times", async () => {
    const w = createWriter("u1", memStore().store, () => NOW);
    await expect(w.logMeal({ type: "dinner" })).rejects.toThrow(/kcal/);
    await expect(w.logMeal({ type: "dinner", kcal: 1, at: "2026-11-06T08:00:00Z" })).rejects.toThrow(/future/);
  });

  it("log_water adds within bounds", async () => {
    const { store } = memStore();
    const w = createWriter("u1", store, () => NOW);
    expect(await w.logWater({ ml: 500 })).toEqual({ path: "users/u1/waterDays/2026-11-05", date: "2026-11-05", totalMl: 500 });
    await expect(w.logWater({ ml: -600 })).rejects.toThrow(/0–20000/);
    await expect(w.logWater({ ml: 100, date: "2026-10-01" })).rejects.toThrow(/7 days/);
  });

  it("set_habit_status matches by name and limits the day", async () => {
    const { store, docs } = memStore();
    const w = createWriter("u1", store, () => NOW);
    expect(await w.setHabitStatus({ habit: "walk", status: "done" })).toMatchObject({ habitId: "h1", date: "2026-11-05" });
    expect(docs["users/u1/habitDays/2026-11-05"]).toEqual({ date: "2026-11-05", entries: { h1: "done" } });
    await w.setHabitStatus({ habit: "h2", status: "missed", date: "2026-11-04" });
    await expect(w.setHabitStatus({ habit: "Walk", status: "done", date: "2026-11-01" })).rejects.toThrow(/Only/);
    await expect(w.setHabitStatus({ habit: "Gym", status: "done" })).rejects.toThrow(/No active habit/);
  });

  it("set_nutrition_targets merges a partial update into the full targets map", async () => {
    const { store, profile } = memStore();
    const w = createWriter("u1", store, () => NOW);
    expect(await w.setNutritionTargets({ calories: 1800 })).toEqual({
      path: "users/u1", calories: 1800, waterMl: 2500, sleepMinutes: 420, steps: 8000, derivedMacrosG: { protein: 135, carbs: 180, fat: 60 }, fiberG: 25,
    });
    expect(profile.targets).toEqual({ calories: 1800, waterMl: 2500, sleepMinutes: 420, steps: 8000 });
    await w.setNutritionTargets({ waterMl: 3000 });
    expect(profile.targets).toMatchObject({ calories: 1800, waterMl: 3000, steps: 8000 });
    await w.setNutritionTargets({ sleepMinutes: 450, steps: 9000 });
    expect(profile.targets).toEqual({ calories: 1800, waterMl: 3000, sleepMinutes: 450, steps: 9000 });
    await expect(w.setNutritionTargets({})).rejects.toThrow(/calories/);
  });

  it("log_meal keeps a GI the assistant gives for a food", async () => {
    const { store, docs } = memStore();
    await createWriter("u1", store, () => NOW).logMeal({ type: "snack", items: [{ name: "Apple", grams: 150, kcal: 52, protein: 0.3, carbs: 14, fat: 0.2, fiber: 2.4, gi: 36 }] });
    expect((docs["users/u1/meals/id1"].items as Record<string, unknown>[])[0]).toMatchObject({ name: "Apple", gi: 36, fiber: 2.4 });
  });

  it("set_profile changes only the fields given", async () => {
    const { store, profile } = memStore();
    const w = createWriter("u1", store, () => NOW);
    expect(await w.setProfile({ goals: ["LOSE_WEIGHT", "EAT_BETTER", "LOSE_WEIGHT"], coachStyle: "commander" })).toEqual({
      path: "users/u1", updated: { goals: ["LOSE_WEIGHT", "EAT_BETTER"], coachStyle: "commander" },
    });
    expect(profile).toMatchObject({ goals: ["LOSE_WEIGHT", "EAT_BETTER"], coachStyle: "commander", targets: { calories: 2200 } });
    await w.setProfile({ displayName: "  Shashank  ", units: "imperial" });
    expect(profile).toMatchObject({ displayName: "Shashank", units: "imperial" });
    await expect(w.setProfile({})).rejects.toThrow(/at least one/);
  });

  it("set_weight_goal starts from the trend, keeps the start of a goal in progress, maintains and clears", async () => {
    const { store, weight } = memStore();
    const w = createWriter("u1", store, () => NOW);
    await expect(w.setWeightGoal({ mode: "change", goalKg: 70 })).rejects.toThrow(/weigh-in/);
    weight.rows = [{ kg: 80, atMs: NOW - 10 * 86_400_000, localDate: "2026-10-26" }, { kg: 79, atMs: NOW, localDate: "2026-11-05" }];
    const first = await w.setWeightGoal({ mode: "change", goalKg: 72, paceKgPerWeek: 0.5 });
    expect(first.goal).toMatchObject({ startDate: "2026-11-05", goalKg: 72, mode: "change", paceKgPerWeek: 0.5 });
    const start = (first.goal as StoredWeightGoal).startKg;
    // Same direction, in progress: keeps the start, changes target and pace.
    await w.setWeightGoal({ mode: "change", goalKg: 70, paceKgPerWeek: 0.75 });
    expect(weight.goal).toMatchObject({ startKg: start, goalKg: 70, paceKgPerWeek: 0.75 });
    await w.setWeightGoal({ mode: "maintain" });
    expect(weight.goal).toMatchObject({ mode: "maintain", goalKg: 70, startKg: 70 });
    expect(await w.setWeightGoal({ mode: "clear" })).toMatchObject({ cleared: true });
    expect(weight.goal).toBeNull();
    await expect(w.setWeightGoal({ mode: "change" })).rejects.toThrow(/goalKg/);
  });

  it("update_meal patches given fields and delete_meal removes an owned meal", async () => {
    const { store, docs } = memStore();
    const w = createWriter("u1", store, () => NOW);
    await w.logMeal({ type: "lunch", title: "Dal rice", kcal: 650, protein: 22, carbs: 100, fat: 15 });
    const patched = await w.updateMeal({ id: "id1", title: "Dal and rice", protein: 28 });
    expect(patched).toMatchObject({ id: "id1", title: "Dal and rice", kcal: 650, protein: 28, carbs: 100, fat: 15 });
    expect(docs["users/u1/meals/id1"]).toMatchObject({ type: "lunch", title: "Dal and rice", source: "manual" });
    expect((docs["users/u1/meals/id1"].items as Record<string, unknown>[])[0]).toMatchObject({ kcal: 650, protein: 28, carbs: 100 });
    await w.updateMeal({ id: "id1", type: "dinner", items: [{ name: "Roti", grams: 80, kcal: 300, protein: 10, carbs: 55, fat: 4 }] });
    expect(docs["users/u1/meals/id1"]).toMatchObject({ type: "dinner" });
    await expect(w.updateMeal({ id: "id1" })).rejects.toThrow(/at least one/);
    await expect(w.updateMeal({ id: "nope", title: "x" })).rejects.toThrow(/doesn't exist/);
    expect(await w.deleteMeal({ id: "id1" })).toEqual({ id: "id1", path: "users/u1/meals/id1", deleted: true });
    expect(docs["users/u1/meals/id1"]).toBeUndefined();
    await expect(w.deleteMeal({ id: "id1" })).rejects.toThrow(/doesn't exist/);
  });

  it("get_nutrition_targets reads targets with derived macros", async () => {
    const r = await createWriter("u1", memStore().store, () => NOW).nutritionTargets();
    expect(r).toMatchObject({ calories: 2200, waterMl: 2500, sleepMinutes: 420, steps: 8000, derivedMacrosG: { protein: 165, carbs: 220, fat: 73 }, fiberG: 31 });
  });

  it("set_nutrition_targets enforces the app's bounds over MCP", async () => {
    const { store, profile } = memStore();
    const [a, b] = InMemoryTransport.createLinkedPair();
    const server = createMcpServer("u1", {} as McpDeps, store);
    const client = new Client({ name: "t", version: "0" });
    await Promise.all([server.connect(a), client.connect(b)]);
    expect((await client.callTool({ name: "set_nutrition_targets", arguments: { calories: 500 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_nutrition_targets", arguments: { waterMl: 9000 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_nutrition_targets", arguments: { calories: 2100.5 } })).isError).toBe(true);
    expect(profile.targets).toEqual({ calories: 2200, steps: 8000 });
    const ok = await client.callTool({ name: "set_nutrition_targets", arguments: { calories: 2100 } });
    expect(parse(ok).calories).toBe(2100);
  });

  it("validates inputs over MCP and reports write errors as tool errors", async () => {
    const { store, docs } = memStore();
    const [a, b] = InMemoryTransport.createLinkedPair();
    const server = createMcpServer("u1", {} as McpDeps, store);
    const client = new Client({ name: "t", version: "0" });
    await Promise.all([server.connect(a), client.connect(b)]);
    expect((await client.callTool({ name: "log_water", arguments: { ml: 0 } })).isError).toBe(true);
    expect((await client.callTool({ name: "log_meal", arguments: { type: "brunch", kcal: 100 } })).isError).toBe(true);
    expect((await client.callTool({ name: "delete_meal", arguments: { id: "missing" } })).isError).toBe(true);
    expect((await client.callTool({ name: "update_meal", arguments: { id: "missing", title: "x" } })).isError).toBe(true);
    expect((await client.callTool({ name: "log_weight", arguments: { kg: 70 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_profile", arguments: { goals: ["GET_RICH"] } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_nutrition_targets", arguments: { steps: 100 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_weight_goal", arguments: { mode: "change", goalKg: 10 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_habit_status", arguments: { habit: "Gym", status: "done" } })).isError).toBe(true);
    expect(Object.keys(docs)).toHaveLength(0);
    const ok = await client.callTool({ name: "log_water", arguments: { ml: 250 } });
    expect(parse(ok).path).toMatch(/^users\/u1\/waterDays\//);
    const logged = await client.callTool({ name: "log_meal", arguments: { type: "lunch", title: "Rice", kcal: 400 } });
    const mealId = parse(logged).id as string;
    expect(parse(await client.callTool({ name: "update_meal", arguments: { id: mealId, title: "Rice bowl" } })).title).toBe("Rice bowl");
    expect(parse(await client.callTool({ name: "delete_meal", arguments: { id: mealId } })).deleted).toBe(true);
  });
});
