import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { formatInsightList, loadEvents, loadHabits, loadProfile, loadRecentInsights, loadSummaries } from "../lib/data";
import { db, userDoc } from "../lib/firebase";
import { formatEvents, formatHabits, formatProfile, formatSummaries } from "../lib/format";
import { WEEKLY_INSIGHT_MODEL } from "../lib/models";
import { notifyInsight } from "../lib/push";
import { recordUsage } from "../lib/quota";
import { addDays, isSaturdayHour, isValidTimeZone, localParts, weekWindow } from "../lib/time";
import { WEEKLY_INSIGHT_SYSTEM } from "../prompts/insights";
import { generateStructured } from "./generate";
import { WEEKLY_INSIGHT_JSON_SCHEMA, WeeklyInsight } from "./schemas";

/** Local hour on Saturday when the weekly review is written. */
export const WEEKLY_HOUR = 19;

/**
 * Runs hourly on Saturdays and Sundays (UTC). For every user whose local time is Saturday 19:xx, writes the
 * Sunday–Saturday review once (doc id weekly-{saturday}), then notifies them.
 */
export const weeklyInsights = onSchedule(
  {
    // Saturday 19:00 local is between Sat 05:00 UTC (UTC+14) and Sun 07:00 UTC (UTC-12),
    // so run hourly on Saturday and Sunday UTC.
    schedule: "0 * * * 6,0",
    timeZone: "UTC",
    secrets: [ANTHROPIC_API_KEY],
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async () => {
    const now = new Date();
    const users = await db.collection("users").where("onboardingComplete", "==", true).get();
    const due = users.docs.filter((u) => {
      const tz = u.get("timezone");
      return isValidTimeZone(tz) && isSaturdayHour(tz, WEEKLY_HOUR, now);
    });
    logger.info("weeklyInsights", { users: users.size, due: due.length });
    // Sequential keeps Claude and Firestore load predictable; fine at this scale.
    for (const u of due) {
      try {
        await writeWeekly(u.id, localParts(u.get("timezone"), now).date);
      } catch (err) {
        logger.error("weekly insight failed", { uid: u.id, err: String(err) });
      }
    }
  },
);

export async function writeWeekly(uid: string, saturday: string): Promise<void> {
  const ref = userDoc(uid).collection("insights").doc(`weekly-${saturday}`);
  if ((await ref.get()).exists) return; // already written this week

  const { start, end } = weekWindow(saturday);
  const prevStart = addDays(start, -7);
  const startMs = Date.parse(`${start}T00:00:00Z`) - 14 * 3600_000; // generous: covers all timezones
  const [profile, thisWeek, lastWeek, events, insights, habits] = await Promise.all([
    loadProfile(uid),
    loadSummaries(uid, start, end),
    loadSummaries(uid, prevStart, addDays(start, -1)),
    loadEvents(uid, startMs, undefined, 60),
    loadRecentInsights(uid, startMs, 30),
    loadHabits(uid, start),
  ]);
  const weekDates = Array.from({ length: 7 }, (_, i) => addDays(start, i));
  if (thisWeek.length === 0) {
    logger.info("weekly insight skipped: no data", { uid, start, end });
    return;
  }
  const weekEvents = events.filter((e) => e.localDate >= start && e.localDate <= end);

  const context = [
    `<profile>\n${formatProfile(profile)}\n</profile>`,
    `<this_week start="${start}" end="${end}">\n${formatSummaries(thisWeek)}\n</this_week>`,
    `<workouts_and_sleep>\n${formatEvents(weekEvents)}\n</workouts_and_sleep>`,
    `<previous_week>\n${formatSummaries(lastWeek)}\n</previous_week>`,
    `<insights_this_week>\n${formatInsightList(insights)}\n</insights_this_week>`,
    `<habits_this_week>\n${formatHabits(habits, weekDates)}\n</habits_this_week>`,
    "Write the weekly review for this_week.",
  ].join("\n\n");

  const { value, usage } = await generateStructured({
    model: WEEKLY_INSIGHT_MODEL,
    system: WEEKLY_INSIGHT_SYSTEM,
    context,
    jsonSchema: WEEKLY_INSIGHT_JSON_SCHEMA,
    schema: WeeklyInsight,
    maxTokens: 16000,
  });

  await ref.create({
    kind: "weekly",
    ...value,
    title: value.headline,
    weekStart: start,
    weekEnd: end,
    model: usage.model,
    createdAt: FieldValue.serverTimestamp(),
  });
  await recordUsage(uid, saturday, usage);
  await notifyInsight(uid, "Your week in review", value.headline, ref.id);
}
