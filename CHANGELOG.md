# Changelog

## Unreleased

### Cardio Points
- Fitness now shows daily points and 30 days of weekly history, with no points goal or streak changes.
- Scores combine available walking/running cadence with clearly labelled workout estimates, counting overlapping activity once.
- A compact Fitness card matches Move Week’s number and green chart styling. Tap its footer for history, daily source breakdowns, refresh, and expandable scoring explanations. Manual workouts work without Health Connect.
- Points stay in a device-local cache excluded from backup, are cleared on sign-out, and are never sent to the coach.
- Google credential cleanup still runs if the local points cache cannot be cleared during sign-out.
- Health Connect reads are bounded to the requested workout period and retained cadence history. Cached scores keep their stale/limited labels on reopening, and workout sources retain installed-app names.


## 0.4.0 (23 October 2026)

### Smart scales
Step on your Bluetooth scale and the weigh-in is logged. No pairing, and no scale app needed.
- Works with scales that broadcast your weight: Xiaomi Mi Scale and Mi Body Composition Scale, Renpho, FITINDEX and other QN scales, OKOK and Chipsea scales, Etekcity Fit 8S, Eufy C20, Yunmai X, PICOOC Mini Lite, Sinocare, AiLink, AAA-series and Scale Up.
- Set it up from Settings or the Weight screen: tap Find my scale and step on.
- Readings are logged even with the app closed. A notification shows each one, with Undo.
- The Smart scale screen shows whether it's listening right now. If something's in the way, like Bluetooth being off, it says what and gives you the button that fixes it.
- On a shared scale, a reading far from your recent weight asks "Was this you?" before it's logged.
- With the Weight screen open, you see the weight settle live.
- Turn on Save weigh-ins to Health Connect and they reach Google Health, Fitbit and other apps too, marked as measured by your scale.

### Weight widget
- A home screen widget shows your latest weigh-in and this week's trend. Wider, it adds a month's trend line.
- Tap Weigh in to listen for your scale, or Log weight if you don't have one. While you're on the scale, the widget shows the weight as it settles.
- Add it from the widget list, or with one tap in Smart scale settings.

## 0.3.0 (18 October 2026)

### Snapshots
Take progress photos whenever you like, and see the change over time.
- The camera shows a faint copy of your last photo, so each new one lines up.
- A slider compares your first photo with your latest.
- Every few weeks a short reel of your photos arrives on its own. Pick how often, from every 3 days to every 8 weeks.
- Photos are encrypted on your phone before they're backed up, so only you can see them. Backup happens by itself.
- Find Snapshots on the Weight screen, in Settings, or by long-pressing the app icon.

### Weight goal
- Your goal now sits at the top of the Weight screen as one bar, split into 2 kg steps, with how far you are from the next one.
- Each milestone gets a small award and a word from your coach. Reaching your goal gets a bigger one.
- Once you reach your goal, set a new one or switch to maintaining your weight.
- The chart shows your plan as a faint line.

### Day streak
- Today's tide is now your day streak: how many days in a row you've done everything you set out to.
- This week shows as seven days, with today filling up as you go.
- Choose what a day needs from the gear on the card: any of your habits, sleep, steps, meals and water. Set your weekly goal from 1 to 7 days.
- The streak, the weekly goal and the calendar now always agree. A missed day covered by your weekly freeze keeps your streak going without adding to it.

### Private habits
- Mark a habit as private and it shows under a cover name everywhere in the app.
- The real name is encrypted and only appears on the habit's own page, after your screen lock.
- Your coach only ever sees the cover name.

### Fixes
- The habit streak no longer counts a freeze day as a full day.
- The week now starts on the first day of the week for your region.
- Settings shows the right app version.
