/** Shapes of the Firestore documents the functions read (written by the Android app). */

export interface DailySummary {
  date: string;
  steps?: number | null;
  distanceM?: number | null;
  kcalTotal?: number | null;
  kcalActive?: number | null;
  sleepMin?: number | null;
  sleepStages?: { deep?: number; light?: number; rem?: number; awake?: number } | null;
  exerciseMin?: number | null;
  exerciseDay?: boolean | null;
  exerciseTypes?: string[] | null;
  hrAvg?: number | null;
  hrMin?: number | null;
  hrMax?: number | null;
  restingHr?: number | null;
  weightKg?: number | null;
}

export interface WorkoutDetails {
  activity: string;
  title?: string | null;
  durationMin: number;
  distanceM?: number | null;
  activeKcal?: number | null;
  totalKcal?: number | null;
  avgHr?: number | null;
  maxHr?: number | null;
  steps?: number | null;
  paceSecPerKm?: number | null;
}

export interface SleepDetails {
  durationMin: number;
  asleepMin: number;
  deepMin: number;
  lightMin: number;
  remMin: number;
  awakeMin: number;
  efficiency?: number | null;
  bedtime: string;
  wake: string;
  avgHr?: number | null;
  minHr?: number | null;
}

export interface ActivityEvent {
  type: "workout" | "sleep";
  hcId: string;
  startMs: number;
  endMs: number;
  timezone: string;
  localDate: string;
  workout?: WorkoutDetails | null;
  sleep?: SleepDetails | null;
  source?: { kind?: string; device?: string | null; app?: string | null } | null;
  status?: "pending" | "processed" | "skipped" | "failed";
}

export interface UserProfile {
  displayName?: string | null;
  goals?: string[];
  targets?: { calories?: number; waterMl?: number; sleepMinutes?: number; steps?: number };
  timezone?: string;
  coachStyle?: string;
  /** The major goal chosen in onboarding. */
  goal?: {
    type?: string;
    weeks?: number;
    startDate?: string;
    startKg?: number | null;
    targetKg?: number | null;
    raceKm?: number | null;
  } | null;
  training?: { days?: string[]; location?: string; sessionMin?: number; experience?: string; style?: string; runs?: string } | null;
  activePlanId?: string | null;
  /** What a day needs to count for the streak, and good days a week asks for. */
  dayGoals?: { sleep?: boolean; steps?: boolean; meals?: number; water?: boolean; weeklyDays?: number } | null;
}

/** users/{uid}/dayResults/{date}: whether the day met its goals (the streak). */
export interface DayResult {
  date: string;
  met: boolean;
  done?: number;
  total?: number;
}

/** users/{uid}/dayScores/{date}: the app's Recovery, Effort and Fuel (0-100) with a sentence on why. */
export interface DayScore {
  date: string;
  recovery?: number | null;
  effort?: number | null;
  fuel?: number | null;
  recoveryWhy?: string | null;
  effortWhy?: string | null;
  fuelWhy?: string | null;
  effortLow?: number | null;
  effortHigh?: number | null;
  /** Today's Effort and Fuel before the day is done. */
  soFar?: boolean | null;
}

/** users/{uid}/plans/{id}: the weekly plan, Monday first. */
export interface StoredPlan {
  name?: string;
  summary?: string;
  days?: {
    day: string;
    rest: boolean;
    title: string;
    kind?: string;
    durationMin?: number;
    focus?: string;
    target?: string;
    note?: string;
    exercises?: { name: string; sets: number; reps: string; load: string }[];
  }[];
  progression?: string;
}
