import { defineSecret } from "firebase-functions/params";

/**
 * Secrets for meal logging, kept apart from the modules that use them so declaring a function's
 * secrets doesn't load Mastra (which adds most of a second to every cold start).
 *   firebase functions:secrets:set USDA_FDC_API_KEY              free key from api.data.gov
 *   firebase functions:secrets:set MASTRA_PLATFORM_ACCESS_TOKEN  projects.mastra.ai, for traces
 *   firebase functions:secrets:set MASTRA_PROJECT_ID
 */
export const USDA_FDC_API_KEY = defineSecret("USDA_FDC_API_KEY");
export const MASTRA_PLATFORM_ACCESS_TOKEN = defineSecret("MASTRA_PLATFORM_ACCESS_TOKEN");
export const MASTRA_PROJECT_ID = defineSecret("MASTRA_PROJECT_ID");

/** Optional backup; configure before deploying with this secret bound. */
export const OPENROUTER_API_KEY = defineSecret("OPENROUTER_API_KEY");
