import { Agent } from "@mastra/core/agent";
import { describe, expect, it } from "vitest";
import { Evidence, groundMeal, MAX_REQUEST_INPUT_TOKENS, promptTokens, runMealAgent, type MealRunDeps } from "../src/food/agent";
import { localFood } from "../src/food/db";

type Part = Record<string, unknown>;
interface Step {
  calls?: { name: string; input: unknown }[];
  text?: string;
  inputTokens?: number;
}

/**
 * A scripted language model: each request returns the next step's tool calls. Records what the
 * agent sent so tests can check tools offered and messages.
 */
function scriptedModel(steps: Step[]) {
  const requests: { tools: string[]; prompt: unknown[] }[] = [];
  let i = 0;
  const parts = (s: Step): Part[] => {
    const usage = { inputTokens: s.inputTokens ?? 2_000, outputTokens: 100, totalTokens: (s.inputTokens ?? 2_000) + 100 };
    const out: Part[] = [{ type: "stream-start", warnings: [] }];
    (s.calls ?? []).forEach((c, j) => out.push({ type: "tool-call", toolCallId: `c${i}-${j}`, toolName: c.name, input: JSON.stringify(c.input) }));
    if (s.text) out.push({ type: "text-start", id: "t" }, { type: "text-delta", id: "t", delta: s.text }, { type: "text-end", id: "t" });
    out.push({ type: "finish", finishReason: s.calls?.length ? "tool-calls" : "stop", usage });
    return out;
  };
  const model = {
    specificationVersion: "v2",
    provider: "mock",
    modelId: "mock-haiku",
    supportedUrls: {},
    async doGenerate(opts: { tools?: { name: string }[]; prompt: unknown[] }) {
      requests.push({ tools: (opts.tools ?? []).map((t) => t.name), prompt: opts.prompt });
      const step = steps[Math.min(i, steps.length - 1)];
      const ps = parts(step);
      i++;
      const content: Part[] = [];
      let text = "";
      let finishReason: unknown;
      let usage: unknown;
      for (const p of ps) {
        if (p.type === "tool-call") content.push({ type: "tool-call", toolCallId: p.toolCallId, toolName: p.toolName, input: p.input });
        if (p.type === "text-delta") text += p.delta as string;
        if (p.type === "finish") {
          finishReason = p.finishReason;
          usage = p.usage;
        }
      }
      if (text) content.push({ type: "text", text });
      return { content, finishReason, usage, warnings: [] };
    },
    async doStream() {
      throw new Error("not used");
    },
  };
  return { model, requests };
}

const deps: MealRunDeps = {
  lookupBarcode: async () => null,
  webLookup: async () => null,
  searchUsda: async () => [],
};

const req = { mealType: "lunch", localTime: "13:10", text: "2 rotis and a bowl of toor dal" };

describe("meal agent (Mastra loop with a scripted model)", () => {
  it("searches, gets a made-up id rejected, fixes it, and returns grounded nutrition", async () => {
    const roti = localFood("cofid:11-459")!;
    const { model, requests } = scriptedModel([
      {
        calls: [
          { name: "search_foods", input: { query: "chapati" } },
          { name: "search_foods", input: { query: "toor dal" } },
          { name: "search_foods", input: { query: "ghee" } },
        ],
      },
      {
        calls: [
          {
            name: "submit_meal",
            input: { title: "Roti and dal", items: [{ name: "Roti", grams: 80, sourceId: "ifct:Z999", confidence: 0.9 }], question: "", options: [], notes: "" },
          },
        ],
      },
      {
        calls: [
          {
            name: "submit_meal",
            input: {
              title: "Roti and dal",
              items: [
                { name: "Roti", grams: 80, sourceId: "cofid:11-459", confidence: 0.9 },
                {
                  name: "Toor dal",
                  grams: 200,
                  components: [
                    { sourceId: "ifct:B021", grams: 60 },
                    { sourceId: "ifct:T013", grams: 5 },
                  ],
                  confidence: 0.7,
                },
              ],
              question: "",
              options: [],
              notes: "Assumed 1 tsp ghee in the dal",
            },
          },
        ],
      },
    ]);
    const agent = new Agent({ id: "test-meal", name: "test", instructions: "test", model: model as never });

    const { meal, usage } = await runMealAgent(req, { ...deps, agent });

    expect(requests).toHaveLength(3);
    expect(requests[0].tools.sort()).toEqual(["lookup_barcode", "read_label", "search_foods", "submit_meal", "web_lookup"]);
    expect(JSON.stringify(requests[2].prompt)).toContain("isn't from a tool result");

    expect(meal.items).toHaveLength(2);
    const [r, d] = meal.items;
    expect(r.sourceId).toBe("cofid:11-459");
    expect(r.per100g.kcal).toBeCloseTo(roti.per100g.kcal!, 0);
    expect(r.verified).toBe(true);

    // 60 g raw toor dal + 5 g ghee spread over a 200 g served bowl.
    const dal = localFood("ifct:B021")!.per100g;
    const ghee = localFood("ifct:T013")!.per100g;
    const expectedKcal = ((dal.kcal! * 60 + ghee.kcal! * 5) / 100 / 200) * 100;
    expect(d.sourceId).toBe("components");
    expect(d.per100g.kcal).toBeCloseTo(expectedKcal, 0);
    expect(d.sourceDetail).toContain("Red gram, dal 60 g");
    expect(usage.steps).toBe(3);
  });

  it("offers only submit_meal near the 99k request limit and stops before crossing it", async () => {
    const near = MAX_REQUEST_INPUT_TOKENS - 6_000;
    const { model, requests } = scriptedModel([
      { calls: [{ name: "search_foods", input: { query: "basmati rice boiled" } }], inputTokens: near },
      {
        calls: [
          { name: "submit_meal", input: { title: "Rice", items: [{ name: "Rice", grams: 150, sourceId: "cofid:11-858", confidence: 0.8 }], question: "", options: [], notes: "" } },
        ],
        inputTokens: near + 1_000,
      },
    ]);
    const agent = new Agent({ id: "test-budget", name: "test", instructions: "test", model: model as never });
    const { meal } = await runMealAgent({ ...req, text: "rice" }, { ...deps, agent });
    expect(requests[1].tools).toEqual(["submit_meal"]);
    expect(meal.items[0].sourceId).toBe("cofid:11-858");

    const over = scriptedModel([{ calls: [{ name: "search_foods", input: { query: "rice" } }], inputTokens: MAX_REQUEST_INPUT_TOKENS }]);
    const agent2 = new Agent({ id: "test-budget-2", name: "test", instructions: "test", model: over.model as never });
    await expect(runMealAgent({ ...req, text: "rice" }, { ...deps, agent: agent2 })).rejects.toThrow();
    expect(over.requests).toHaveLength(1);
  });

  it("reads a nutrition label only when there is a photo, and grounds the item in it", async () => {
    const saved: string[] = [];
    const { model } = scriptedModel([
      {
        calls: [
          {
            name: "read_label",
            input: { productName: "Haldiram's Aloo Bhujia", basisGrams: 100, kcal: 600, protein: 10, carbs: 40, fat: 44, sodiumMg: 900 },
          },
        ],
      },
      {
        calls: [
          {
            name: "submit_meal",
            input: { title: "Aloo bhujia", items: [{ name: "Aloo bhujia", grams: 30, sourceId: "label:8904063200011", confidence: 0.95 }], question: "", options: [], notes: "" },
          },
        ],
      },
    ]);
    const agent = new Agent({ id: "test-label", name: "test", instructions: "test", model: model as never });
    const { meal } = await runMealAgent(
      { mealType: "snack", localTime: "17:00", image: "/9j/AAAA", barcode: "8904063200011" },
      { ...deps, agent, onLabel: (r) => saved.push(r.id) },
    );
    expect(saved).toEqual(["label:8904063200011"]);
    expect(meal.items[0].per100g.kcal).toBe(600);
    expect(meal.items[0].per100g.sodium).toBe(900);
    expect(meal.items[0].sourceName).toBe("Nutrition label");
  });
});

describe("groundMeal", () => {
  const evidence = () => {
    const e = new Evidence();
    e.add(localFood("ifct:A015")!);
    return e;
  };
  const base = { title: "x", question: "", options: [], notes: "" };

  it("rejects items with two ways of giving nutrition or none", () => {
    const both = groundMeal({ ...base, items: [{ name: "Rice", grams: 100, sourceId: "ifct:A015", estimatePer100g: { kcal: 1, protein: 0, carbs: 0, fat: 0, fiber: 0 }, confidence: 1 }] }, evidence());
    expect("problems" in both && both.problems[0]).toContain("exactly one");
    const none = groundMeal({ ...base, items: [{ name: "Rice", grams: 100, confidence: 1 }] }, evidence());
    expect("problems" in none).toBe(true);
  });

  it("allows an estimate only after searching, and marks it unverified", () => {
    const item = { name: "Thing", grams: 100, estimatePer100g: { kcal: 200, protein: 5, carbs: 30, fat: 6, fiber: 1 }, confidence: 0.9 };
    expect("problems" in groundMeal({ ...base, items: [item] }, evidence())).toBe(true);
    const e = evidence();
    e.searches = 1;
    const ok = groundMeal({ ...base, items: [item] }, e);
    if (!("meal" in ok)) throw new Error("expected a meal");
    expect(ok.meal.items[0]).toMatchObject({ sourceId: "estimate", verified: false, confidence: 0.5 });
    expect(ok.meal.items[0].per100g.iron).toBeNull();
  });

  it("rejects ingredient weights no cooking explains", () => {
    const r = groundMeal({ ...base, items: [{ name: "Rice", grams: 100, components: [{ sourceId: "ifct:A015", grams: 400 }], confidence: 1 }] }, evidence());
    expect("problems" in r && r.problems[0]).toContain("no cooking explains");
  });
});

describe("prompt size for the 99k cap", () => {
  it("uses the provider's raw total, else counts cached tokens too (never under)", () => {
    expect(promptTokens({ inputTokens: 1_000, cachedInputTokens: 30_000, raw: { inputTokens: { total: 31_000 } } })).toBe(31_000);
    expect(promptTokens({ inputTokens: 1_000, cachedInputTokens: 30_000, cacheCreationInputTokens: 2_000 })).toBe(33_000);
  });
});
