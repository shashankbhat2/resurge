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

## Candidates not implemented in this pass
- **analyze_meal**: estimate foods and macros from a JPEG (base64) and/or text, plus mealType and localTime. Returns a `MealEstimate`. Wraps `food/analyze.ts` `analyzeMeal`. It calls Anthropic, which costs money and uses the `foodScans` quota.
- **coach_chat**: send a message to a coach thread, with text and model "fast"\|"smart". Wraps `coach/chat.ts` `coachChat`. It streams, writes thread turns and uses quota.
- `analyzeEvent` (Firestore trigger) and `weeklyInsights` (scheduled) can't be called on demand, so they aren't tools. Their output can be read through get_insights.
