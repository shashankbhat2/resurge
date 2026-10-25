import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { LIMITS } from "../lib/models";
import { recordUsage, tryConsume } from "../lib/quota";
import { flushTraces, mastra } from "../mastra";
import { MEAL_MODEL_ID, MealAgentError, runMealAgent, type MealRequest } from "./agent";
import { isBarcode } from "./openfoodfacts";
import { cachedWebLookup, lookupProduct, saveLabelProduct } from "./products";
import { toResponse, type MealResponse } from "./schema";
import { GEMINI_API_KEY, USDA_FDC_API_KEY } from "./secrets";
import { setUsdaKey } from "./usda";
import { webId } from "./websearch";

/** One analyzeMeal request after auth, validation and the daily limit (see analyze.ts). */
export async function analyzeMealRequest(input: MealRequest, uid: string, today: string): Promise<MealResponse> {
  setUsdaKey(() => USDA_FDC_API_KEY.value() || "DEMO_KEY");
  const geminiKey = GEMINI_API_KEY.value();
  try {
    const { meal, usage } = await runMealAgent(input, {
      agent: mastra().getAgent("mealLogger"),
      anthropicApiKey: ANTHROPIC_API_KEY.value(),
      traceTags: [input.image ? "photo" : input.barcode ? "barcode" : "text"],
      lookupBarcode: lookupProduct,
      webLookup: async (query) => {
        if (!geminiKey) return null;
        if (!(await tryConsume(uid, today, "webLookups", LIMITS.webLookups))) return null;
        return cachedWebLookup(query, geminiKey, webId(query));
      },
      onLabel: (record) => {
        if (input.barcode && isBarcode(input.barcode)) {
          saveLabelProduct(input.barcode, record, uid).catch((e) => logger.warn("label save failed", { error: (e as Error).message }));
        }
      },
    });
    await recordUsage(uid, today, {
      model: MEAL_MODEL_ID,
      inputTokens: usage.inputTokens,
      outputTokens: usage.outputTokens,
      cacheReadTokens: usage.cacheReadTokens,
      cacheWriteTokens: usage.cacheWriteTokens,
    });
    logger.info("analyzeMeal", { uid, steps: usage.steps, maxRequestInputTokens: usage.maxRequestInputTokens, items: meal.items.length });
    if (meal.items.length === 0) {
      throw new HttpsError("invalid-argument", "No food found. Try a clearer photo or describe the meal.");
    }
    return toResponse(meal);
  } catch (e) {
    if (e instanceof HttpsError) throw e;
    logger.error("analyzeMeal failed", { uid, error: (e as Error).message });
    if (e instanceof MealAgentError) throw new HttpsError("internal", "Couldn't analyse the meal. Try again.");
    throw new HttpsError("unavailable", "Couldn't analyse the meal. Try again.");
  } finally {
    await flushTraces();
  }
}
