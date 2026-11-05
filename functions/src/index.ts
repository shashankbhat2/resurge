/**
 * Resurge Cloud Functions.
 * - analyzeEvent: activity/sleep insight when the app posts a finished workout or night (Firestore trigger)
 * - weeklyInsights: Saturday 19:00 local weekly review (scheduled)
 * - coachChat: streaming coach chat with Fast (Sonnet) / Smart (Opus) models (callable)
 * - analyzeMeal: foods, portions and nutrition from a meal photo or description (callable)
 * - mcp: read-only MCP server (Streamable HTTP) over the signed-in user's data for AI agents
 */
import { setGlobalOptions } from "firebase-functions/v2";

setGlobalOptions({ region: "us-central1", maxInstances: 10 });

export { analyzeEvent } from "./insights/activity";
export { weeklyInsights } from "./insights/weekly";
export { coachChat } from "./coach/chat";
export { analyzeMeal } from "./food/analyze";
export { mcp } from "./mcp/http";
