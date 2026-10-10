import { logger } from "firebase-functions";
import { messaging, userDoc } from "./firebase";

/**
 * Sends a notification to every device the user registered (users/{uid}/devices/{token}).
 * Tapping it opens the app on the Coach › Insights tab. Dead tokens are cleaned up.
 */
export async function notifyInsight(uid: string, title: string, body: string, insightId: string): Promise<void> {
  const devices = await userDoc(uid).collection("devices").get();
  if (devices.empty) return;
  const tokens = devices.docs.map((d) => d.id);
  const res = await messaging().sendEachForMulticast({
    tokens,
    notification: { title, body: body.length > 180 ? `${body.slice(0, 177)}…` : body },
    data: { open: "insights", insightId },
    android: { priority: "normal", notification: { channelId: "insights" } },
  });
  await Promise.all(
    res.responses.map(async (r, i) => {
      const code = r.error?.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        await userDoc(uid).collection("devices").doc(tokens[i]).delete();
      } else if (r.error) {
        logger.warn("FCM send failed", { uid, code });
      }
    }),
  );
}
