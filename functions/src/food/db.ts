import { readFileSync } from "node:fs";
import { join } from "node:path";
import { NUTRIENT_KEYS, nutrientsFrom, type Nutrients } from "./nutrients";

/**
 * The bundled food tables (data/food-db.json, built by scripts/build_food_db.py): IFCT 2017 for
 * Indian foods, CoFID 2021 for UK foods and South Asian dishes, and an Indian packaged-foods set.
 */
export interface FoodRecord {
  /** "ifct:A015", "cofid:19-454", "inpf:6", "fdc:171287", "off:8901063…", "label:…", "web:…" */
  id: string;
  name: string;
  group: string;
  aliases: string[];
  per100g: Nutrients;
  /** Grams in one stated serving (packaged foods), when known. */
  servingGrams?: number | null;
}

export type LocalSource = "ifct" | "cofid" | "inpf";

export const SOURCE_NAMES: Record<string, string> = {
  ifct: "IFCT 2017 (ICMR-NIN)",
  cofid: "CoFID 2021",
  inpf: "Indian packaged foods",
  fdc: "USDA FoodData Central",
  off: "Open Food Facts",
  label: "Nutrition label",
  web: "Web",
};

export const sourceOf = (id: string) => id.slice(0, id.indexOf(":"));

interface DbFile {
  nutrients: string[];
  foods: [string, string, string, string[], (number | null)[]][];
}

interface Indexed {
  record: FoodRecord;
  /** Words of the name, then of each local name, matched separately. */
  fields: string[][];
}

let indexed: Indexed[] | undefined;
let byId: Map<string, FoodRecord> | undefined;

function load(): Indexed[] {
  if (indexed) return indexed;
  const file = JSON.parse(readFileSync(join(__dirname, "../../data/food-db.json"), "utf8")) as DbFile;
  if (file.nutrients.join() !== NUTRIENT_KEYS.join()) throw new Error("food-db.json nutrient columns don't match NUTRIENT_KEYS");
  indexed = file.foods.map(([id, name, group, aliases, values]) => {
    const record: FoodRecord = { id, name, group, aliases, per100g: nutrientsFrom(values) };
    return { record, fields: [name, ...aliases].map((f) => [...new Set(tokenize(f))]).filter((f) => f.length > 0) };
  });
  byId = new Map(indexed.map((i) => [i.record.id, i.record]));
  return indexed;
}

export function localFood(id: string): FoodRecord | undefined {
  load();
  return byId!.get(id);
}

export function localFoodCount(): number {
  return load().length;
}

export function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .split(" ")
    .filter((t) => t.length > 1 && !STOP.has(t));
}

const STOP = new Set(["and", "with", "of", "the", "in", "a", "an", "or", "made", "type", "style", "fresh"]);

/**
 * Hindi and Hinglish food words → the words the tables use. Each query word also matches itself, so
 * this only adds alternatives; a multi-word alternative ("kidney bean") must match all its words.
 */
const SYNONYMS: Record<string, string[]> = {
  roti: ["chapati", "chapatis", "phulka"],
  chapati: ["roti", "chapatis"],
  phulka: ["chapati", "roti"],
  dal: ["dhal", "dahl", "lentil", "lentils", "daal"],
  daal: ["dal", "dhal", "dahl", "lentil"],
  dhal: ["dal", "dahl"],
  chana: ["chick pea", "chickpea", "chickpeas", "bengal gram", "channa"],
  channa: ["chana", "chick pea", "chickpea"],
  chole: ["chick pea", "chickpea", "chana", "channa"],
  chhole: ["chick pea", "chickpea", "chana"],
  rajma: ["kidney bean", "kidney beans", "rajmah"],
  dahi: ["yoghurt", "yogurt", "curd"],
  curd: ["dahi", "yoghurt", "yogurt"],
  chawal: ["rice"],
  aloo: ["potato", "potatoes", "alu"],
  gobi: ["cauliflower", "gobhi"],
  gobhi: ["cauliflower", "gobi"],
  palak: ["spinach"],
  saag: ["spinach", "mustard", "sag"],
  bhindi: ["okra", "ladies finger"],
  baingan: ["aubergine", "brinjal", "eggplant"],
  brinjal: ["aubergine", "eggplant", "baingan"],
  matar: ["pea", "peas", "mutter"],
  methi: ["fenugreek"],
  doodh: ["milk"],
  atta: ["whole wheat flour", "wholemeal flour", "ata"],
  maida: ["refined wheat flour", "white flour"],
  sooji: ["semolina", "suji", "rava"],
  suji: ["semolina", "sooji", "rava"],
  rava: ["semolina", "sooji"],
  poha: ["rice flakes", "flaked rice", "beaten rice"],
  besan: ["bengal gram flour", "gram flour", "chickpea flour"],
  moong: ["green gram", "mung"],
  mung: ["green gram", "moong"],
  masoor: ["red lentil", "lentil", "masur"],
  toor: ["red gram", "pigeon pea", "arhar", "tur"],
  arhar: ["red gram", "pigeon pea", "toor"],
  urad: ["black gram"],
  anda: ["egg", "eggs"],
  murgh: ["chicken"],
  murg: ["chicken"],
  gosht: ["mutton", "lamb", "goat"],
  mutton: ["goat", "lamb"],
  machli: ["fish"],
  macchi: ["fish"],
  kheera: ["cucumber"],
  pyaz: ["onion", "onions"],
  pyaaz: ["onion", "onions"],
  tamatar: ["tomato", "tomatoes"],
  gajar: ["carrot", "carrots"],
  lauki: ["bottle gourd"],
  karela: ["bitter gourd"],
  nimbu: ["lemon", "lime"],
  kela: ["banana"],
  aam: ["mango"],
  seb: ["apple"],
  chai: ["tea"],
  sabzi: ["bhaji", "curry", "vegetable", "subji"],
  sabji: ["bhaji", "curry", "vegetable", "subji"],
  subji: ["bhaji", "curry", "vegetable", "sabzi"],
  biriyani: ["biryani"],
  pulao: ["pilau", "pilaf", "pulav"],
  pulav: ["pilau", "pilaf", "pulao"],
  parantha: ["paratha"],
  poori: ["puri"],
  pakoda: ["pakora", "bhaji"],
  bhajia: ["pakora", "bhaji"],
  gur: ["jaggery"],
  cheeni: ["sugar"],
  shakkar: ["sugar"],
  makhana: ["fox nut", "lotus seed"],
  moongphali: ["groundnut", "peanut"],
  peanut: ["groundnut"],
  badam: ["almond", "almonds"],
  kaju: ["cashew"],
  ghee: ["butter ghee"],
  yogurt: ["yoghurt", "curd"],
  yoghurt: ["yogurt", "curd"],
  eggplant: ["aubergine", "brinjal"],
};

/** Edit distance ≤ 1 (one substitution, insertion or deletion). */
function nearMatch(a: string, b: string): boolean {
  if (Math.abs(a.length - b.length) > 1) return false;
  let i = 0;
  let j = 0;
  let edits = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      i++;
      j++;
      continue;
    }
    if (++edits > 1) return false;
    if (a.length > b.length) i++;
    else if (b.length > a.length) j++;
    else {
      i++;
      j++;
    }
  }
  return edits + (a.length - i) + (b.length - j) <= 1;
}

function wordScore(q: string, tokens: string[]): number {
  let best = 0;
  for (const t of tokens) {
    if (t === q) return 1;
    if (q.length >= 3 && t.startsWith(q)) best = Math.max(best, 0.8);
    else if (q.length >= 5 && nearMatch(q, t)) best = Math.max(best, 0.7);
    else if (q.length >= 4 && t.length >= 4 && q.startsWith(t)) best = Math.max(best, 0.6);
  }
  return best;
}

/** Best match of a query word or any of its synonyms (a synonym counts slightly less). */
function groupScore(alternatives: string[][], tokens: string[]): number {
  let best = 0;
  alternatives.forEach((words, i) => {
    const s = Math.min(...words.map((w) => wordScore(w, tokens))) * (i === 0 ? 1 : 0.9);
    best = Math.max(best, s);
  });
  return best;
}

const MIN_SCORE = 0.6;

export interface SearchHit {
  record: FoodRecord;
  score: number;
}

/**
 * Ranked matches for a free-text food name across the bundled tables. Every query word must match
 * something (directly, via a synonym, by prefix or with one typo); shorter, closer names rank first.
 * Each table gets a share of the results so Indian entries (IFCT) aren't crowded out by CoFID's
 * many variants.
 */
export function searchLocal(query: string, opts: { limit?: number; sources?: LocalSource[] } = {}): SearchHit[] {
  const words = tokenize(query);
  if (words.length === 0) return [];
  const groups = words.map((w) => [[w], ...(SYNONYMS[w] ?? []).map((alt) => tokenize(alt))]);
  const hits: SearchHit[] = [];
  for (const { record, fields } of load()) {
    if (opts.sources && !opts.sources.includes(sourceOf(record.id) as LocalSource)) continue;
    let best = 0;
    fields.forEach((tokens, i) => {
      let sum = 0;
      let matched = 0;
      for (const g of groups) {
        const s = groupScore(g, tokens);
        sum += s;
        if (s > 0) matched++;
      }
      // Allow one unmatched word in longer queries ("homemade", "plain", brand words).
      if (matched === 0 || matched < groups.length - (groups.length >= 3 ? 1 : 0)) return;
      // Prefer names close to the query's length: "Rice, boiled" over "Rice pudding, canned, with jam".
      const extra = Math.max(0, tokens.length - words.length);
      const score = (sum / groups.length - 0.04 * extra) * (i === 0 ? 1 : 0.95);
      best = Math.max(best, score);
    });
    if (best < MIN_SCORE) continue;
    hits.push({ record, score: best + (sourceOf(record.id) === "ifct" ? 0.02 : 0) });
  }
  hits.sort((a, b) => b.score - a.score || a.record.name.length - b.record.name.length);
  const limit = opts.limit ?? 9;
  const share = Math.max(2, Math.ceil(limit / 3));
  const picked: SearchHit[] = [];
  const perSource = new Map<string, number>();
  for (const h of hits) {
    const src = sourceOf(h.record.id);
    if ((perSource.get(src) ?? 0) >= share) continue;
    perSource.set(src, (perSource.get(src) ?? 0) + 1);
    picked.push(h);
    if (picked.length >= limit) break;
  }
  // Fill any room left by a source with few hits.
  for (const h of hits) {
    if (picked.length >= limit) break;
    if (!picked.includes(h)) picked.push(h);
  }
  return picked.sort((a, b) => b.score - a.score);
}
