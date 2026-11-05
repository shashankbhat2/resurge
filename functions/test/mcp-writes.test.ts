import { describe, expect, it, vi } from "vitest";

vi.mock("../src/lib/firebase", () => ({ db: {}, userDoc: () => ({}), DATABASE: "resurge", messaging: () => ({}) }));

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { createMcpServer, type McpDeps } from "../src/mcp/tools";
import { createWriter, type WriteStore } from "../src/mcp/writes";

const NOW = Date.parse("2026-11-05T08:00:00Z"); // 13:30 in Asia/Kolkata

function memStore(tz = "Asia/Kolkata") {
  const docs: Record<string, Record<string, unknown>> = {};
  const water: Record<string, number> = {};
  let n = 0;
  const store: WriteStore = {
    timezone: async () => tz,
    habitResetTime: async () => "04:00",
    create: async (uid, col, data) => { const id = `id${++n}`; docs[`users/${uid}/${col}/${id}`] = data; return id; },
    merge: async (uid, col, id, data) => { docs[`users/${uid}/${col}/${id}`] = data; },
    activeHabits: async () => [{ id: "h1", name: "Walk" }, { id: "h2", name: "Read" }],
    addWater: async (_u, date, ml, max) => { const next = (water[date] ?? 0) + ml; if (next < 0 || next > max) return null; water[date] = next; return next; },
  };
  return { store, docs, water };
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

  it("validates inputs over MCP and reports write errors as tool errors", async () => {
    const { store, docs } = memStore();
    const [a, b] = InMemoryTransport.createLinkedPair();
    const server = createMcpServer("u1", {} as McpDeps, store);
    const client = new Client({ name: "t", version: "0" });
    await Promise.all([server.connect(a), client.connect(b)]);
    expect((await client.callTool({ name: "log_water", arguments: { ml: 0 } })).isError).toBe(true);
    expect((await client.callTool({ name: "log_meal", arguments: { type: "brunch", kcal: 100 } })).isError).toBe(true);
    expect((await client.callTool({ name: "log_weight", arguments: { kg: 70 } })).isError).toBe(true);
    expect((await client.callTool({ name: "set_habit_status", arguments: { habit: "Gym", status: "done" } })).isError).toBe(true);
    expect(Object.keys(docs)).toHaveLength(0);
    const ok = await client.callTool({ name: "log_water", arguments: { ml: 250 } });
    expect(parse(ok).path).toMatch(/^users\/u1\/waterDays\//);
  });
});
