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
}
