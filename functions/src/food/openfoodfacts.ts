import type { FoodRecord } from "./db";
import { emptyNutrients, type NutrientKey, type Nutrients } from "./nutrients";

/**
 * Open Food Facts (ODbL): barcode lookups for packaged foods. No key; they ask for a descriptive
 * User-Agent and at most ~100 product reads per minute.
 */
const BASE = "https://world.openfoodfacts.org/api/v2/product";
const USER_AGENT = "Resurge/0.4 (Android health app; https://github.com/shashankbhat2/resurge-ai)";
const FIELDS = "code,product_name,product_name_en,brands,quantity,serving_size,nutriments,nutrition_data_per";

/** OFF stores _100g values in grams (energy in kcal); [factor] converts to our units. */
const MAP: { key: NutrientKey; off: string; factor: number; max: number }[] = [
  { key: "kcal", off: "energy-kcal", factor: 1, max: 950 },
  { key: "protein", off: "proteins", factor: 1, max: 100 },
  { key: "carbs", off: "carbohydrates", factor: 1, max: 100 },
  { key: "fat", off: "fat", factor: 1, max: 100 },
  { key: "fiber", off: "fiber", factor: 1, max: 100 },
  { key: "sugar", off: "sugars", factor: 1, max: 100 },
  { key: "satFat", off: "saturated-fat", factor: 1, max: 100 },
  { key: "cholesterol", off: "cholesterol", factor: 1e3, max: 3_000 },
  { key: "sodium", off: "sodium", factor: 1e3, max: 40_000 },
  { key: "potassium", off: "potassium", factor: 1e3, max: 10_000 },
  { key: "calcium", off: "calcium", factor: 1e3, max: 3_000 },
  { key: "iron", off: "iron", factor: 1e3, max: 100 },
  { key: "magnesium", off: "magnesium", factor: 1e3, max: 1_500 },
  { key: "zinc", off: "zinc", factor: 1e3, max: 100 },
  { key: "vitA", off: "vitamin-a", factor: 1e6, max: 30_000 },
  { key: "vitC", off: "vitamin-c", factor: 1e3, max: 3_000 },
  { key: "vitD", off: "vitamin-d", factor: 1e6, max: 500 },
  { key: "vitB12", off: "vitamin-b12", factor: 1e6, max: 1_000 },
  { key: "folate", off: "vitamin-b9", factor: 1e6, max: 5_000 },
];

export interface OffProduct {
  code?: string;
  product_name?: string;
  product_name_en?: string;
  brands?: string;
  quantity?: string;
  serving_size?: string;
  nutriments?: Record<string, unknown>;
}

/**
 * Per-100 g nutrients from an OFF product. Uses the as-sold values, falling back to "prepared"
 * values (e.g. instant noodles). Values outside what any food could contain are unit mistakes in
 * the crowd-sourced data and are dropped.
 */
export function offNutrients(nutriments: Record<string, unknown> = {}): Nutrients {
  const n = emptyNutrients();
  const read = (name: string): number | null => {
    for (const k of [`${name}_100g`, `${name}_prepared_100g`]) {
      const v = nutriments[k];
      const num = typeof v === "number" ? v : typeof v === "string" ? Number(v) : NaN;
      if (Number.isFinite(num)) return num;
    }
    return null;
  };
  for (const m of MAP) {
    const v = read(m.off);
    if (v == null || v < 0) continue;
    const value = v * m.factor;
    if (value > m.max) continue;
    n[m.key] = value;
  }
  if (n.kcal == null) {
    const kj = read("energy-kj") ?? read("energy");
    if (kj != null && kj > 0 && kj / 4.184 <= 950) n.kcal = kj / 4.184;
  }
  return n;
}

export function offRecord(p: OffProduct): FoodRecord | null {
  const name = (p.product_name_en || p.product_name || "").trim();
  if (!p.code || !name) return null;
  const brand = (p.brands ?? "").split(",")[0]?.trim() ?? "";
  const per100g = offNutrients(p.nutriments);
  if (per100g.kcal == null || per100g.protein == null || per100g.fat == null) return null;
  return {
    id: `off:${p.code}`,
    name: brand && !name.toLowerCase().includes(brand.toLowerCase()) ? `${brand} ${name}` : name,
    group: ["Packaged", p.quantity, p.serving_size ? `serving ${p.serving_size}` : null].filter(Boolean).join(" · "),
    aliases: [],
    per100g,
    servingGrams: servingGrams(p.serving_size),
  };
}

export function isBarcode(code: string): boolean {
  return /^\d{8,14}$/.test(code);
}

/**
 * The product for a barcode with usable nutrition, or null when OFF doesn't have it (OFF answers
 * 200 with status 0 for unknown codes) or the network fails.
 */
export async function lookupOff(barcode: string, fetchImpl: typeof fetch = fetch): Promise<FoodRecord | null> {
  if (!isBarcode(barcode)) return null;
  try {
    const res = await fetchImpl(`${BASE}/${barcode}.json?fields=${FIELDS}`, {
      headers: { "user-agent": USER_AGENT },
      signal: AbortSignal.timeout(6_000),
    });
    if (!res.ok) return null;
    const body = (await res.json()) as { status?: number; product?: OffProduct };
    if (body.status !== 1 || !body.product) return null;
    return offRecord({ ...body.product, code: body.product.code ?? barcode });
  } catch {
    return null;
  }
}

/** Grams in one serving, parsed from "70 g" / "1 pack (30g)"; null when not stated in grams/ml. */
export function servingGrams(serving: string | undefined): number | null {
  const m = serving?.match(/(\d+(?:\.\d+)?)\s*(g|gm|grams?|ml)\b/i);
  const v = m ? Number(m[1]) : NaN;
  return Number.isFinite(v) && v > 0 && v <= 3000 ? v : null;
}
