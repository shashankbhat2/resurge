import { describe, expect, it } from "vitest";
import { addDays, isSaturdayHour, isValidTimeZone, localParts, weekWindow } from "../src/lib/time";

describe("time", () => {
  it("reads local date, weekday and hour in a timezone", () => {
    // 2026-10-10T13:30Z is Saturday 19:00 in Kolkata (UTC+5:30).
    const at = new Date("2026-10-10T13:30:00Z");
    expect(localParts("Asia/Kolkata", at)).toEqual({ date: "2026-10-10", weekday: 6, hour: 19, minute: 0 });
    expect(localParts("America/Los_Angeles", at)).toMatchObject({ date: "2026-10-10", weekday: 6, hour: 6 });
  });

  it("detects Saturday 19:xx local only", () => {
    expect(isSaturdayHour("Asia/Kolkata", 19, new Date("2026-10-10T13:30:00Z"))).toBe(true);
    expect(isSaturdayHour("Asia/Kolkata", 19, new Date("2026-10-10T14:30:00Z"))).toBe(false);
    // UTC-12 hits Saturday 19:00 on Sunday 07:00 UTC — inside the Sat/Sun UTC schedule window.
    expect(isSaturdayHour("Etc/GMT+12", 19, new Date("2026-10-11T07:00:00Z"))).toBe(true);
    // UTC+14 hits it on Saturday 05:00 UTC.
    expect(isSaturdayHour("Pacific/Kiritimati", 19, new Date("2026-10-10T05:00:00Z"))).toBe(true);
  });

  it("builds Sunday–Saturday weeks", () => {
    expect(weekWindow("2026-10-10")).toEqual({ start: "2026-10-04", end: "2026-10-10" }); // Saturday
    expect(weekWindow("2026-10-04")).toEqual({ start: "2026-10-04", end: "2026-10-10" }); // Sunday
    expect(weekWindow("2026-10-07")).toEqual({ start: "2026-10-04", end: "2026-10-10" }); // Wednesday
  });

  it("adds days across month boundaries", () => {
    expect(addDays("2026-10-01", -1)).toBe("2026-09-30");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });

  it("validates timezones", () => {
    expect(isValidTimeZone("Europe/London")).toBe(true);
    expect(isValidTimeZone("Mars/Base")).toBe(false);
    expect(isValidTimeZone(undefined)).toBe(false);
  });
});
