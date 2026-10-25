import { Agent } from "@mastra/core/agent";
import { createTool } from "@mastra/core/tools";
import { z } from "zod";
import { MEAL_LOGGER_SYSTEM } from "../prompts/food";
import { localFood, searchLocal, SOURCE_NAMES, sourceOf, type FoodRecord } from "./db";
import { brief, dishPer100g, emptyNutrients, rounded, type Nutrients } from "./nutrients";
import { searchUsda } from "./usda";
import type { WebLookupResult } from "./websearch";

export const MEAL_MODEL_ID = "claude-haiku-5-5";

/**
 * Haiku 5.5 is billed at its lower rate only while a request's prompt is at most 100K tokens, and an
 * agent loop resends the whole history on every step. Every request stays under this.
 */
export const MAX_REQUEST_INPUT_TOKENS = 99_000;
const MAX_STEPS = 10;
const MAX_ITEMS = 12;

/** What the agent found during one run. Only these entries can back a logged item. */
export class Evidence {
  readonly records = new Map<string, { record: FoodRecord; verified: boolean }>();
  searches = 0;

  add(record: FoodRecord, verified = true) {
    if (!this.records.has(record.id)) this.records.set(record.id, { record, verified });
  }

  get(id: string) {
    return this.records.get(id);
  }
}

/** A logged food: nutrition per 100 g from its source, which the review screen scales by grams. */
export interface GroundedItem {
  name: string;
  grams: number;
  per100g: Nutrients;
  confidence: number;
  /** "ifct:A015", "components", "estimate", … */
  sourceId: string;
  /** "IFCT 2017 (ICMR-NIN)", "Built from 4 ingredients", "Estimate" */
  sourceName: string;
  /** The matched entry's own name ("Rice, white, basmati, boiled"), or the ingredients. */
  sourceDetail: string;
  /** False for estimates and web results whose numbers weren't found on the page. */
  verified: boolean;
}

export interface GroundedMeal {
  title: string;
  items: GroundedItem[];
  question: string;
  options: string[];
  notes: string;
}

const Per100gEstimate = z.object({
  kcal: z.number().min(0).max(950),
  protein: z.number().min(0).max(100),
  carbs: z.number().min(0).max(100),
  fat: z.number().min(0).max(100),
  fiber: z.number().min(0).max(100),
});

const SubmittedItem = z.object({
  name: z.string().min(1).max(60),
  grams: z.number().min(1).max(3000).describe("Portion as served, in grams (ml for drinks)"),
  sourceId: z.string().optional().describe("Id of the matching entry from a tool result"),
  components: z
    .array(z.object({ sourceId: z.string(), grams: z.number().min(0.5).max(3000) }))
    .max(12)
    .optional()
    .describe("For a dish built from ingredients: each ingredient's entry and its weight as it goes in (raw entries at raw weight)"),
  estimatePer100g: Per100gEstimate.optional().describe("Only when no entry could be found for this food"),
  confidence: z.number().min(0).max(1),
});

export const SubmitMealInput = z.object({
  title: z.string().max(80),
  items: z.array(SubmittedItem).max(MAX_ITEMS),
  question: z.string().max(120),
  options: z.array(z.string().max(40)).max(4),
  notes: z.string().max(200),
});
export type SubmitMealInput = z.infer<typeof SubmitMealInput>;

/**
 * Checks a submission against the evidence and resolves each item's nutrition. Returns the problems
 * to send back to the model, or the grounded meal.
 */
export function groundMeal(input: SubmitMealInput, evidence: Evidence): { problems: string[] } | { meal: GroundedMeal } {
  const problems: string[] = [];
  const items: GroundedItem[] = [];
  input.items.forEach((item, i) => {
    const label = `Item ${i + 1} (${item.name})`;
    const ways = [item.sourceId, item.components?.length ? item.components : undefined, item.estimatePer100g].filter((w) => w != null);
    if (ways.length !== 1) {
      problems.push(`${label}: give exactly one of sourceId, components or estimatePer100g.`);
      return;
    }
    if (item.sourceId) {
      const found = evidence.get(item.sourceId);
      if (!found) {
        problems.push(`${label}: ${item.sourceId} isn't from a tool result in this conversation. Search for it and use an id from the results.`);
        return;
      }
      items.push({
        name: item.name,
        grams: item.grams,
        per100g: rounded(found.record.per100g),
        confidence: item.confidence,
        sourceId: found.record.id,
        sourceName: SOURCE_NAMES[sourceOf(found.record.id)] ?? "Source",
        sourceDetail: found.record.name,
        verified: found.verified,
      });
      return;
    }
    if (item.components?.length) {
      const parts: { record: FoodRecord; grams: number }[] = [];
      for (const c of item.components) {
        const found = evidence.get(c.sourceId);
        if (!found) {
          problems.push(`${label}: component ${c.sourceId} isn't from a tool result. Search for the ingredient first.`);
          return;
        }
        parts.push({ record: found.record, grams: c.grams });
      }
      // Ingredients go in by their own weight (raw dal, oil); cooking adds or removes water, but
      // not more than this.
      const total = parts.reduce((s, p) => s + p.grams, 0);
      if (total < item.grams * 0.2 || total > item.grams * 2.5) {
        problems.push(`${label}: ingredients total ${Math.round(total)} g for a ${Math.round(item.grams)} g portion, which no cooking explains. Check the grams.`);
        return;
      }
      items.push({
        name: item.name,
        grams: item.grams,
        per100g: rounded(dishPer100g(parts.map((p) => ({ per100g: p.record.per100g, grams: p.grams })), item.grams)),
        confidence: item.confidence,
        sourceId: "components",
        sourceName: `Built from ${parts.length} ingredient${parts.length === 1 ? "" : "s"}`,
        sourceDetail: parts.map((p) => `${p.record.name} ${Math.round(p.grams)} g`).join("; "),
        verified: parts.every((p) => evidence.get(p.record.id)!.verified),
      });
      return;
    }
    if (evidence.searches === 0) {
      problems.push(`${label}: search for the food before giving an estimate.`);
      return;
    }
    const est = item.estimatePer100g!;
    items.push({
      name: item.name,
      grams: item.grams,
      per100g: rounded({ ...emptyNutrients(), ...est }),
      confidence: Math.min(item.confidence, 0.5),
      sourceId: "estimate",
      sourceName: "Estimate",
      sourceDetail: "No table had this food",
      verified: false,
    });
  });
  if (problems.length > 0) return { problems };
  return {
    meal: {
      title: input.title.trim() || items[0]?.name || "Meal",
      items,
      question: input.question.trim(),
      options: input.question.trim() ? input.options : [],
      notes: input.notes.trim(),
    },
  };
}

function describe(r: FoodRecord): string {
  const serving = r.servingGrams ? `; serving ${r.servingGrams} g` : "";
  return `${r.id} | ${r.name} | ${r.group}${serving} | per 100 g: ${brief(r.per100g)}`;
}

export interface MealRunDeps {
  /** Packaged product by barcode (our cache, then Open Food Facts). */
  lookupBarcode: (barcode: string) => Promise<FoodRecord | null>;
  /** Grounded web lookup, or null when it is unavailable or the daily allowance is used up. */
  webLookup: (query: string) => Promise<WebLookupResult | null>;
  /** Called with label values the model read, so the product can be cached by barcode. */
  onLabel?: (record: FoodRecord) => void;
  searchUsda?: typeof searchUsda;
}

/** Tools for one run, sharing its evidence. */
export function mealTools(evidence: Evidence, deps: MealRunDeps, state: { submitted: GroundedMeal | null; webUsed: boolean; hasImage: boolean; barcode?: string }) {
  const usda = deps.searchUsda ?? searchUsda;
  return {
    search_foods: createTool({
      id: "search_foods",
      description:
        "Search the nutrition tables for one food: IFCT 2017 (Indian), CoFID 2021 (UK, with many South Asian dishes) and Indian packaged foods, plus USDA FoodData Central when those have no close match or global is true. Returns entries with their ids and calories/macros per 100 g.",
      inputSchema: z.object({
        query: z.string().min(1).max(80).describe("Short food name, e.g. 'toor dal', 'chapati', 'basmati rice boiled'"),
        global: z.boolean().optional().describe("Also search USDA (global foods, US dishes)"),
      }),
      execute: async ({ query, global }) => {
        evidence.searches++;
        const local = searchLocal(query, { limit: 8 }).map((h) => h.record);
        const strong = searchLocal(query, { limit: 1 })[0]?.score ?? 0;
        const remote = global || strong < 0.85 ? await usda(query, 5) : [];
        const all = [...local, ...remote];
        all.forEach((r) => evidence.add(r));
        if (all.length === 0) return `No entries for "${query}". Try another name, a more general one, or its main ingredients.`;
        return all.map(describe).join("\n");
      },
    }),
    lookup_barcode: createTool({
      id: "lookup_barcode",
      description: "Look up a packaged product by its barcode (EAN/UPC digits). Returns the product's nutrition per 100 g and serving size if known.",
      inputSchema: z.object({ barcode: z.string().regex(/^\d{8,14}$/) }),
      execute: async ({ barcode }) => {
        const r = await deps.lookupBarcode(barcode);
        if (!r) return `No product found for ${barcode}. If there's a photo of the nutrition label, use read_label; otherwise search by product name.`;
        evidence.add(r);
        return describe(r);
      },
    }),
    read_label: createTool({
      id: "read_label",
      description:
        "Record the values printed on a nutrition label in the photo. Copy the numbers exactly as printed, for the basis the label uses (per 100 g, or per serving with its gram weight). Returns an entry id to use in submit_meal.",
      inputSchema: z.object({
        productName: z.string().min(1).max(80),
        basisGrams: z.number().min(1).max(1000).describe("100 for per-100 g columns, else the serving weight in grams"),
        kcal: z.number().min(0).max(5000),
        protein: z.number().min(0).max(1000),
        carbs: z.number().min(0).max(1000),
        fat: z.number().min(0).max(1000),
        fiber: z.number().min(0).max(1000).optional(),
        sugar: z.number().min(0).max(1000).optional(),
        satFat: z.number().min(0).max(1000).optional(),
        sodiumMg: z.number().min(0).max(100_000).optional(),
        servingGrams: z.number().min(1).max(3000).optional(),
      }),
      execute: async (v) => {
        if (!state.hasImage) return "There is no photo in this request, so there is no label to read.";
        const scale = 100 / v.basisGrams;
        const per100g = emptyNutrients();
        per100g.kcal = v.kcal * scale;
        per100g.protein = v.protein * scale;
        per100g.carbs = v.carbs * scale;
        per100g.fat = v.fat * scale;
        if (v.fiber != null) per100g.fiber = v.fiber * scale;
        if (v.sugar != null) per100g.sugar = v.sugar * scale;
        if (v.satFat != null) per100g.satFat = v.satFat * scale;
        if (v.sodiumMg != null) per100g.sodium = v.sodiumMg * scale;
        const macros = (per100g.protein ?? 0) + (per100g.carbs ?? 0) + (per100g.fat ?? 0);
        if (macros > 105 || (per100g.kcal ?? 0) > 950) {
          return "These values are more than 100 g of nutrients per 100 g of food. Check the basis (per 100 g or per serving) and the numbers.";
        }
        const record: FoodRecord = {
          id: `label:${state.barcode ?? v.productName.toLowerCase().replace(/[^a-z0-9]+/g, "-").slice(0, 40)}`,
          name: v.productName,
          group: "Nutrition label",
          aliases: [],
          per100g,
          servingGrams: v.servingGrams ?? (v.basisGrams !== 100 ? v.basisGrams : null),
        };
        evidence.add(record);
        deps.onLabel?.(record);
        return describe(record);
      },
    }),
    web_lookup: createTool({
      id: "web_lookup",
      description:
        "Last resort for restaurant, chain or branded foods that search_foods can't find: searches the web for the published nutrition facts. Once per meal.",
      inputSchema: z.object({ query: z.string().min(3).max(100).describe("Brand or restaurant and item, e.g. 'Haldiram's raj kachori'") }),
      execute: async ({ query }) => {
        if (state.webUsed) return "web_lookup was already used for this meal. Use a table entry or an estimate.";
        state.webUsed = true;
        const result = await deps.webLookup(query);
        if (!result) return `Nothing usable found on the web for "${query}". Use a close table entry, build it from ingredients, or estimate.`;
        evidence.add(result.record, result.verified);
        const check = result.verified ? "numbers confirmed on the page" : "numbers NOT confirmed on the page; it will show as unverified";
        return `${describe(result.record)} | ${result.url} (${check})`;
      },
    }),
    submit_meal: createTool({
      id: "submit_meal",
      description: "Submit the finished meal. Every item needs a sourceId or components from tool results, or (only if nothing was found) estimatePer100g.",
      inputSchema: SubmitMealInput,
      execute: async (input) => {
        const result = groundMeal(input, evidence);
        if ("problems" in result) return `Not accepted. Fix these and call submit_meal again:\n- ${result.problems.join("\n- ")}`;
        state.submitted = result.meal;
        return "Accepted.";
      },
    }),
  };
}

export const mealLogger = new Agent({
  id: "meal-logger",
  name: "Meal logger",
  description: "Identifies foods and portions in a meal and matches each to a nutrition table entry.",
  instructions: {
    role: "system",
    content: MEAL_LOGGER_SYSTEM,
    providerOptions: { anthropic: { cacheControl: { type: "ephemeral" } } },
  },
  model: ({ requestContext }) => ({
    id: `anthropic/${MEAL_MODEL_ID}`,
    apiKey: requestContext.get("anthropicApiKey") as string | undefined,
  }),
});

export interface MealRequest {
  /** JPEG, base64. */
  image?: string;
  text?: string;
  answer?: string;
  barcode?: string;
  mealType: string;
  localTime: string;
}

export interface MealRunUsage {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  steps: number;
  maxRequestInputTokens: number;
}

export class MealAgentError extends Error {}

function userContent(req: MealRequest) {
  const lines = [`Meal slot: ${req.mealType}. Local time: ${req.localTime.slice(0, 5)}.`];
  if (req.barcode) lines.push(`Barcode scanned: ${req.barcode}`);
  if (req.text) lines.push(`Description: ${req.text}`);
  if (req.answer) lines.push(`Answer to your earlier question: ${req.answer}`);
  if (!req.image) lines.push("No photo.");
  const content: ({ type: "image"; image: string; mediaType: string } | { type: "text"; text: string })[] = [];
  if (req.image) content.push({ type: "image", image: req.image, mediaType: "image/jpeg" });
  content.push({ type: "text", text: lines.join("\n") });
  return content;
}

interface StepUsage {
  inputTokens?: number;
  outputTokens?: number;
  cachedInputTokens?: number;
  cacheCreationInputTokens?: number;
  raw?: unknown;
}

interface RawInput {
  inputTokens?: { total?: number; cacheRead?: number; cacheWrite?: number };
}
const rawInput = (u: StepUsage) => (u.raw as RawInput | undefined)?.inputTokens;

/**
 * The whole prompt of one request. Haiku's price tier depends on all of it, cached or not. Providers
 * differ on whether inputTokens includes cache reads and writes, so without the raw total this adds
 * them, which can only overestimate.
 */
export function promptTokens(u: StepUsage): number {
  const total = rawInput(u)?.total;
  if (typeof total === "number") return total;
  return (u.inputTokens ?? 0) + (u.cachedInputTokens ?? 0) + (u.cacheCreationInputTokens ?? 0);
}

/** Rough tokens for text the model will read next step (tool results): ~3.5 chars per token. */
const tokensOf = (value: unknown) => Math.ceil(JSON.stringify(value ?? "").length / 3.5);

/**
 * Runs the meal agent once. The run ends when submit_meal accepts a meal, or before any request
 * would exceed [MAX_REQUEST_INPUT_TOKENS] (then only submit_meal stays available), or at MAX_STEPS.
 */
export async function runMealAgent(
  req: MealRequest,
  deps: MealRunDeps & { agent?: Agent; anthropicApiKey?: string; traceTags?: string[] },
): Promise<{ meal: GroundedMeal; usage: MealRunUsage }> {
  const evidence = new Evidence();
  const state = { submitted: null as GroundedMeal | null, webUsed: false, hasImage: !!req.image, barcode: req.barcode };
  const agent = deps.agent ?? mealLogger;
  const usage: MealRunUsage = { inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0, steps: 0, maxRequestInputTokens: 0 };

  const { RequestContext } = await import("@mastra/core/request-context");
  const requestContext = new RequestContext();
  if (deps.anthropicApiKey) requestContext.set("anthropicApiKey", deps.anthropicApiKey);

  // Pre-resolve a barcode so the first request already carries the product (one step saved).
  if (req.barcode) {
    const product = await deps.lookupBarcode(req.barcode).catch(() => null);
    if (product) evidence.add(product);
  }
  const preloaded = [...evidence.records.values()].map((e) => describe(e.record));
  const content = userContent(req);
  if (preloaded.length) content.push({ type: "text", text: `Product for the barcode:\n${preloaded.join("\n")}` });

  let projectedNext = 0;
  await agent.generate([{ role: "user", content }], {
    requestContext,
    toolsets: { meal: mealTools(evidence, deps, state) },
    maxSteps: MAX_STEPS,
    modelSettings: { maxOutputTokens: 8_000 },
    providerOptions: { anthropic: { effort: "medium" } },
    tracingOptions: { tags: ["meal", ...(deps.traceTags ?? [])] },
    stopWhen: () => state.submitted !== null,
    onStepFinish: (step: { usage?: StepUsage; toolResults?: unknown[]; content?: unknown }) => {
      const u = step.usage ?? {};
      const input = promptTokens(u);
      const cacheRead = rawInput(u)?.cacheRead ?? u.cachedInputTokens ?? 0;
      const cacheWrite = rawInput(u)?.cacheWrite ?? u.cacheCreationInputTokens ?? 0;
      usage.steps++;
      usage.inputTokens += Math.max(0, input - cacheRead - cacheWrite);
      usage.outputTokens += u.outputTokens ?? 0;
      usage.cacheReadTokens += cacheRead;
      usage.cacheWriteTokens += cacheWrite;
      usage.maxRequestInputTokens = Math.max(usage.maxRequestInputTokens, input);
      // The next request resends everything: this prompt, this step's output and its tool results.
      projectedNext = input + (u.outputTokens ?? 0) + tokensOf(step.toolResults) + 500;
    },
    prepareStep: () => {
      // Near the cap: only allow finishing. Past it: stop rather than pay the higher rate.
      if (projectedNext > MAX_REQUEST_INPUT_TOKENS) throw new MealAgentError("Meal analysis ran out of room.");
      if (projectedNext > MAX_REQUEST_INPUT_TOKENS * 0.85) return { activeTools: ["submit_meal"] };
      return undefined;
    },
  });

  if (!state.submitted) throw new MealAgentError("The meal analysis didn't finish.");
  return { meal: state.submitted, usage };
}

/** For callers that only have a local id (tests, the eval). */
export function evidenceFor(ids: string[]): Evidence {
  const e = new Evidence();
  for (const id of ids) {
    const r = localFood(id);
    if (r) e.add(r);
  }
  e.searches = 1;
  return e;
}
