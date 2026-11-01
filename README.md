# Rest Relay

A small Android app that watches notifications from gym and delivery apps and posts
its own high-priority alerts. Useful because a Mi Band 10 (and similar) forwards
normal notifications but silently ignores ongoing / low-priority countdown
notifications.

No internet permission, no analytics, no third-party libraries beyond AndroidX.

## What it does

- **Per-app rules** — add any number of rules. Each rule picks an app and a type:
  - **Rest timer** (gym apps like openGym)
  - **Delivery** (Uber Eats, DoorDash, etc.)
- **Rest timer** — three detection modes per rule: *on removal* (with skip detection),
  *keyword in text*, or *not updated for N seconds*.
  - **Skip detection:** when the timer notification is removed, the app computes the
    remaining time (from `Notification.when` countdown end time and parsed `m:ss`
    text). If lots of time was still left it treats the removal as a user skip and
    does **not** alert.
- **Delivery** — parses an ETA from title/text/bigText/subText (relative *"in 6 min"*
  and absolute *"at 7:45 PM"*), then fires *"arriving soon"*, *"delayed"*, or
  *"arrived"* alerts with debounce, re-arm, and cooldown logic.
- **Debug log** — records every posted/updated/removed event for every watched app
  (timestamp, title, text, bigText, subText, ongoing flag, importance, channel id)
  plus ETA parse results and skip/fire decisions, so you can write rules from real data.
- **Customisable alerts** — each alert type (rest over, arriving soon, delayed,
  arrived) has its own title/body (with `{app}`, `{eta}`, `{exercise}` placeholders),
  vibration pattern (presets + custom), sound, accent colour, icon, auto-dismiss
  timeout, and BigText toggle.
- **Ignore-ETA toggle** on the main screen — delivery alerts become arrival-only.

## Alert channel management

Android locks a notification channel's vibration and sound once created, so the app
creates a new channel whenever those settings change (the channel id includes a hash
of the vibration + sound settings, e.g. `rest_over_<hash>`) and deletes the old
unused channels. Importance is always `HIGH`.

## Detection details

### Rest-timer skip detection
On each update the app records the timer's post time and, when available, its end
time (`Notification.when` for a count-down chronometer via
`EXTRA_CHRONOMETER_COUNT_DOWN`) and any `m:ss` value parsed from title/text. On
removal it computes the remaining time and:
- remaining ≤ threshold (default 3 s) → natural finish → alert
- remaining > threshold → user skip → no alert
- unknown → fall back to elapsed-vs-duration, then the "when unsure" policy.

### Delivery ETA parsing
Two editable regexes (in **Delivery settings**) with named groups:
- **relative** — `(?<h>` hours `)?` and `(?<m>` minutes `)`.
- **absolute** — `(?<h>` hour `)`, `(?<m>` minute `)`, optional `(?<ap>` am|pm `)`.

A hand-written fallback still works if a regex is empty or malformed. Every parse
success or failure is logged.

### Delivery alert engine
- **Arriving soon** fires when the ETA is ≤ *N* minutes for a configurable number of
  **consecutive** updates (default 2).
- **Re-arm** once the ETA rises above *N + margin* (default 3 min).
- **Delayed** (off by default) fires if the ETA jumps up more than the threshold.
- **Arrived** fires on arrival keywords regardless of ETA settings.
- A per-order **cooldown** (default 10 min) suppresses duplicates.

## Build (Gradle)

Requirements: JDK 17 and the Android SDK (API 34).

**Android Studio** — open the folder, sync, then *Build → Build App Bundle(s) / APK(s)
→ Build APK(s)*.

**Command line**:

```bash
gradle wrapper --gradle-version 8.7   # once
./gradlew assembleDebug              # Linux/macOS
gradlew.bat assembleDebug            # Windows
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and tap it (allow "install unknown apps").

## Setup on the phone

1. Open **Rest Relay** → **Enable notification access** → grant access.
2. Allow the notifications permission (Android 13+).
3. **Rules** → *Add rule* → pick your app and type.
4. **Alert appearance** → tune each alert and use **Send test alert**.
5. Start a timer / place an order, then check **Debug log** to see exactly how the
   app signals the end, and refine rules / keywords / regexes from that data.

## Notes / tips

- If the band doesn't vibrate, confirm the phone itself vibrates for the alert and
  that Do Not Disturb / battery optimization isn't suppressing it. Disable battery
  optimization for Rest Relay if needed.
- Heads-up pop-ups require importance `HIGH` (always set) **and** that the app's
  system notification settings allow pop-ups.

## License

MIT — see `LICENSE`.
