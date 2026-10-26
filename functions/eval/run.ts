/**
 * Runs the meal agent on the text cases in cases.ts against the real model and scores the results.
 *
 *   ANTHROPIC_API_KEY=… npx tsx eval/run.ts                 all cases
 *   ANTHROPIC_API_KEY=… npx tsx eval/run.ts --only=maggi,banana
 *   npx tsx eval/run.ts --self-test                          check the scoring, no API calls
 *
 * Optional: USDA_FDC_API_KEY (else DEMO_KEY, 30 requests/hour), MASTRA_PLATFORM_ACCESS_TOKEN and
 * MASTRA_PROJECT_ID to send traces to the Mastra platform, --concurrency=N (default 3).
 */
import { strict as assert } from "node:assert";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { mealLogger, runMealAgent, type GroundedMeal, type MealRunUsage } from "../src/food/agent";
import { localFood } from "../src/food/db";
import { emptyNutrients } from "../src/food/nutrients";
import { lookupOff } from "../src/food/openfoodfacts";
import { setUsdaKey } from "../src/food/usda";
import { CASES, type EvalCase } from "./cases";
import { accepts, formatTable, kcalRange, scoreCase, summarize, type CaseResult } from "./score";

const args = new Map(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, "").split("=");
    return [k, v ?? "true"] as const;
  }),
);

async function runCase(c: EvalCase, apiKey: string, agent: typeof mealLogger): Promise<CaseResult> {
  try {
    const out = await runMealAgent(
      { text: c.text, mealType: c.mealType, localTime: "13:00" },
      {
        agent,
        anthropicApiKey: apiKey,
        traceTags: ["eval", c.id],
        lookupBarcode: (code) => lookupOff(code),
        webLookup: async () => null,
      },
    );
    return scoreCase(c, out);
  } catch (e) {
    return scoreCase(c, { error: (e as Error).message });
  }
}

async function pool<T, R>(items: T[], limit: number, fn: (t: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: Math.min(limit, items.length) }, async () => {
      while (next < items.length) {
        const i = next++;
        out[i] = await fn(items[i]);
      }
    }),
  );
  return out;
}

async function main() {
  if (args.has("self-test")) return selfTest();

  const apiKey = process.env.ANTHROPIC_API_KEY;
  if (!apiKey) {
    console.log("ANTHROPIC_API_KEY isn't set, so nothing was run.\n\n  ANTHROPIC_API_KEY=sk-ant-… npx tsx eval/run.ts\n\nSee eval/README.md. `npx tsx eval/run.ts --self-test` checks the scoring without the API.");
    return;
  }
  setUsdaKey(() => process.env.USDA_FDC_API_KEY || "DEMO_KEY");

  const only = args.get("only")?.split(",");
  const cases = only ? CASES.filter((c) => only.includes(c.id)) : CASES;
  const concurrency = Number(args.get("concurrency") ?? 3);

  // Traces go to the Mastra platform only when a token is configured.
  let agent = mealLogger;
  let flush = async () => {};
  if (process.env.MASTRA_PLATFORM_ACCESS_TOKEN) {
    const m = require("../src/mastra") as typeof import("../src/mastra");
    agent = m.mastra().getAgent("mealLogger") as typeof mealLogger;
    flush = m.flushTraces;
  }

  console.log(`Running ${cases.length} cases, ${concurrency} at a time…`);
  const started = Date.now();
  const results = await pool(cases, concurrency, async (c) => {
    const r = await runCase(c, apiKey, agent);
    console.log(`${r.ok ? "PASS" : "FAIL"} ${r.id}${r.ok ? "" : `: ${r.error ?? `missing=${r.missing.join(",") || "none"}, kcal=${r.kcal} (${r.kcalRange.join("–")})`}`}`);
    return r;
  });
  await flush();

  const summary = summarize(results);
  console.log(`\n${formatTable(results, summary)}\n(${Math.round((Date.now() - started) / 1000)} s)`);

  const dir = join(__dirname, "results");
  mkdirSync(dir, { recursive: true });
  const file = join(dir, `${new Date().toISOString().replace(/[:.]/g, "-")}.json`);
  writeFileSync(file, JSON.stringify({ summary, results }, null, 2));
  console.log(`Results: ${file}`);
  if (summary.passed !== summary.cases) process.exitCode = 1;
}

/** Scores made-up agent results to check the scoring itself. */
function selfTest() {
  // Every reference id must exist so every range can be computed.
  for (const c of CASES) if (!c.expectEmpty) kcalRange(c);
  const ids = new Set(CASES.map((c) => c.id));
  assert.equal(ids.size, CASES.length, "case ids must be unique");

  const usage: MealRunUsage = { inputTokens: 16_000, outputTokens: 2_000, cacheReadTokens: 10_000, cacheWriteTokens: 4_000, steps: 3, maxRequestInputTokens: 12_000 };
  const item = (sourceId: string, grams: number, kcalPer100g?: number) => {
    const per100g = kcalPer100g != null ? { ...emptyNutrients(), kcal: kcalPer100g } : localFood(sourceId)!.per100g;
    return { name: sourceId, grams, per100g, confidence: 0.9, sourceId, sourceName: "", sourceDetail: "", verified: true };
  };
  const meal = (items: GroundedMeal["items"]): GroundedMeal => ({ title: "t", items, question: "", options: [], notes: "" });
  const roti = CASES.find((c) => c.id === "roti-toor-dal")!;

  // The reference meal itself passes.
  const good = scoreCase(roti, { meal: meal([item("cofid:11-459", 80), item("components", 200, (331 * 60 + 900 * 5) / 200)]), usage });
  assert.equal(good.ok, true, JSON.stringify(good));
  assert.equal(good.matched, 2);
  assert.equal(good.grounding, 1);

  // A wrong source for the dal and half the calories fail, and an estimate lowers grounding.
  const bad = scoreCase(roti, { meal: meal([item("cofid:11-459", 80), item("estimate", 100, 100)]), usage });
  assert.equal(bad.ok, false);
  assert.deepEqual(bad.missing, ["toor dal"]);
  assert.equal(bad.grounding, 0.5);
  assert.equal(bad.kcalOk, false);

  // Valid cooked Indian entries must not be rejected by the source allowlists.
  for (const [caseId, label, sourceId] of [
    ["roti-toor-dal", "toor dal", "cofid:15-780"],
    ["rajma-chawal", "rajma", "cofid:15-146"],
    ["bhindi-roti", "bhindi", "cofid:15-627"],
  ]) {
    const expected = CASES.find((c) => c.id === caseId)!.expect.find((e) => e.label === label)!;
    assert.ok(localFood(sourceId), sourceId);
    assert.ok(accepts(expected.accept, sourceId), `${caseId}: ${sourceId}`);
    assert.equal(accepts(expected.accept, "cofid:11-459"), false, `${caseId}: chapati is not ${label}`);
  }

  // Composed items retain their actual sources, so valid cereal and milk still match.
  const cereal = CASES.find((c) => c.id === "cornflakes-milk")!;
  const composed = (id: string, grams: number) => ({ ...item(id, grams), sourceId: "components", componentSourceIds: [id] });
  assert.equal(scoreCase(cereal, { meal: meal([composed("cofid:11-742", 30), composed("ifct:L002", 150)]), usage }).ok, true);
  assert.equal(scoreCase(cereal, { meal: meal([composed("cofid:11-459", 30), composed("ifct:L002", 150)]), usage }).ok, false);

  // One item can't satisfy two expected foods.
  const one = scoreCase(roti, { meal: meal([item("components", 280, 150)]), usage });
  assert.equal(one.matched, 1);

  // The non-food case passes only when nothing is logged.
  const notFood = CASES.find((c) => c.expectEmpty)!;
  assert.equal(scoreCase(notFood, { meal: meal([]), usage }).ok, true);
  assert.equal(scoreCase(notFood, { meal: meal([item("cofid:11-459", 40)]), usage }).ok, false);

  // Over the token cap fails even when the food is right; errors score zero.
  const over = scoreCase(roti, { meal: meal([item("cofid:11-459", 80), item("components", 200, (331 * 60 + 900 * 5) / 200)]), usage: { ...usage, maxRequestInputTokens: 99_000 } });
  assert.equal(over.ok, false);
  const err = scoreCase(roti, { error: "boom" });
  assert.equal(err.ok, false);
  assert.equal(err.matched, 0);

  // Cost: 16k uncached × $0.10 + 10k cache reads × $0.01 + 4k writes × $0.125 + 2k out × $0.50, per million.
  assert.ok(Math.abs(good.costUsd - (16_000 * 0.1 + 10_000 * 0.01 + 4_000 * 0.125 + 2_000 * 0.5) / 1e6) < 1e-12);

  const s = summarize([good, bad, err]);
  assert.equal(s.passed, 1);
  assert.equal(s.errors, 1);
  console.log(formatTable([good, bad, err], s));
  console.log(`\nSelf-test passed (${CASES.length} cases, all reference ranges computed).`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
