import type Anthropic from "@anthropic-ai/sdk";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { ENFORCE_APP_CHECK } from "../coach/chat";
import { generateStructured, InsightGenerationError } from "../insights/generate";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { userDoc } from "../lib/firebase";
import { LIMITS, MEAL_ANALYSIS_MODEL } from "../lib/models";
import { consumeOrThrow, recordUsage } from "../lib/quota";
import { isValidTimeZone, localParts } from "../lib/time";
import { MEAL_ANALYSIS_SYSTEM } from "../prompts/food";
import { MEAL_ESTIMATE_JSON_SCHEMA, MealEstimate } from "./schema";

/** About 1.5 MB of JPEG once decoded; the app sends ~1568 px images well under this. */
const MAX_IMAGE_BASE64 = 2_000_000;

const Input = z
  .object({
    image: z.string().max(MAX_IMAGE_BASE64).optional(),
    text: z.string().trim().max(500).optional(),
    answer: z.string().trim().max(120).optional(),
    mealType: z.enum(["breakfast", "lunch", "dinner", "snack"]),
    localTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  })
  .refine((v) => !!v.image || !!v.text, { message: "Send a photo or a description" });

/** JPEG files start with FF D8 FF ("/9j/" in base64). */
function isJpeg(base64: string): boolean {
  return base64.startsWith("/9j/");
}

/**
 * Estimates a meal's foods, portions and nutrition from a photo and/or description. Returns a draft
 * for the app's review screen; nothing is stored until the person logs it.
 */
export const analyzeMeal = onCall(
  {
    secrets: [ANTHROPIC_API_KEY],
    enforceAppCheck: ENFORCE_APP_CHECK,
    timeoutSeconds: 90,
    memory: "512MiB",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to scan meals.");

    const parsed = Input.safeParse(request.data);
    if (!parsed.success) throw new HttpsError("invalid-argument", "Send a photo or a description of the meal.");
    const input = parsed.data;
    if (input.image && !isJpeg(input.image)) throw new HttpsError("invalid-argument", "Photos must be JPEG.");

    const user = (await userDoc(uid).get()).data() ?? {};
    const tz = isValidTimeZone(user.timezone) ? (user.timezone as string) : "UTC";
    const today = localParts(tz).date;
    await consumeOrThrow(uid, today, "foodScans", LIMITS.foodScans);

    // Image first, then the text, as the vision docs recommend.
    const content: Anthropic.Beta.BetaContentBlockParam[] = [];
    if (input.image) {
      content.push({ type: "image", source: { type: "base64", media_type: "image/jpeg", data: input.image } });
    }
    const lines = [`Meal slot: ${input.mealType}. Local time: ${input.localTime.slice(0, 5)}.`];
    if (input.text) lines.push(`Description: ${input.text}`);
    if (input.answer) lines.push(`Answer to your earlier question: ${input.answer}`);
    if (!input.image) lines.push("No photo; estimate from the description.");
    content.push({ type: "text", text: lines.join("\n") });

    try {
      const { value, usage } = await generateStructured({
        model: MEAL_ANALYSIS_MODEL,
        system: MEAL_ANALYSIS_SYSTEM,
        context: content,
        jsonSchema: MEAL_ESTIMATE_JSON_SCHEMA,
        schema: MealEstimate,
      });
      await recordUsage(uid, today, usage);
      if (value.items.length === 0) {
        throw new HttpsError("invalid-argument", "No food found. Try a clearer photo or describe the meal.");
      }
      return value;
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      logger.error("analyzeMeal failed", { uid, error: (e as Error).message });
      if (e instanceof InsightGenerationError) throw new HttpsError("internal", "Couldn't analyse the meal. Try again.");
      throw new HttpsError("unavailable", "Couldn't analyse the meal. Try again.");
    }
  },
);
