import type { FoodRecord } from "./db";
import { emptyNutrients, type NutrientKey, type Nutrients } from "./nutrients";

/**
 * USDA FoodData Central (public domain, CC0): https://fdc.nal.usda.gov/api-guide
 * Free key from api.data.gov (secret USDA_FDC_API_KEY); DEMO_KEY works for development
 * (30 requests/hour).
 */

const BASE = "https://api.nal.usda.gov/fdc/v1";
/** Generic foods and mixed dishes (FNDDS has many Indian dishes); Branded is too noisy for search. */
const DATA_TYPES = ["Foundation", "SR Legacy", "Survey (FNDDS)"];

/** FDC nutrient ids → our keys. Values in search results are per 100 g for these data types. */
const NUTRIENT_IDS: Record<number, NutrientKey> = {
  1008: "kcal",
  1003: "protein",
  1005: "carbs",
  1004: "fat",
  1079: "fiber",
  2000: "sugar",
  1258: "satFat",
  1253: "cholesterol",
  1093: "sodium",
  1092: "potassium",
  1087: "calcium",
  1089: "iron",
  1090: "magnesium",
  1095: "zinc",
  1106: "vitA",
  1162: "vitC",
  1114: "vitD",
  1178: "vitB12",
  1177: "folate",
};
/** Foundation foods often report energy only as Atwater factors. */
const ENERGY_FALLBACK = [2047, 2048];

interface FdcNutrient {
  nutrientId?: number;
  value?: number;
  amount?: number;
  nutrient?: { id?: number };
}
interface FdcFood {
  fdcId: number;
  description: string;
  dataType?: string;
  foodCategory?: string | { description?: string };
  foodNutrients?: FdcNutrient[];
}

export function fdcNutrients(list: FdcNutrient[] = []): Nutrients {
  const n = emptyNutrients();
  let energyFallback: number | null = null;
  for (const fn of list) {
    const id = fn.nutrientId ?? fn.nutrient?.id;
    const value = fn.value ?? fn.amount;
    if (id == null || value == null || !Number.isFinite(value)) continue;
    const key = NUTRIENT_IDS[id];
    if (key) n[key] = value;
    else if (ENERGY_FALLBACK.includes(id) && energyFallback == null) energyFallback = value;
  }
  if (n.kcal == null) n.kcal = energyFallback;
  return n;
}

export function fdcRecord(food: FdcFood): FoodRecord {
  const category = typeof food.foodCategory === "string" ? food.foodCategory : food.foodCategory?.description;
  return {
    id: `fdc:${food.fdcId}`,
    name: food.description,
    group: [food.dataType, category].filter(Boolean).join(" · "),
    aliases: [],
    per100g: fdcNutrients(food.foodNutrients),
  };
}

let apiKey: () => string = () => process.env.USDA_FDC_API_KEY || "DEMO_KEY";

/** For tests and for wiring the Firebase secret at startup. */
export function setUsdaKey(get: () => string) {
  apiKey = get;
}

/** Search generic foods and dishes. Returns [] on any network or quota error (the agent moves on). */
export async function searchUsda(query: string, limit = 6, fetchImpl: typeof fetch = fetch): Promise<FoodRecord[]> {
  const url = `${BASE}/foods/search?api_key=${encodeURIComponent(apiKey())}`;
  try {
    const res = await fetchImpl(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ query, dataType: DATA_TYPES, pageSize: limit }),
      signal: AbortSignal.timeout(8_000),
    });
    if (!res.ok) return [];
    const body = (await res.json()) as { foods?: FdcFood[] };
    return (body.foods ?? []).map(fdcRecord).filter((r) => r.per100g.kcal != null);
  } catch {
    return [];
  }
}
