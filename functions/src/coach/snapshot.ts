import { formatInsightList, loadEvents, loadHabits, loadProfile, loadRecentInsights, loadSummaries } from "../lib/data";
import { formatEvents, formatHabits, formatProfile, formatSummaries } from "../lib/format";
import { addDays } from "../lib/time";

const DAY_MS = 24 * 60 * 60 * 1000;

/** Health snapshot given to the coach at thread start and refreshed (as a system message) on later days. */
export async function buildSnapshot(uid: string, today: string): Promise<string> {
  const from = addDays(today, -13);
  const [profile, summaries, events, insights, habits] = await Promise.all([
    loadProfile(uid),
    loadSummaries(uid, addDays(today, -29), today),
    loadEvents(uid, Date.now() - 7 * DAY_MS, undefined, 30),
    loadRecentInsights(uid, Date.now() - 7 * DAY_MS, 10),
    loadHabits(uid, from),
  ]);
  const dates = Array.from({ length: 14 }, (_, i) => addDays(from, i));
  return [
    `<health_snapshot today="${today}">`,
    `<profile>\n${formatProfile(profile)}\n</profile>`,
    `<last_30_days>\n${formatSummaries(summaries)}\n</last_30_days>`,
    `<last_7_days_activities>\n${formatEvents(events)}\n</last_7_days_activities>`,
    `<recent_insights>\n${formatInsightList(insights)}\n</recent_insights>`,
    `<habits_last_14_days>\n${formatHabits(habits, dates)}\n</habits_last_14_days>`,
    `</health_snapshot>`,
  ].join("\n");
}
