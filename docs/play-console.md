# Google Play Console: ready-to-paste answers (v0.4)

App: **Anatole: Sleep tracker & alarm** · package `se.lyzio.anatole` · developer account: personal.
Store text and images are not entered here: the release workflow uploads them from
`fastlane/metadata/android/en-US/` (title, short and full description, icon, feature graphic,
screenshots, release notes). Change those files to change the listing.

New personal accounts must run a **closed test with at least 12 testers, opted in for 14 days
in a row**, before they can apply for production.

## Create the app

Play Console › **Create app**

| Field | Answer |
|---|---|
| App name | Anatole: Sleep tracker & alarm |
| Default language | English (United States) – en-US |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick both (Developer Program Policies, US export laws) |

## Store settings

Grow users › Store presence › **Store settings**

| Field | Answer |
|---|---|
| App category | Health & Fitness |
| Tags | Sleep, Alarm clock (pick the closest offered) |
| Email address | oliver.sundstrom@gmail.com |
| Website | https://lyzio.github.io/Anatole |
| Phone | leave empty |

## App content (Policy › App content)

### Privacy policy
`https://lyzio.github.io/Anatole/privacy.html`

### App access
**All functionality in my app is available without any access restrictions.** (No login.)

### Ads
**No, my app does not contain ads.**

### Content rating
Start questionnaire. Email: oliver.sundstrom@gmail.com. Category: **All other app types**
(not a game, not social or communication). Answer **No** to every question: no violence,
fear, sexuality, crude humor, drugs, alcohol, tobacco, gambling, bad language; users cannot
interact or share content with each other; the app does not share the user's location; no
purchases of digital goods; no unrestricted internet access (the app has no internet at all).
Expected rating: Everyone / PEGI 3 / USK 0.

### Target audience and content
| Question | Answer |
|---|---|
| Target age groups | **18 and over** only |
| Could the app unintentionally appeal to children? | No |

### News app
No.

### COVID-19 contact tracing and status apps
My app is not a publicly available COVID-19 contact tracing or status app.

### Data safety
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

That is all Play asks when the answer is No. Why it is true: Play counts data as "collected"
only when it is sent off the device. Anatole has no `INTERNET` permission (CI fails a build
that has one), no SDKs that send data, and Android backup is off. Night data stays on the
phone; CSV export only happens when the user taps Export and chooses where the file goes.

### Advertising ID
**No**, the app does not use an advertising ID. (The manifest has no `AD_ID` permission.)

### Government apps
No.

### Financial features
My app doesn't provide any financial features.

### Health apps
| Question | Answer |
|---|---|
| Health features | **Sleep management** (sleep tracking). If the list has no such entry, pick the closest fitness/wellness entry, never a medical one |
| Is the app a medical device / does it diagnose or treat? | No |
| Health Connect | Not used |
| Description | Anatole is a smart alarm that estimates sleep from the phone's motion sensor while it lies on the mattress. It shows time asleep, time in bed and an estimated sleep-stage curve, and can ring at light sleep inside a wake window. It is a wellness tool, not a medical device, and makes no medical claims. All data stays on the device. |

### Foreground service permissions
The manifest has two foreground-service types. No service uses the camera (the barcode
challenge runs in a normal screen) or the microphone (not in v0.4), so declare only these.
Record each video on the Fairphone (screen recording, 30–60 s), upload it (YouTube unlisted
or Google Drive with link sharing) and paste the link.

**1. `FOREGROUND_SERVICE_HEALTH` (type `health`) – TrackingService**

| Field | Answer |
|---|---|
| Task | Other health / fitness: overnight sleep tracking |
| Description | When the user taps "Start sleep" at bedtime, Anatole records movement from the accelerometer all night with the phone on the mattress, to build the morning sleep graph and to ring at light sleep inside the user's wake window. Tracking runs only after this explicit start, shows an ongoing notification with a "Stop tracking" action, and ends when the alarm is dismissed or the user stops it. |
| Impact if deferred or interrupted | The night would have gaps: sleep data would be missing or wrong, and smart wake could not detect light sleep, so the alarm would only ring at the latest time. The user expects tracking to run continuously from bedtime until they get up. |
| Video | Shows: Alarms tab › Start sleep › Bedtime screen and the "Tracking your sleep" notification with Stop tracking › screen off › later the Morning screen with the graph |

**2. `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (type `mediaPlayback`) – RingingService**

| Field | Answer |
|---|---|
| Task | Media playback: playing the alarm sound |
| Description | When an alarm the user set goes off, Anatole plays the alarm sound (rising volume) and vibrates until the user dismisses or snoozes it on the full-screen ringing screen or in the notification. The service starts from the alarm clock (`setAlarmClock`) and stops as soon as the alarm is dismissed or snoozed. |
| Impact if deferred or interrupted | The alarm would not sound, or would stop while the user is still asleep, so the user could oversleep. An alarm must start at the set time and keep playing until the user reacts. |
| Video | Shows: set an alarm one minute ahead (or Settings › Test alarm), lock the phone, the alarm rings full screen with sound, then I'm up / Snooze |

### Full-screen intent
| Question | Answer |
|---|---|
| Does your app use the full-screen intent permission? | Yes |
| Use case | **Alarm** (the app's core function is an alarm clock) |
| Description | Anatole is an alarm clock. When an alarm the user set rings, a full-screen ringing screen is shown over the lock screen so the user can dismiss or snooze it. It is not used for anything else. |

### Exact alarms
| Question | Answer |
|---|---|
| Permission | `USE_EXACT_ALARM` (Android 13+); `SCHEDULE_EXACT_ALARM` only up to Android 12 |
| Core functionality | **Alarm clock**: the user sets wake-up alarms that must ring at the exact time |
| Description | Anatole is an alarm clock app. It schedules the user's wake-up alarms with `AlarmManager.setAlarmClock()`, which must fire at the exact minute set by the user, also in Doze. Exact timing is the app's core function. |

## Testing tracks

1. **Internal testing** (up to 100 people, no review): CI uploads every `v*` tag here.
   Testing › Internal testing › Testers: create an email list (yourself plus anyone helping).
2. **Closed testing** (the 12-tester / 14-day requirement): Testing › Closed testing ›
   create a track (e.g. "Alpha"), add a Google Group or an email list with at least 12 testers,
   then **Promote release** from internal testing to this track. Testers join via the opt-in
   link and must stay opted in for 14 days in a row.
3. **Production**: after the 14 days, Dashboard › Apply for production. Promotion stays manual.

## After the first upload

Check **Release › Setup › App signing**: "App signing key certificate" SHA-256 must match
the release APK on GitHub (`apksigner verify --print-certs`, printed in the Release workflow
log). That proves GitHub, Play and (later) F-Droid builds can update each other.
