import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { ANTHROPIC_API_KEY } from "../lib/anthropic";
import { formatInsightList, loadEvents, loadProfile, loadRecentInsights, loadSummaries } from "../lib/data";
import { DATABASE, userDoc } from "../lib/firebase";
import { formatEvent, formatEvents, formatProfile, formatSummaries } from "../lib/format";
import { ACTIVITY_INSIGHT_MODEL, LIMITS } from "../lib/models";
import { notifyInsight } from "../lib/push";
import { recordUsage, tryConsume } from "../lib/quota";
import { addDays } from "../lib/time";
import type { ActivityEvent } from "../lib/types";
import { ACTIVITY_INSIGHT_SYSTEM } from "../prompts/insights";
import { generateStructured } from "./generate";
import { ACTIVITY_INSIGHT_JSON_SCHEMA, ActivityInsight } from "./schemas";

const DAY_MS = 24 * 60 * 60 * 1000;
/** Workouts finishing within this window of an earlier one share (and update) its insight. */
const COALESCE_MS = 30 * 60 * 1000;

/**
 * Fires when the app posts a finished workout or night of sleep (users/{uid}/events/{eventId}).
 * Writes one insight (or updates a just-written workout insight) and notifies the user.
 */
export const analyzeEvent = onDocumentCreated(
  {
    document: "users/{uid}/events/{eventId}",
    database: DATABASE,
    secrets: [ANTHROPIC_API_KEY],
    timeoutSeconds: 120,
    memory: "512MiB",
    retry: false,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const { uid, eventId } = event.params;
    const activity = snap.data() as ActivityEvent;
    const eventRef = snap.ref;

    try {
      if (!(await tryConsume(uid, activity.localDate, "activityInsights", LIMITS.activityInsights))) {
        await eventRef.update({ status: "skipped", reason: "daily-limit" });
        return;
      }

      // Coalesce back-to-back workouts into one insight.
      const recentWorkoutInsight =
        activity.type === "workout" ? await findRecentWorkoutInsight(uid, activity.startMs) : null;
      const eventIds = [...(recentWorkoutInsight?.eventIds ?? []), eventId];

      const context = await buildContext(uid, activity, recentWorkoutInsight?.eventIds ?? []);
      const { value, usage } = await generateStructured({
        model: ACTIVITY_INSIGHT_MODEL,
        system: ACTIVITY_INSIGHT_SYSTEM,
        context,
        jsonSchema: ACTIVITY_INSIGHT_JSON_SCHEMA,
        schema: ActivityInsight,
      });

      const insightsRef = userDoc(uid).collection("insights");
      const insightRef = recentWorkoutInsight ? insightsRef.doc(recentWorkoutInsight.id) : insightsRef.doc();
      await insightRef.set(
        {
          kind: activity.type === "sleep" ? "sleep" : "activity",
          ...value,
          eventIds,
          model: usage.model,
          createdAt: recentWorkoutInsight ? recentWorkoutInsight.createdAt : FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: false },
      );
      await eventRef.update({ status: "processed", insightId: insightRef.id });
      await recordUsage(uid, activity.localDate, usage);
      await notifyInsight(uid, value.title, value.body, insightRef.id);
    } catch (err) {
      logger.error("analyzeEvent failed", { uid, eventId, err: String(err) });
      await eventRef.update({ status: "failed", reason: String(err).slice(0, 300) });
    }
  },
);

async function findRecentWorkoutInsight(uid: string, startMs: number) {
  const snap = await userDoc(uid)
    .collection("insights")
    .where("kind", "==", "activity")
    .where("createdAt", ">=", Timestamp.fromMillis(startMs - COALESCE_MS))
    .orderBy("createdAt", "desc")
    .limit(1)
    .get();
  const doc = snap.docs[0];
  if (!doc) return null;
  return { id: doc.id, eventIds: (doc.get("eventIds") as string[]) ?? [], createdAt: doc.get("createdAt") as Timestamp };
}

async function buildContext(uid: string, activity: ActivityEvent, coalescedIds: string[]): Promise<string> {
  const today = activity.localDate;
  const [profile, summaries, sameType, otherType, insights] = await Promise.all([
    loadProfile(uid),
    loadSummaries(uid, addDays(today, -14), today),
    loadEvents(uid, activity.endMs - 45 * DAY_MS, activity.type, 8),
    loadEvents(uid, activity.endMs - 2 * DAY_MS, activity.type === "sleep" ? "workout" : "sleep", 4),
    loadRecentInsights(uid, activity.endMs - 7 * DAY_MS, 10),
  ]);
  const coalesced = sameType.filter((e) => coalescedIds.includes(e.id));
  const previous = sameType.filter((e) => e.hcId !== activity.hcId && !coalescedIds.includes(e.id)).slice(0, 5);

  return [
    `<profile>\n${formatProfile(profile)}\n</profile>`,
    `<just_finished>\n${[...coalesced, activity].map(formatEvent).join("\n")}\n</just_finished>`,
    `<recent_similar>\n${formatEvents(previous)}\n</recent_similar>`,
    activity.type === "sleep"
      ? `<recent_workouts>\n${formatEvents(otherType)}\n</recent_workouts>`
      : `<recent_sleep>\n${formatEvents(otherType)}\n</recent_sleep>`,
    `<last_14_days>\n${formatSummaries(summaries)}\n</last_14_days>`,
    `<insights_already_shown>\n${formatInsightList(insights)}\n</insights_already_shown>`,
    "Write the insight for the activity in <just_finished>. Avoid repeating points from <insights_already_shown>.",
  ].join("\n\n");
}
