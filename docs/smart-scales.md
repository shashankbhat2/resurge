# Smart scales

Step on a Bluetooth scale and the weigh-in is logged, with the app open or closed. Resurge
supports scales that **broadcast** the weight in their Bluetooth advertisements. It only ever
listens, so there's no pairing with the scale, no connection, and no vendor app to install.

The protocols are ported from [openScale](https://github.com/oliexdev/openScale). openScale is GPL-3.0, the same licence as Resurge.

## Supported scales

| Protocol (`core/scale/.../protocol`) | Scales |
|---|---|
| `MiScaleProtocol` | Xiaomi Mi Smart Scale (1, 2), Mi Body Composition Scale (2) |
| `QnBroadcastProtocol` | Renpho, FITINDEX (FT-26R) and other QN "AABB" broadcast scales |
| `OkOkProtocol`, `OkOkUnflaggedProtocol` | OKOK / Chipsea scales (V20, C0, V11, VF0) |
| `EtekcityFit8SProtocol` | Etekcity Fit 8S |
| `EufyC20Protocol` | Eufy Smart Scale C20 (T9130) |
| `YunmaiXProtocol` | Yunmai X |
| `PicoocMiniLiteProtocol` | PICOOC Mini Lite |
| `SinocareProtocol` | Sinocare |
| `AiLinkBroadcastProtocol` | AiLink broadcast body fat scales |
| `AaaxProtocol` | AAA-series broadcast scales |
| `ScaleupProtocol` | Scale Up |

Not supported yet:
- Scales that need a GATT connection (the Bluetooth Weight Scale profile 0x181D over GATT, Withings, Garmin, Omron). Withings and Garmin already write to Health Connect, so their readings come in through the Health Connect import.
- Scales that encrypt with a per-device key, such as the Xiaomi S400.

## How a weigh-in flows

```
 scale wakes up, advertises ─┐
                             ▼
  Android background scan (filtered to paired addresses, low power, runs with the app closed)
                             │ PendingIntent
                             ▼
  ScaleScanReceiver ──► starts WeighInService (foreground, "Listening for your scale")
         │                    │ active low-latency scan, up to 40–60 s
         └──── both feed ─────┤
                              ▼
                       WeighInEngine
          protocol.decode ► WeighSession (settles one reading per person-on-scale)
                              │
              looks like someone else? (≥ 3 kg / 5 % off the latest reading in 45 days)
               ├─ yes ► Unusual ► notification "Was this you?"  [Log it] [Not me]
               └─ no  ► WeightRepository.saveFromScale
                             ├─ Firestore users/{uid}/weights/scale_<atMs>  (offline-first)
                             ├─ Health Connect WeightRecord (if "Save weigh-ins to Health Connect")
                             └─ WeightChangeListener ► home screen widget refresh
                              ▼
                       ScaleOutcome ► notification "Logged 72.4 kg"  [Undo]
                                     (skipped while the Weight screen is showing it live)
```

The same engine serves three ways of listening:
1. **Background:** the system scan wakes the app when the scale advertises.
2. **Weight screen:** while it's open, it scans and shows the weight settling live.
3. **Widget or "Weigh in now":** a one-minute foreground listen.

Two layers stop a reading from being logged twice:
- `WeighSession` logs one reading per time someone stands on the scale, even though the scale keeps broadcasting the final number.
- `saveFromScale` skips a reading within 0.3 kg of a scale reading from the last 5 minutes. This catches two phones, or two listeners, hearing the same weigh-in.

Readings under 20 kg (children, pets, objects) are ignored.

## Sync

**Resurge account (Firestore)**
- Each weigh-in is written to `users/{uid}/weights` with `source: "scale"`, plus `device` (the model name) and `impedanceOhm` when the scale measures it.
- Firestore's offline cache queues the write if there's no network, so the weigh-in reaches every phone signed in to the account.
- Paired scales are kept per phone, in the `resurge_scales` DataStore: a Bluetooth address only means something to the phone that heard it.

**Health Connect, Google Health, Fitbit**
- When **Save weigh-ins to Health Connect** is on, each reading is also written as a `WeightRecord`. The flag is the same one manual weigh-ins use, `weight_write_hc`.
- The record has `Metadata.autoRecorded(Device(TYPE_SCALE, model))`, so other apps see it as measured by a scale rather than typed in.
- Its `clientRecordId` is `resurge_<weigh-in id>`, so a retry replaces the record instead of adding a second one.
- Google Health, Fitbit, Samsung Health and others read Health Connect, which is how weigh-ins reach them. Google Fit's own APIs are deprecated; Health Connect replaces them.
- Undo removes the Health Connect record too, through the stored `hcId`.
- Resurge doesn't import its own Health Connect records back: `weightHistory` filters out its own package.

**Coach**
- The coach reads `dailySummaries.weightKg`, which comes from Health Connect. With the Health Connect write on, scale readings reach the coach the same way.

## Android details

**Permissions**
- **Android 12+:** `BLUETOOTH_SCAN` with `neverForLocation`, shown to the user as "Nearby devices". No location access.
- **Android 8–11:** fine location, which Android needs for any Bluetooth scan. Location services must be turned on.
- **Android 10–11:** background location ("Allow all the time"), for scans with the app closed. The Smart scale screen explains this and asks for it.
- No `BLUETOOTH_CONNECT`: device names are read from the advertisement itself.

**Foreground service**
- `WeighInService` runs as a `connectedDevice` foreground service.
- Bluetooth scan broadcasts are exempt from Android 12's limits on starting a foreground service from the background.
- If the service still can't start, the receiver feeds the engine directly, so the reading is still logged.

**Keeping background listening on**
- The background scan stops when Bluetooth is switched off, the phone reboots or the app is updated.
- `ScaleWatchWorker` re-arms it every 6 hours. It's also re-armed on app start, on boot and after an update.

**Battery**
- The background scan uses a hardware address filter in low-power mode, so it costs next to nothing while nobody is on the scale.

## Adding a scale

1. Add an `object FooProtocol : ScaleProtocol` in `core/scale/src/main/kotlin/com/resurgeai/core/scale/protocol/`.
   - `recognise()` identifies the scale from its name, company id, magic bytes or service UUIDs.
   - `decode()` returns kg and the stable flag. Set `reportsStability = false` if the scale has no stable flag; `WeighSession` then waits for the weight to repeat.
2. Register it in `ScaleProtocols.all`, before any looser matcher it could be confused with.
3. Add byte-vector tests next to the others in `core/scale/src/test/`. Captures from a real scale are best; openScale's tests are a good source.

`id` is persisted with paired scales, so never rename one.
