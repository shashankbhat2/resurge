import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { ENFORCE_APP_CHECK } from "../coach/chat";
import { SOURCE_NAMES, sourceOf } from "./db";
import { NUTRIENT_KEYS, rounded } from "./nutrients";

const Input = z.object({ barcode: z.string().trim().regex(/^\d{8,14}$/) });

/**
 * A packaged product by barcode, with no model call: our product cache (including labels people
 * photographed before), then Open Food Facts. { found: false } tells the app to ask for a photo of
 * the nutrition label, which goes to analyzeMeal with the barcode.
 */
export const lookupBarcode = onCall({ enforceAppCheck: ENFORCE_APP_CHECK, timeoutSeconds: 20, memory: "256MiB" }, async (request) => {
  if (!request.auth?.uid) throw new HttpsError("unauthenticated", "Sign in to scan barcodes.");
  const parsed = Input.safeParse(request.data);
  if (!parsed.success) throw new HttpsError("invalid-argument", "That isn't a product barcode.");
  const { lookupProduct } = require("./products") as typeof import("./products");
  const product = await lookupProduct(parsed.data.barcode);
  if (!product) return { found: false };
  const n = rounded(product.per100g);
  return {
    found: true,
    product: {
      sourceId: product.id,
      sourceName: SOURCE_NAMES[sourceOf(product.id)] ?? "Source",
      name: product.name,
      servingGrams: product.servingGrams ?? null,
      nutrients: Object.fromEntries(NUTRIENT_KEYS.map((k) => [k, n[k]])),
    },
  };
});
