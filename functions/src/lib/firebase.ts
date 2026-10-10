import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const app = getApps()[0] ?? initializeApp();

/** Resurge shares its Firebase project with other apps, so all data lives in this named database. */
export const DATABASE = "resurge";
export const db = getFirestore(app, DATABASE);
export const messaging = () => getMessaging(app);

export const userDoc = (uid: string) => db.collection("users").doc(uid);
