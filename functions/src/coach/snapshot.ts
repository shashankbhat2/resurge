import {
  formatInsightList,
  loadDayResults,
  loadDayScores,
  loadEvents,
  loadHabits,
  loadPlan,
  loadProfile,
  loadRecentInsights,
  loadSummaries,
} from "../lib/data";
import { formatDays, formatEvents, formatGoal, formatHabits, formatPlan, formatProfile, formatSummaries } from "../lib/format";
import { addDays } from "../lib/time";

const DAY_MS = 24 * 60 * 60 * 1000;

/** Health snapshot given to the coach at thread start and refreshed (as a system message) on later days. */
export async function buildSnapshot(uid: string, today: string): Promise<string> {
  const from = addDays(today, -13);
  const profile = await loadProfile(uid);
  const [summaries, events, insights, habits, plan, results, scores] = await Promise.all([
    loadSummaries(uid, addDays(today, -29), today),
    loadEvents(uid, Date.now() - 7 * DAY_MS, undefined, 30),
    loadRecentInsights(uid, Date.now() - 7 * DAY_MS, 10),
    loadHabits(uid, from),
    loadPlan(uid, profile.activePlanId),
    loadDayResults(uid, from, today),
    loadDayScores(uid, from, today),
  ]);
  const dates = Array.from({ length: 14 }, (_, i) => addDays(from, i));
  return [
    `<health_snapshot today="${today}">`,
    `<profile>\n${formatProfile(profile)}\n</profile>`,
    `<goal>\n${formatGoal(profile, today)}\n</goal>`,
    `<plan>\n${formatPlan(plan)}\n</plan>`,
    `<days_and_scores_last_14_days>\n${formatDays(dates, results, scores)}\n</days_and_scores_last_14_days>`,
    `<last_30_days>\n${formatSummaries(summaries)}\n</last_30_days>`,
    `<last_7_days_activities>\n${formatEvents(events)}\n</last_7_days_activities>`,
    `<recent_insights>\n${formatInsightList(insights)}\n</recent_insights>`,
    `<habits_last_14_days>\n${formatHabits(habits, dates)}\n</habits_last_14_days>`,
    `</health_snapshot>`,
  ].join("\n");
}
