import type { GroundedMeal, MealRunUsage } from "../src/food/agent";
import { MAX_REQUEST_INPUT_TOKENS } from "../src/food/agent";
import { localFood } from "../src/food/db";
import type { Accept, EvalCase } from "./cases";

/** Haiku 5.5 rates for prompts ≤ 100K tokens, $ per million tokens. */
export const RATES = { input: 0.1, output: 0.5, cacheRead: 0.01, cacheWrite: 0.125 };

export interface CaseResult {
  id: string;
  text: string;
  ok: boolean;
  error?: string;
  /** Share of logged items backed by a source (anything but an estimate). 1 when nothing was logged. */
  grounding: number;
  /** Expected foods matched to an acceptable entry, out of expected. */
  matched: number;
  expected: number;
  missing: string[];
  kcal: number;
  kcalRange: [number, number];
  kcalOk: boolean;
  steps: number;
  maxRequestInputTokens: number;
  underTokenCap: boolean;
  costUsd: number;
  items: { name: string; grams: number; sourceId: string; componentSourceIds?: string[]; kcal: number; verified: boolean }[];
}

/** kcal of the reference portion and the accepted range around it. */
export function kcalRange(c: EvalCase): [number, number] {
  let center = c.extraKcal ?? 0;
  for (const part of c.ref) {
    const per100 = part.kcalPer100g ?? localFood(part.id)?.per100g.kcal;
    if (per100 == null) throw new Error(`${c.id}: reference ${part.id} has no kcal in data/food-db.json`);
    center += (per100 * part.grams) / 100;
  }
  const t = c.tolerance ?? 0.25;
  return [Math.round(center * (1 - t)), Math.round(center * (1 + t))];
}

export function accepts(patterns: Accept[], sourceId: string): boolean {
  return patterns.some((p) => {
    if (p instanceof RegExp) return p.test(sourceId);
    if (p.endsWith(":")) return sourceId.startsWith(p);
    return p === sourceId;
  });
}

/**
 * Pairs each expected food with a distinct logged item whose source is acceptable. Expected foods
 * are tried in order of how few items could match them, so a broad pattern doesn't take an item a
 * narrow one needs.
 */
export function matchItems(c: EvalCase, sourceIds: (string | string[])[]): { matched: number; missing: string[] } {
  const candidates = c.expect.map((e) => ({ e, ids: sourceIds.map((id, i) => ((Array.isArray(id) ? id.some((part) => accepts(e.accept, part)) : accepts(e.accept, id)) ? i : -1)).filter((i) => i >= 0) }));
  candidates.sort((a, b) => a.ids.length - b.ids.length);
  const used = new Set<number>();
  const missing: string[] = [];
  let matched = 0;
  for (const { e, ids } of candidates) {
    const free = ids.find((i) => !used.has(i));
    if (free == null) missing.push(e.label);
    else {
      used.add(free);
      matched++;
    }
  }
  return { matched, missing };
}

export function costUsd(u: MealRunUsage): number {
  // Usage input tokens include cache reads and writes; bill those at their own rates.
  const uncached = u.inputTokens; // runMealAgent reports cache reads and writes separately
  return (uncached * RATES.input + u.cacheReadTokens * RATES.cacheRead + u.cacheWriteTokens * RATES.cacheWrite + u.outputTokens * RATES.output) / 1e6;
}

export function scoreCase(c: EvalCase, outcome: { meal: GroundedMeal; usage: MealRunUsage } | { error: string; usage?: MealRunUsage }): CaseResult {
  const range = c.expectEmpty ? ([0, 0] as [number, number]) : kcalRange(c);
  const usage = outcome.usage ?? { inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0, steps: 0, maxRequestInputTokens: 0 };
  const base = {
    id: c.id,
    text: c.text,
    kcalRange: range,
    steps: usage.steps,
    maxRequestInputTokens: usage.maxRequestInputTokens,
    underTokenCap: usage.maxRequestInputTokens < MAX_REQUEST_INPUT_TOKENS,
    costUsd: costUsd(usage),
    expected: c.expect.length,
  };
  if ("error" in outcome) {
    return { ...base, ok: false, error: outcome.error, grounding: 0, matched: 0, missing: c.expect.map((e) => e.label), kcal: 0, kcalOk: false, items: [] };
  }
  const items = outcome.meal.items.map((i) => ({
    name: i.name,
    grams: Math.round(i.grams),
    sourceId: i.sourceId,
    componentSourceIds: i.componentSourceIds,
    kcal: Math.round(((i.per100g.kcal ?? 0) * i.grams) / 100),
    verified: i.verified,
  }));
  const kcal = items.reduce((s, i) => s + i.kcal, 0);
  const grounding = items.length === 0 ? 1 : items.filter((i) => i.sourceId !== "estimate").length / items.length;
  if (c.expectEmpty) {
    const empty = items.length === 0;
    return { ...base, ok: empty, grounding, matched: 0, missing: [], kcal, kcalOk: empty, items };
  }
  const { matched, missing } = matchItems(
    c,
    items.map((i) => [i.sourceId, ...(i.componentSourceIds ?? [])]),
  );
  const kcalOk = kcal >= range[0] && kcal <= range[1];
  return { ...base, ok: missing.length === 0 && kcalOk && base.underTokenCap, grounding, matched, missing, kcal, kcalOk, items };
}

export interface Summary {
  cases: number;
  passed: number;
  errors: number;
  grounding: number;
  sourceMatch: number;
  kcalInRange: number;
  allUnderTokenCap: boolean;
  maxRequestInputTokens: number;
  meanSteps: number;
  totalCostUsd: number;
  meanCostUsd: number;
}

export function summarize(results: CaseResult[]): Summary {
  const n = results.length || 1;
  const food = results.filter((r) => r.expected > 0);
  const expected = food.reduce((s, r) => s + r.expected, 0) || 1;
  const total = results.reduce((s, r) => s + r.costUsd, 0);
  return {
    cases: results.length,
    passed: results.filter((r) => r.ok).length,
    errors: results.filter((r) => r.error).length,
    grounding: results.reduce((s, r) => s + r.grounding, 0) / n,
    sourceMatch: food.reduce((s, r) => s + r.matched, 0) / expected,
    kcalInRange: results.filter((r) => r.kcalOk).length / n,
    allUnderTokenCap: results.every((r) => r.underTokenCap),
    maxRequestInputTokens: Math.max(0, ...results.map((r) => r.maxRequestInputTokens)),
    meanSteps: results.reduce((s, r) => s + r.steps, 0) / n,
    totalCostUsd: total,
    meanCostUsd: total / n,
  };
}

const pct = (v: number) => `${Math.round(v * 100)}%`;

export function formatTable(results: CaseResult[], s: Summary): string {
  const rows = results.map((r) =>
    [
      r.ok ? "PASS" : "FAIL",
      r.id.padEnd(26),
      `${r.matched}/${r.expected}`.padStart(5),
      `${r.kcal}`.padStart(5),
      `${r.kcalRange[0]}–${r.kcalRange[1]}`.padStart(10),
      pct(r.grounding).padStart(5),
      `${r.steps}`.padStart(3),
      `${Math.round(r.maxRequestInputTokens / 1000)}k`.padStart(5),
      `$${r.costUsd.toFixed(5)}`,
      r.error ? ` ${r.error}` : r.missing.length ? ` missing: ${r.missing.join(", ")}` : "",
    ].join("  "),
  );
  const head = ["    ", "case".padEnd(26), "match", " kcal", "     range", "grnd.", "stp", "  max", "cost"].join("  ");
  const foot = [
    `passed ${s.passed}/${s.cases} (errors ${s.errors})`,
    `grounding ${pct(s.grounding)}, source match ${pct(s.sourceMatch)}, kcal in range ${pct(s.kcalInRange)}`,
    `steps ${s.meanSteps.toFixed(1)} on average; largest request ${s.maxRequestInputTokens} input tokens (${s.allUnderTokenCap ? "all" : "NOT all"} under ${MAX_REQUEST_INPUT_TOKENS})`,
    `cost $${s.totalCostUsd.toFixed(4)} total, $${s.meanCostUsd.toFixed(5)} per meal`,
  ];
  return [head, ...rows, "", ...foot].join("\n");
}
