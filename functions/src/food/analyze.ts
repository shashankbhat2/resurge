import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { ENFORCE_APP_CHECK } from "../coach/chat";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { userDoc } from "../lib/firebase";
import { LIMITS } from "../lib/models";
import { consumeOrThrow } from "../lib/quota";
import { isValidTimeZone, localParts } from "../lib/time";
import { GEMINI_API_KEY, MASTRA_PLATFORM_ACCESS_TOKEN, MASTRA_PROJECT_ID, USDA_FDC_API_KEY } from "./secrets";

/** About 1.5 MB of JPEG once decoded; the app sends ~1568 px images well under this. */
const MAX_IMAGE_BASE64 = 2_000_000;

const Input = z
  .object({
    image: z.string().max(MAX_IMAGE_BASE64).optional(),
    text: z.string().trim().max(500).optional(),
    answer: z.string().trim().max(120).optional(),
    barcode: z.string().trim().regex(/^\d{8,14}$/).optional(),
    mealType: z.enum(["breakfast", "lunch", "dinner", "snack"]),
    localTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  })
  .refine((v) => !!v.image || !!v.text || !!v.barcode, { message: "Send a photo, a description or a barcode" });

/** JPEG files start with FF D8 FF ("/9j/" in base64). */
function isJpeg(base64: string): boolean {
  return base64.startsWith("/9j/");
}

/**
 * Logs a meal from a photo, description, nutrition label or barcode. A Mastra agent on Claude Haiku
 * identifies the foods and portions and matches each to a nutrition table entry (IFCT, CoFID,
 * Indian packaged foods, USDA, Open Food Facts, a label, or a checked web page); the numbers come
 * from those entries, not from the model. Returns a draft for the review screen; nothing is stored
 * until the person logs it.
 */
export const analyzeMeal = onCall(
  {
    secrets: [ANTHROPIC_API_KEY, USDA_FDC_API_KEY, GEMINI_API_KEY, MASTRA_PLATFORM_ACCESS_TOKEN, MASTRA_PROJECT_ID],
    enforceAppCheck: ENFORCE_APP_CHECK,
    timeoutSeconds: 120,
    memory: "1GiB",
  },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in to log meals.");

    const parsed = Input.safeParse(request.data);
    if (!parsed.success) throw new HttpsError("invalid-argument", "Send a photo, a description or a barcode of the meal.");
    const input = parsed.data;
    if (input.image && !isJpeg(input.image)) throw new HttpsError("invalid-argument", "Photos must be JPEG.");

    const user = (await userDoc(uid).get()).data() ?? {};
    const tz = isValidTimeZone(user.timezone) ? (user.timezone as string) : "UTC";
    const today = localParts(tz).date;
    await consumeOrThrow(uid, today, "foodScans", LIMITS.foodScans);

    // Required here, not imported at the top, so the other functions in this codebase don't load
    // Mastra (most of a second) at cold start.
    const { analyzeMealRequest } = require("./run") as typeof import("./run");
    return analyzeMealRequest(input, uid, today);
  },
);
