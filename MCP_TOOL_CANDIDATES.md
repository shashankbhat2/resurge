# MCP tool candidates (resurge `functions/` backend)

The backend is Firebase Cloud Functions (TypeScript). The callable functions use Firebase Auth (`request.auth.uid`). Every data read is scoped to `users/{uid}` in the Firestore database `resurge`.

| Tool | What it does | Inputs | Output | Wraps |
|---|---|---|---|---|
| get_health_snapshot | Profile, last 30 days of summaries, 7 days of activities, recent insights and 14 days of habits | none | text block `<health_snapshot>` | `coach/snapshot.ts` `buildSnapshot(uid, today)` |
| get_profile | The user profile (goals, body, preferences) | none | text | `lib/data.ts` `loadProfile` + `lib/format.ts` `formatProfile` |
| get_daily_summaries | Daily totals (steps, distance, kcal, sleep stages, exercise, HR, weight) | from: string yyyy-MM-dd, to: string yyyy-MM-dd (at most 120 days) | text table | `coach/tools.ts` `runTool("get_daily_summaries")` → `loadSummaries`/`formatSummaries` |
| get_activities | Workouts and/or sleep nights, newest first | type: "workout"\|"sleep"\|"any", days: int 1-90 | text | `runTool("get_activities")` → `loadEvents`/`formatEvents` |
| get_insights | Insights the app already showed | days: int 1-60 | text list | `runTool("get_insights")` → `loadRecentInsights`/`formatInsightList` |
| get_meals | Logged meals with macros and daily totals | days: int 1-60 | text | `runTool("get_meals")` → `loadMeals`/`formatMeals` |
| get_workout_logs | Manually logged or edited workouts (sets, reps, RPE, notes) | days: int 1-90 | text | `runTool("get_workout_logs")` → `loadWorkoutLogs`/`formatWorkoutLogs` |
| get_habits | Active habits with streaks and a done/missed log (private habits show their cover name only) | days: int 1-60 | text | `lib/data.ts` `loadHabits` + `formatHabits` |

## Write tools (implemented)

Every write goes under `users/{uid}` for the authenticated user. Documents use the same shape the Android repositories write and pass the same checks as `firestore.rules`. The rules only allow known `source` values and fields (`hasOnly`), and the app edits these documents later. So MCP writes are stored as `source: "manual"`, with no extra marker field.

| Tool | Inputs | Writes | Mirrors |
|---|---|---|---|
| log_meal | type: breakfast\|lunch\|dinner\|snack; title?: string ≤80; at?: ISO time; items?: [{name, grams, kcal, protein, carbs, fat, fiber?} per 100 g] ≤20; or kcal, protein?, carbs?, fat?, fiber? totals | `meals/{auto}` {type, loggedAtMs, localDate, source, title, items, updatedAt} | `MealsRepository.save` |
| log_workout | activityId: ActivityCatalog id; durationMin: 1-1440; start?: ISO; title?, notes?; metrics? {distanceM, elevationM, avgHr, kcal, laps, poolLengthM, reps, rpe 1-10, exercises[{name, sets[{reps, weightKg?}]}]} | `workouts/{auto}` {source "manual", activityId, title, startMs, endMs, localDate, metrics, notes, deleted false, updatedAt} | `WorkoutsRepository.save` |
| log_weight | kg: 20-400; at?: ISO; note?: ≤120 | `weights/{auto}` {kg, atMs, localDate, note, source "manual", updatedAt} | `WeightRepository.save` (no Health Connect mirror) |
| log_water | ml: int ±5000, not 0; date?: yyyy-MM-dd (last 7 days) | `waterDays/{date}` {date, ml, updatedAt}, transactional, total kept in 0-20000 | `MealsRepository.addWater` |
| set_habit_status | habit: name or id; status: done\|missed\|clear; date?: current habit day or the one before | `habitDays/{date}` {date, entries.{habitId}, updatedAt} (merge) | `HabitsRepository.setStatus` |

Not exposed: creating or archiving habits (private habits need the phone's encryption key), weight goals, progress photos, and editing or deleting existing entries.

## Candidates not implemented in this pass
- **analyze_meal**: estimate foods and macros from a JPEG (base64) and/or text, plus mealType and localTime. Returns a `MealEstimate`. Wraps `food/analyze.ts` `analyzeMeal`. It calls Anthropic, which costs money and uses the `foodScans` quota.
- **coach_chat**: send a message to a coach thread, with text and model "fast"\|"smart". Wraps `coach/chat.ts` `coachChat`. It streams, writes thread turns and uses quota.
- `analyzeEvent` (Firestore trigger) and `weeklyInsights` (scheduled) can't be called on demand, so they aren't tools. Their output can be read through get_insights.
