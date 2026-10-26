/** The FAQ on Home, also given to search engines as FAQPage structured data. */
export const FAQS: [question: string, answer: string][] = [
  ['When can I get it?', "Resurge is coming to Google Play. Join the waitlist and you'll get an invite to the test before it's out for everyone. The app is open source under the GPL-3.0."],
  ['Does it work with my watch?', 'Anything that writes to Health Connect: Fitbit, Pixel Watch, Samsung Health, Garmin and more. Workouts that show up from two sources are merged into one.'],
  ['Where does my data go?', 'Your logs are stored under your account with per-user security rules, so only you can read them. The coach runs in Cloud Functions that call Claude; no API keys ship in the app. Or run your own backend.'],
  ['Which phones are supported?', 'Android 8.0 or later. Steps, sleep and heart rate need Health Connect, which is built into Android 14 and newer and a free install before that.'],
  ['Is there an iPhone version?', 'Not yet. Resurge is Android only for now.'],
];
