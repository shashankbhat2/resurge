/** Timezone helpers. Users' local time comes from the IANA zone the app saves on their profile. */

export interface LocalParts {
  date: string; // yyyy-MM-dd
  weekday: number; // 0 = Sunday … 6 = Saturday
  hour: number;
  minute: number;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export function localParts(timeZone: string, at: Date = new Date()): LocalParts {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(at);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return {
    date: `${get("year")}-${get("month")}-${get("day")}`,
    weekday: WEEKDAYS.indexOf(get("weekday")),
    hour: Number(get("hour")),
    minute: Number(get("minute")),
  };
}

/** True during the local hour [hour, hour+1) on Saturdays. */
export function isSaturdayHour(timeZone: string, hour: number, at: Date = new Date()): boolean {
  const p = localParts(timeZone, at);
  return p.weekday === 6 && p.hour === hour;
}

/** Adds days to a yyyy-MM-dd string (calendar arithmetic, no timezone involved). */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Sunday–Saturday week containing [date] (the week ends on Saturday). */
export function weekWindow(date: string): { start: string; end: string } {
  const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
  const start = addDays(date, -weekday);
  return { start, end: addDays(start, 6) };
}

export function isValidTimeZone(tz: unknown): tz is string {
  if (typeof tz !== "string" || tz.length === 0) return false;
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}
