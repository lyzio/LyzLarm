# v0.3 test checklist (test phone: Fairphone 6, Android 16)

Install the debug APK from the pull request's latest **Build** run (Actions › run ›
Artifacts › `anatole-debug-…`, unzip, open the `.apk`). It installs over the v0.2 test
build and keeps your alarms and nights. Each check below is also a "To Test" issue
under milestone v0.3; close it when it passes, or comment what went wrong.

Existing alarms keep a **fixed time** after the update (window Off), so nothing rings
earlier than you set. Turn the wake window on in Edit alarm to try smart wake. New
alarms get the window from Settings › Alarm defaults (30 min).

## Name and upgrade
- [ ] The launcher shows **Anatole**; About shows "Anatole · Sleep tracker & alarm", version 0.3.0 and the story of the name
- [ ] Upgrading from v0.2 keeps all alarms and past nights; old nights open with time asleep and in bed

## Smart wake
- [ ] Edit alarm: wake window chips (Off, 15, 20, 30, 45 min) work; header says "Rings between 06:30 and 07:00"
- [ ] Alarms tab: the next-alarm card reads "06:30–07:00 · smart wake"; the alarm row says "30 min window"
- [ ] Bedtime: "Wake 06:30–07:00", Smart wake window row "06:30–07:00 · Cautious" (learning) and "Learning your sleep · night N of 7"
- [ ] Short test: alarm 40 min ahead with a 30 min window, start sleep, lie still, then move a lot after the window opens: it rings within about a minute, and the ringing screen says "Light sleep at HH:MM"
- [ ] Lie still through the whole window: it rings exactly at the alarm time, ringing screen says "End of your window"
- [ ] Without tracking (no Start sleep), a smart alarm rings at the alarm time
- [ ] An alarm with the window Off rings at its time and says "Fixed time"
- [ ] Repeating alarm that rang early: it rings again the next repeat day, not again the same morning
- [ ] Smart snooze: snooze 9 min and move after about 5 min: it rings early ("Snooze ended at light sleep" in the events); lying still, it rings after 9 min

## Stopping early (#71)
- [ ] Start sleep, then Hold to stop before the alarm: the alarm does not ring that morning; the event list says "Tracking stopped · alarm turned off"
- [ ] A repeating alarm stopped early rings again on its next day; a one-time alarm is switched off

## Bedtime reminder (#26)
- [ ] Reminder's **Start sleep** opens the Bedtime screen and tracking starts (notification appears); **Not tonight** closes it

## Morning screen
- [ ] Tiles: Time asleep, In bed, Fell asleep in, Woke (with reason underneath)
- [ ] Sleep stages curve labelled "Estimate", with Deep / Light totals
- [ ] Graph: pink wake window band, alarm marker; legend shows Wake window
- [ ] Event list: Wake window opened, Light sleep detected, Alarm rang · at light sleep
- [ ] Past nights: each row shows "… asleep" and "… in bed"

## Empty bed test and out of bed
- [ ] Settings › System check › Empty bed test: 30 s to leave, 10 min countdown, then "Saved" with the noise level
- [ ] Someone on the bed during the test: "Not saved", Try again
- [ ] The row shows the last run date; **Export** shares a CSV
- [ ] Bedtime "Out of bed" row says "Active" after the test, "Run the empty bed test" before it
- [ ] Get up for 5+ min during a night: either an "Out of bed" band and events appear, or the Morning screen says detection was not reliable tonight (report which)

## Snooze lock
- [ ] Edit alarm › Snooze lock on: the notification has no Snooze / I'm up buttons; the ringing screen says both buttons ask for a challenge
- [ ] Math (try Easy, Normal, Hard), Memory and Typing each work; a wrong answer shows "Not quite" and Retry gives a new one
- [ ] The alarm keeps ringing (vibration and rising volume) during the challenge; Back returns to the ringing screen
- [ ] Barcode: turning it on asks for the camera; a new code solves it; the same code again within 7 days is refused; "Can't scan?" switches to another challenge
- [ ] Stay-awake check: after solving, lie back down still: it rings again after about 5 minutes ("Alarm rang again (back in bed)")
- [ ] Stay-awake check: get up and walk around with the phone: no second ring; the night is saved after 5 minutes

## Overnight (5–7 nights)
For each night note in the PR: wake time and reason, whether it felt like a good moment,
out-of-bed accuracy (or "not reliable"), how believable the stage curve looks, battery at
start and end. Send two or three night CSVs (they now include a `noise` column).

# Regression: v0.2 checks (test phone: Fairphone 6, Android 16)

Install the debug APK from the pull request's latest **Build** run (Actions › run ›
Artifacts › `anatole-debug-…`, unzip, open the `.apk` on the phone). It installs as a
separate app next to any release build. Tick each box; note anything odd in the PR.

Coming from the v0.1 debug build: **uninstall it first** (it was signed with a one-off
test key, so Android refuses the update) and set your alarms again. From v0.2 on, test
builds share one fixed test key, so later builds install over earlier ones.

## Upgrade and logo
- [ ] Installing a later v0.2 build over an earlier one works and keeps alarms and nights
- [ ] The launcher icon is the new logo (crescent moon holding a pink sun) on a dark background
- [ ] With themed icons on (Wallpaper & style › Themed icons), the icon shows moon and sun in one color
- [ ] Notifications show the moon-and-sun small icon; Welcome (reset app data to see it) and About show the new logo

## Start sleep
- [ ] Alarms tab: the card reads "Next alarm · in …" and "Fixed time · smart wake arrives in v0.3"
- [ ] With no alarm in the next 16 h, **Start sleep** is grey with "Set an alarm first"
- [ ] With an alarm set: the hint reads "Tracks until the HH:MM alarm"; tapping **Start sleep** opens the Bedtime screen
- [ ] A low "Tracking your sleep · Alarm HH:MM · movement tracking on" notification appears with **Stop tracking**
- [ ] Bedtime screen: time, alarm, Movement "Active, all night", Alarm "Rings in …", Battery "…%, enough for tonight" (or a warning), greyed Smart wake and Sound rows
- [ ] After 30 s without touching, the screen dims; a tap brightens it
- [ ] A short press on **Hold to stop tracking** does nothing; holding ~1.5 s stops tracking. Under 15 min tracked: a message says it was not saved; longer: the night's graph opens
- [ ] Back from Bedtime returns to the Alarms tab while tracking continues; the big button then reads "Tracking your sleep" and reopens Bedtime

## Short test night (30–60 min, set an alarm 45 min ahead)
- [ ] Start sleep, put the phone face down on the bed, move it now and then, lock the screen
- [ ] When the alarm rings, **Snooze** once, then **I'm up**: after unlocking, the Morning screen opens ("Saving last night…" for a moment is OK)
- [ ] Morning: date range, tiles Tracked / Started / Woke (full text, not cut off), legend on one or two lines, "Battery X% → Y% (Z% used)"; the graph shows bars where you moved, an accent line and dot at the alarm, time labels every 2 h
- [ ] Event list (newest first): Alarm dismissed, Snoozed 9 min, Alarm rang, Tracking started · battery …%
- [ ] "How did you wake up?" is greyed with "Arrives in v0.5"
- [ ] The Alarms tab shows a "Last night" card (tracked time, start – end, gaps) that opens Morning
- [ ] The tracking notification is gone

## Robustness
- [ ] Kill the app while tracking (Settings › Apps › Anatole › Force stop is too strong; instead swipe it away in Recents): tracking continues or comes back within 15 min
- [ ] **Restart the phone** while tracking and unlock it: the tracking notification comes back by itself; the night later shows a hatched "Not tracked" area and "Tracking interrupted for N min"
- [ ] The alarm rings on time in both cases

## Reminders and battery
- [ ] Settings › Tracking: Bedtime reminder on, "Reminder time: Automatic, around HH:MM" (alarm − 9 h until three nights are tracked)
- [ ] Set the reminder to a fixed time 2 min ahead: "Time for bed?" arrives with **Start sleep** (opens Bedtime and starts tracking) and **Not tonight** (closes it)
- [ ] Set the warning threshold above your battery level and the warning time 2 min ahead, unplugged: "Battery at …%" arrives with **OK**. Plugged in: no warning
- [ ] Turning the reminder or warning off stops it

## Past nights and export
- [ ] Sleep tab: nights newest first, date range, start – end, yellow **Gap** marker where there were gaps, tracked time
- [ ] Tap opens the graph; long press asks to delete, and Delete removes it
- [ ] Morning › **Export this night (CSV)** › **Share…** opens the share sheet; **Save to file…** saves a .csv you can open
- [ ] With **Record raw sensor data** on (Settings › Tracking) for a night, the export has `raw` rows; without it only `epoch` and `event` rows

## Overnight (5–7 nights)
For each night note in the PR: battery at start and end (shown in the export header), any gaps, anything odd.
Send two or three night CSVs, at least one with raw sensor data on.

# Regression: v0.1 alarm checks

## Setup
- [ ] First launch shows Welcome (step 1 of 5)
- [ ] Notifications: tapping **Allow notifications** shows Android's prompt; allowing moves on
- [ ] Alarms and full screen: both rows say **Allowed** (or **Allow** opens the right system page, and coming back shows **Allowed**)
- [ ] Battery: **Open battery settings** shows Android's "allow background" prompt; after allowing, status says unrestricted
- [ ] Test alarm: **Start test alarm**, lock the screen. Within about a minute the phone vibrates, then the sound fades in, and the ringing screen shows over the lock screen
- [ ] Tapping **I'm up** stops it; setup shows **Your alarm works** and **Finish setup** opens the alarm list

## Alarms
- [ ] **+** opens New alarm with the time picker; Save adds it to the list and the Next wake-up card shows it with a correct countdown
- [ ] The status bar shows the alarm-clock icon; Android's quick settings / lock screen show the same next alarm time
- [ ] Wake window, Sensitivity and Sound tracking are greyed out with "Arrives in v0.3 / v0.4"
- [ ] Edit an alarm, change time and days, Save: the list and Next wake-up update
- [ ] Toggle an alarm off: it dims and is no longer the next wake-up
- [ ] Delete alarm removes it
- [ ] Sound: picking another alarm sound shows its name and that sound plays when it rings

## Ringing (set an alarm 2–3 minutes ahead each time)
- [ ] Screen locked and off: the phone wakes, vibrates first (~45 s), then the sound fades in over ~3 min; the screen shows time, date, label and "Volume rising"
- [ ] Screen on and unlocked (in another app): a notification with **Snooze** and **I'm up** appears and the alarm plays
- [ ] **Snooze 9 min** stops it and it rings again 9 minutes later; Next wake-up shows "Snoozed"
- [ ] **I'm up** stops it. A one-time alarm turns itself off; a repeating alarm shows its next day
- [ ] The Back button does not stop the ringing
- [ ] Do Not Disturb on (default settings): the alarm still rings

## Reliability
- [ ] Set an alarm 5 min ahead, **restart the phone**, unlock it: the alarm still rings on time
- [ ] Set an alarm, change the phone's **time zone** (Settings › System › Date & time): the alarm rings at the same local time in the new zone
- [ ] Install a newer debug APK over the old one: existing alarms still ring
- [ ] Leave the phone untouched and locked for 1 h+ (Doze) with an alarm set: it rings on time

## Settings
- [ ] System check shows OK for each item, or **Fix** opens the right system page
- [ ] **Run test** in System check works like the setup test and updates "Last passed"
- [ ] About shows version 0.2.0-debug, links open the browser, Open-source licenses lists the fonts

## Privacy
- [ ] Android Settings › Apps › Anatole › Permissions shows no network access (the build also checks this)
