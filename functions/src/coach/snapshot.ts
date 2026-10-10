import { formatInsightList, loadEvents, loadProfile, loadRecentInsights, loadSummaries } from "../lib/data";
import { formatEvents, formatProfile, formatSummaries } from "../lib/format";
import { addDays } from "../lib/time";

const DAY_MS = 24 * 60 * 60 * 1000;

/** Health snapshot given to the coach at thread start and refreshed (as a system message) on later days. */
export async function buildSnapshot(uid: string, today: string): Promise<string> {
  const [profile, summaries, events, insights] = await Promise.all([
    loadProfile(uid),
    loadSummaries(uid, addDays(today, -29), today),
    loadEvents(uid, Date.now() - 7 * DAY_MS, undefined, 30),
    loadRecentInsights(uid, Date.now() - 7 * DAY_MS, 10),
  ]);
  return [
    `<health_snapshot today="${today}">`,
    `<profile>\n${formatProfile(profile)}\n</profile>`,
    `<last_30_days>\n${formatSummaries(summaries)}\n</last_30_days>`,
    `<last_7_days_activities>\n${formatEvents(events)}\n</last_7_days_activities>`,
    `<recent_insights>\n${formatInsightList(insights)}\n</recent_insights>`,
    `</health_snapshot>`,
  ].join("\n");
}
