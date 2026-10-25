import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { db } from "../lib/firebase";
import type { FoodRecord } from "./db";
import { NUTRIENT_KEYS, nutrientsFrom, rounded } from "./nutrients";
import { isBarcode, lookupOff } from "./openfoodfacts";
import { webLookup, type WebLookupResult } from "./websearch";

/**
 * Shared caches in the resurge database (server-written; see firestore.rules):
 *   foodProducts/{barcode}  a packaged product: from Open Food Facts, or read off a label photo
 *   foodWebCache/{id}       a web lookup result by normalised query, for 30 days
 */
const products = () => db.collection("foodProducts");
const webCache = () => db.collection("foodWebCache");
const WEB_CACHE_DAYS = 30;

interface StoredProduct {
  id: string;
  name: string;
  group: string;
  values: (number | null)[];
  servingGrams: number | null;
  source: "off" | "label" | "web";
}

function toStored(r: FoodRecord, source: StoredProduct["source"]): StoredProduct {
  const n = rounded(r.per100g);
  return { id: r.id, name: r.name, group: r.group, values: NUTRIENT_KEYS.map((k) => n[k]), servingGrams: r.servingGrams ?? null, source };
}

function fromStored(s: StoredProduct): FoodRecord {
  return { id: s.id, name: s.name, group: s.group, aliases: [], per100g: nutrientsFrom(s.values), servingGrams: s.servingGrams };
}

/** Our cache first (products people scanned before, including label reads), then Open Food Facts. */
export async function lookupProduct(barcode: string): Promise<FoodRecord | null> {
  if (!isBarcode(barcode)) return null;
  try {
    const cached = await products().doc(barcode).get();
    if (cached.exists) return fromStored(cached.data() as StoredProduct);
  } catch (e) {
    logger.warn("foodProducts read failed", { barcode, error: (e as Error).message });
  }
  const off = await lookupOff(barcode);
  if (off) {
    products()
      .doc(barcode)
      .set({ ...toStored(off, "off"), updatedAt: FieldValue.serverTimestamp() })
      .catch((e) => logger.warn("foodProducts write failed", { barcode, error: (e as Error).message }));
  }
  return off;
}

/**
 * Saves values read off a nutrition label for [barcode], so the next scan of this product fills in
 * without a photo. Open Food Facts entries are never overwritten by a label read.
 */
export async function saveLabelProduct(barcode: string, record: FoodRecord, uid: string): Promise<void> {
  if (!isBarcode(barcode)) return;
  const ref = products().doc(barcode);
  await db.runTransaction(async (tx) => {
    const existing = await tx.get(ref);
    if (existing.exists && (existing.data() as StoredProduct).source === "off") return;
    tx.set(ref, { ...toStored({ ...record, id: `label:${barcode}` }, "label"), readBy: uid, updatedAt: FieldValue.serverTimestamp() });
  });
}

/** Web lookup with a shared 30-day cache, so the same restaurant item is only searched once. */
export async function cachedWebLookup(query: string, apiKey: string, id: string): Promise<WebLookupResult | null> {
  const ref = webCache().doc(id.replace(/^web:/, ""));
  try {
    const hit = await ref.get();
    const data = hit.data() as (StoredProduct & { url: string; verified: boolean; createdAtMs: number }) | undefined;
    if (data && Date.now() - data.createdAtMs < WEB_CACHE_DAYS * 86_400_000) {
      return { record: fromStored(data), url: data.url, verified: data.verified };
    }
  } catch (e) {
    logger.warn("foodWebCache read failed", { error: (e as Error).message });
  }
  const result = await webLookup(query, { apiKey });
  if (result) {
    ref
      .set({ ...toStored(result.record, "web"), query, url: result.url, verified: result.verified, createdAtMs: Date.now() })
      .catch((e) => logger.warn("foodWebCache write failed", { error: (e as Error).message }));
  }
  return result;
}
