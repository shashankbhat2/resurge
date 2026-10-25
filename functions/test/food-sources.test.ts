import { describe, expect, it } from "vitest";
import { localFood, localFoodCount, searchLocal } from "../src/food/db";
import { dishPer100g, emptyNutrients } from "../src/food/nutrients";
import { lookupOff, offNutrients, servingGrams } from "../src/food/openfoodfacts";
import { fdcNutrients, searchUsda } from "../src/food/usda";
import { numberOnPage, pageText, parseLookup, verifyOnPage, webLookup } from "../src/food/websearch";

const top = (q: string) => searchLocal(q, { limit: 3 }).map((h) => h.record.id);

describe("bundled food tables", () => {
  it("loads IFCT, CoFID and Indian packaged foods with per-100 g values in our units", () => {
    expect(localFoodCount()).toBeGreaterThan(4000);
    const rice = localFood("ifct:A015")!; // Rice, raw, milled: energy given in kJ, minerals in g
    expect(rice.per100g.kcal).toBeCloseTo(356, 0);
    expect(rice.per100g.iron).toBeCloseTo(0.65, 2); // mg
    const spinach = localFood("ifct:C033")!;
    expect(spinach.per100g.folate).toBeCloseTo(142, 0); // µg
    expect(localFood("ifct:T013")!.per100g.kcal).toBeGreaterThan(850); // ghee: IFCT omits energy for fats
  });

  it("finds Indian foods by English, Hindi and Hinglish names", () => {
    expect(top("toor dal")[0]).toBe("ifct:B021");
    expect(top("rajma")[0]).toMatch(/^ifct:B0(18|19|20)$/);
    expect(top("ghee")[0]).toBe("ifct:T013");
    expect(top("roti")).toContain("cofid:11-459");
    expect(top("chicken biryani")[0]).toBe("cofid:19-454");
    expect(top("palak")).toContain("ifct:C033");
    // Organ meat must not come up for rajma (kidney beans).
    expect(searchLocal("rajma").some((h) => /kidneys?,/i.test(h.record.name) && !/bean/i.test(h.record.name))).toBe(false);
  });

  it("gives every table a share of the results", () => {
    const sources = new Set(searchLocal("banana", { limit: 9 }).map((h) => h.record.id.split(":")[0]));
    expect(sources.has("ifct")).toBe(true);
  });
});

describe("dish built from ingredients", () => {
  it("divides by the served weight so cooking water dilutes the values", () => {
    const dal = { ...emptyNutrients(), kcal: 335, protein: 22, carbs: 57, fat: 1.5, fiber: 9, iron: 3 };
    const per100g = dishPer100g([{ per100g: dal, grams: 60 }], 200);
    expect(per100g.kcal).toBeCloseTo(100.5, 1);
    expect(per100g.iron).toBeCloseTo(0.9, 2);
    expect(per100g.vitC).toBeNull();
  });
});

describe("Open Food Facts", () => {
  it("converts grams to our units and drops impossible crowd-sourced values", () => {
    const n = offNutrients({ "energy-kcal_100g": 384, proteins_100g: 8.2, fat_100g: 12.5, sodium_100g: 1.03, iron_100g: 6.9, "vitamin-c_100g": 0.012 });
    expect(n.kcal).toBe(384);
    expect(n.sodium).toBeCloseTo(1030, 0);
    expect(n.iron).toBeNull(); // 6.9 g iron per 100 g: someone typed mg into a g field
    expect(n.vitC).toBeCloseTo(12, 5);
    expect(offNutrients({ "energy-kcal_prepared_100g": 90 }).kcal).toBe(90);
  });

  it("treats status 0 as not found", async () => {
    const fake = (async () => new Response(JSON.stringify({ status: 0 }), { status: 200 })) as typeof fetch;
    expect(await lookupOff("8901063092358", fake)).toBeNull();
    expect(await lookupOff("12ab", fake)).toBeNull();
  });

  it("parses serving sizes", () => {
    expect(servingGrams("70 g")).toBe(70);
    expect(servingGrams("1 pack (30g)")).toBe(30);
    expect(servingGrams("2 biscuits")).toBeNull();
  });
});

describe("USDA FoodData Central", () => {
  it("maps nutrient ids and falls back to Atwater energy", () => {
    const n = fdcNutrients([
      { nutrientId: 1003, value: 5.7 },
      { nutrientId: 1093, value: 309 },
      { nutrientId: 2047, value: 210 },
    ]);
    expect(n).toMatchObject({ protein: 5.7, sodium: 309, kcal: 210 });
  });

  it("returns [] when the API fails", async () => {
    const failing = (async () => new Response("rate limited", { status: 429 })) as typeof fetch;
    expect(await searchUsda("dosa", 3, failing)).toEqual([]);
  });
});

describe("web lookup grounding", () => {
  const answer = `NAME: Raj Kachori\nBASIS: 1 piece (120 g)\nKCAL: 320\nPROTEIN_G: 8\nCARBS_G: 38\nFAT_G: 15\nFIBER_G: ?\nSUGAR_G: 6\nSODIUM_MG: 540\nURL: https://example.com/raj-kachori`;

  it("parses the reply and scales a per-serving basis to 100 g", async () => {
    const parsed = parseLookup(answer)!;
    expect(parsed.basisGrams).toBe(120);
    const page = `<html><body><h1>Raj Kachori</h1><td>Energy</td><td>320 kcal</td><td>Protein 8 g</td><td>Carbohydrate 38g</td><script>var x = 999</script></body></html>`;
    const client = { models: { generateContent: async () => ({ text: answer, candidates: [] }) } } as never;
    const fetchImpl = (async () => new Response(page, { headers: { "content-type": "text/html" } })) as typeof fetch;
    const result = (await webLookup("haldiram raj kachori", { apiKey: "x", client, fetchImpl }))!;
    expect(result.verified).toBe(true);
    expect(result.record.per100g.kcal).toBeCloseTo(266.7, 1);
    expect(result.record.servingGrams).toBe(120);
  });

  it("marks the result unverified when the page doesn't show the numbers", async () => {
    const client = { models: { generateContent: async () => ({ text: answer, candidates: [] }) } } as never;
    const fetchImpl = (async () => new Response("<p>Raj kachori recipe, 1400 kcal</p>", { headers: { "content-type": "text/html" } })) as typeof fetch;
    const result = (await webLookup("raj kachori", { apiKey: "x", client, fetchImpl }))!;
    expect(result.verified).toBe(false);
  });

  it("matches numbers on a page exactly, not as part of other numbers", () => {
    const text = pageText("<td>140 kcal</td><td>8.0 g</td>");
    expect(numberOnPage(text, 40)).toBe(false);
    expect(numberOnPage(text, 140)).toBe(true);
    expect(numberOnPage(text, 8)).toBe(true);
    expect(verifyOnPage(text, { kcal: 140, protein: 8, carbs: 3, fat: 1 })).toBe(false);
  });
});
