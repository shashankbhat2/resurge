import { createHash } from "node:crypto";
import Anthropic from "@anthropic-ai/sdk";
import type { FoodRecord } from "./db";
import { emptyNutrients, type NutrientKey } from "./nutrients";

/**
 * Last-resort web lookup for foods no table has (restaurant and chain items, regional brands):
 * Anthropic server-side web search finds a page that lists the nutrition, then the server
 * fetches that page and keeps the result only if the numbers are actually on it. Uses the
 * existing ANTHROPIC_API_KEY. Search is capped at one per lookup.
 */
export const WEB_LOOKUP_MODEL = "claude-haiku-5-5";

const PROMPT = (query: string) => `Find the published nutrition facts for this food: "${query}".
Search the web before answering. Prefer the brand's or restaurant's own page, then a nutrition database page. Don't use recipe blogs.
Reply with exactly these lines and nothing else (use "?" for anything the page doesn't list):
NAME: <food as named on the page>
BASIS: <"100 g" or the serving size in grams, e.g. "1 piece (85 g)">
KCAL: <number>
PROTEIN_G: <number>
CARBS_G: <number>
FAT_G: <number>
FIBER_G: <number>
SUGAR_G: <number>
SODIUM_MG: <number>
URL: <the page these numbers come from>`;

export interface WebLookupResult {
  record: FoodRecord;
  url: string;
  /** The numbers were found on the fetched page. */
  verified: boolean;
}

interface Parsed {
  name: string;
  basisGrams: number;
  values: Partial<Record<NutrientKey, number>>;
  url: string | null;
}

export function parseLookup(text: string): Parsed | null {
  const line = (key: string) => text.match(new RegExp(`^\\s*${key}\\s*:\\s*(.+)$`, "mi"))?.[1]?.trim() ?? null;
  const num = (key: string) => {
    const v = line(key);
    const m = v?.replace(/,/g, "").match(/-?\d+(?:\.\d+)?/);
    return m ? Number(m[0]) : undefined;
  };
  const name = line("NAME");
  const basis = line("BASIS") ?? "";
  const grams = basis.match(/(\d+(?:\.\d+)?)\s*(g|gm|grams?|ml)\b/i);
  const basisGrams = grams ? Number(grams[1]) : /100/.test(basis) ? 100 : NaN;
  const kcal = num("KCAL");
  if (!name || !Number.isFinite(basisGrams) || basisGrams <= 0 || kcal == null) return null;
  return {
    name,
    basisGrams,
    url: line("URL")?.match(/https?:\/\/\S+/)?.[0] ?? null,
    values: {
      kcal,
      protein: num("PROTEIN_G"),
      carbs: num("CARBS_G"),
      fat: num("FAT_G"),
      fiber: num("FIBER_G"),
      sugar: num("SUGAR_G"),
      sodium: num("SODIUM_MG"),
    },
  };
}

/** Visible text of an HTML page, roughly: scripts, styles and tags removed, whitespace collapsed. */
export function pageText(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ");
}

/** Whether [value] appears on the page as a number (40 matches "40", "40.0", "40 g"; not "140"). */
export function numberOnPage(text: string, value: number): boolean {
  const variants = new Set([String(value), value.toFixed(1), value.toFixed(2), String(Math.round(value))]);
  for (const v of variants) {
    const re = new RegExp(`(^|[^\\d.])${v.replace(".", "\\.")}(?![\\d])`);
    if (re.test(text)) return true;
  }
  return false;
}

/** Energy and at least two of protein / carbs / fat must be on the page. */
export function verifyOnPage(text: string, values: Parsed["values"]): boolean {
  if (values.kcal == null || !numberOnPage(text, values.kcal)) return false;
  const macros = [values.protein, values.carbs, values.fat].filter((v): v is number => v != null);
  return macros.filter((v) => numberOnPage(text, v)).length >= 2;
}

async function fetchPage(url: string, fetchImpl: typeof fetch): Promise<string | null> {
  try {
    const res = await fetchImpl(url, {
      redirect: "follow",
      headers: { "user-agent": "Mozilla/5.0 (compatible; ResurgeNutritionCheck/1.0)" },
      signal: AbortSignal.timeout(6_000),
    });
    if (!res.ok) return null;
    const type = res.headers.get("content-type") ?? "";
    if (!/text\/html|text\/plain|application\/xhtml/.test(type)) return null;
    return pageText((await res.text()).slice(0, 2_000_000));
  } catch {
    return null;
  }
}

export function webId(query: string): string {
  return `web:${createHash("sha1").update(query.trim().toLowerCase()).digest("hex").slice(0, 16)}`;
}

/**
 * One grounded lookup. Returns null when nothing usable was found. The page check runs against the
 * cited source pages returned by Anthropic search; if none of them shows the
 * numbers, the result comes back unverified.
 */
async function anthropicWebLookup(
  query: string,
  opts: { apiKey: string; model?: string; fetchImpl?: typeof fetch; client?: Pick<Anthropic, "messages"> },
): Promise<WebLookupResult | null> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const client = opts.client ?? new Anthropic({ apiKey: opts.apiKey, timeout: 20_000, maxRetries: 0 });
  let text = "";
  let sources: string[] = [];
  try {
    const res = await client.messages.create({
      model: opts.model ?? WEB_LOOKUP_MODEL,
      // Haiku 5.5 thinks by default; with a small cap the answer was cut off before the numbers.
      max_tokens: 4000,
      output_config: { effort: "low" },
      messages: [{ role: "user", content: PROMPT(query) }],
      tools: [{ type: "web_search_20250305", name: "web_search", max_uses: 1 }],
    });
    if (res.stop_reason === "max_tokens") return null;
    text = res.content.filter((c) => c.type === "text").map((c) => c.text).join("\n");
    for (const block of res.content) {
      if (block.type === "web_search_tool_result" && Array.isArray(block.content)) {
        sources.push(...block.content.map((result) => result.url));
      }
    }
    // No search evidence means no usable lookup, even if the model supplied numbers and a URL.
    if (sources.length === 0) return null;
  } catch {
    return null;
  }
  return resolveWebResult(query, text, sources, fetchImpl);
}

async function resolveWebResult(query: string, text: string, sources: string[], fetchImpl: typeof fetch): Promise<WebLookupResult | null> {
  const parsed = parseLookup(text);
  if (!parsed) return null;

  const candidates = [...new Set([...(parsed.url && sources.includes(parsed.url) ? [parsed.url] : []), ...sources])].slice(0, 4);
  let verifiedUrl: string | null = null;
  for (const url of candidates) {
    const page = await fetchPage(url, fetchImpl);
    if (page && verifyOnPage(page, parsed.values)) {
      verifiedUrl = url;
      break;
    }
  }

  const per100g = emptyNutrients();
  const scale = 100 / parsed.basisGrams;
  for (const [k, v] of Object.entries(parsed.values) as [NutrientKey, number | undefined][]) {
    if (v != null) per100g[k] = v * scale;
  }
  const url = verifiedUrl ?? candidates[0] ?? "";
  let host = "";
  try {
    host = new URL(parsed.url ?? url).hostname.replace(/^www\./, "");
  } catch {
    host = "";
  }
  return {
    url,
    verified: verifiedUrl != null,
    record: {
      id: webId(query),
      name: parsed.name,
      group: `Web${host ? ` · ${host}` : ""}`,
      aliases: [],
      per100g,
      servingGrams: parsed.basisGrams !== 100 ? parsed.basisGrams : null,
    },
  };
}

/** Anthropic first; OpenRouter is only called for missing or unverified results. */
export async function webLookup(
  query: string,
  opts: {
    apiKey: string;
    model?: string;
    fetchImpl?: typeof fetch;
    client?: Pick<Anthropic, "messages">;
    openRouterApiKey?: string;
    openRouterModel?: string;
    openRouterFetch?: typeof fetch;
  },
): Promise<WebLookupResult | null> {
  const primary = await anthropicWebLookup(query, opts);
  if (primary?.verified || !opts.openRouterApiKey) return primary;
  const backup = await openRouterLookup(query, opts);
  return backup?.verified ? backup : primary ?? backup;
}

async function openRouterLookup(
  query: string,
  opts: { openRouterApiKey?: string; openRouterModel?: string; openRouterFetch?: typeof fetch; fetchImpl?: typeof fetch },
): Promise<WebLookupResult | null> {
  try {
    const response = await (opts.openRouterFetch ?? fetch)("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: { Authorization: `Bearer ${opts.openRouterApiKey}`, "Content-Type": "application/json" },
      signal: AbortSignal.timeout(20_000),
      body: JSON.stringify({
        model: opts.openRouterModel ?? "openai/gpt-4.1-mini",
        messages: [{ role: "user", content: PROMPT(query) }],
        max_tokens: 1500,
        max_tool_calls: 1,
        tools: [{ type: "openrouter:web_search", parameters: { engine: "parallel", mode: "basic", max_uses: 1, max_results: 3 } }],
      }),
    });
    if (!response.ok) return null;
    const data = await response.json() as { choices?: { message?: { content?: string; annotations?: { type?: string; url_citation?: { url?: string } }[] } }[] };
    const message = data.choices?.[0]?.message;
    const sources = (message?.annotations ?? []).filter((a) => a.type === "url_citation").map((a) => a.url_citation?.url).filter((url): url is string => !!url);
    if (!sources.length || typeof message?.content !== "string") return null;
    return await resolveWebResult(query, message.content, sources, opts.fetchImpl ?? fetch);
  } catch {
    return null;
  }
}
