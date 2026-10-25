import { createHash } from "node:crypto";
import { GoogleGenAI } from "@google/genai";
import type { FoodRecord } from "./db";
import { emptyNutrients, type NutrientKey } from "./nutrients";

/**
 * Last-resort web lookup for foods no table has (restaurant and chain items, regional brands):
 * Gemini with Grounding with Google Search finds a page that lists the nutrition, then the server
 * fetches that page and keeps the result only if the numbers are actually on it. Uses the free
 * GEMINI_API_KEY from Google AI Studio.
 */
export const WEB_LOOKUP_MODEL = "gemini-3.5-flash-lite";

const PROMPT = (query: string) => `Find the published nutrition facts for this food: "${query}".
Prefer the brand's or restaurant's own page, then a nutrition database page. Don't use recipe blogs.
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
 * cited source pages (Gemini's grounding chunks, plus the URL it named); if none of them shows the
 * numbers, the result comes back unverified.
 */
export async function webLookup(
  query: string,
  opts: { apiKey: string; model?: string; fetchImpl?: typeof fetch; client?: Pick<GoogleGenAI, "models"> },
): Promise<WebLookupResult | null> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const client = opts.client ?? new GoogleGenAI({ apiKey: opts.apiKey });
  let text = "";
  let sources: string[] = [];
  try {
    const res = await client.models.generateContent({
      model: opts.model ?? WEB_LOOKUP_MODEL,
      contents: PROMPT(query),
      config: { tools: [{ googleSearch: {} }], abortSignal: AbortSignal.timeout(20_000) },
    });
    text = res.text ?? "";
    sources = (res.candidates?.[0]?.groundingMetadata?.groundingChunks ?? [])
      .map((c) => c.web?.uri)
      .filter((u): u is string => !!u);
  } catch {
    return null;
  }
  const parsed = parseLookup(text);
  if (!parsed) return null;

  const candidates = [...new Set([parsed.url, ...sources].filter((u): u is string => !!u))].slice(0, 4);
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
