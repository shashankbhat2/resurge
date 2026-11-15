/** Shapes of the Firestore documents the functions read (written by the Android app). */

export interface DailySummary {
  date: string;
  steps?: number | null;
  distanceM?: number | null;
  kcalTotal?: number | null;
  kcalActive?: number | null;
  /** Time in bed across the night that ended on this date. */
  sleepMin?: number | null;
  /** Time actually asleep (awake stages removed). */
  asleepMin?: number | null;
  /** Null when the source didn't record deep/light/REM stages. */
  sleepStages?: { deep?: number; light?: number; rem?: number; awake?: number } | null;
  exerciseMin?: number | null;
  exerciseDay?: boolean | null;
  exerciseTypes?: string[] | null;
  /** Each Health Connect session that day, in time order (written by app builds from late 2026). */
  exerciseSessions?: ExerciseSessionSummary[] | null;
  hrAvg?: number | null;
  hrMin?: number | null;
  hrMax?: number | null;
  restingHr?: number | null;
  weightKg?: number | null;
  /** Cardio points the phone calculated for the day (uploaded by app builds from late 2026). */
  cardioPoints?: number | null;
  /** True when some cardio points were estimated from the activity type rather than measured. */
  cardioEstimated?: boolean | null;
}

export interface ExerciseSessionSummary {
  type: string;
  title?: string | null;
  /** Local start time, HH:mm. */
  start?: string | null;
  durationMin: number;
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
}
