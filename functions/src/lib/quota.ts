import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { userDoc } from "./firebase";
import type { UsageSummary } from "./anthropic";

export type QuotaField = "fastTurns" | "smartTurns" | "activityInsights" | "foodScans" | "webLookups";

/**
 * Atomically consumes one unit of [field] for the user's local [date]. Returns false (and consumes
 * nothing) when the daily [limit] is already reached.
 */
export async function tryConsume(uid: string, date: string, field: QuotaField, limit: number): Promise<boolean> {
  const ref = userDoc(uid).collection("usage").doc(date);
  return ref.firestore.runTransaction(async (tx) => {
    const used = ((await tx.get(ref)).get(field) as number | undefined) ?? 0;
    if (used >= limit) return false;
    tx.set(ref, { [field]: used + 1, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    return true;
  });
}

export async function consumeOrThrow(uid: string, date: string, field: QuotaField, limit: number): Promise<void> {
  if (!(await tryConsume(uid, date, field, limit))) {
    throw new HttpsError("resource-exhausted", "Daily limit reached. It resets at midnight.");
  }
}

/** Adds token usage to the day's totals (for cost tracking). */
export async function recordUsage(uid: string, date: string, usage: UsageSummary): Promise<void> {
  await userDoc(uid)
    .collection("usage")
    .doc(date)
    .set(
      {
        inputTokens: FieldValue.increment(usage.inputTokens),
        outputTokens: FieldValue.increment(usage.outputTokens),
        cacheReadTokens: FieldValue.increment(usage.cacheReadTokens),
        cacheWriteTokens: FieldValue.increment(usage.cacheWriteTokens),
      },
      { merge: true },
    );
}
