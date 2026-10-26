/**
 * Resurge Cloud Functions.
 * - analyzeEvent: activity/sleep insight when the app posts a finished workout or night (Firestore trigger)
 * - weeklyInsights: Saturday 19:00 local weekly review (scheduled)
 * - coachChat: streaming coach chat with Fast (Sonnet) / Smart (Opus) models (callable)
 * - analyzeMeal: foods, portions and nutrition from a photo, description, label or barcode, matched
 *   to nutrition tables by a Mastra agent (callable)
 * - lookupBarcode: a packaged product's nutrition by barcode, no model call (callable)
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "us-central1", maxInstances: 10 });

export { analyzeEvent } from "./insights/activity";
export { weeklyInsights } from "./insights/weekly";
export { coachChat } from "./coach/chat";
export { analyzeMeal } from "./food/analyze";
export { lookupBarcode } from "./food/barcode";
