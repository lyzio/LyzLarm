# v0.2 test checklist (test phone: Fairphone 6, Android 16)

Install the debug APK from the pull request's latest **Build** run (Actions › run ›
Artifacts › `lyzlarm-debug-…`, unzip, open the `.apk` on the phone). It installs as a
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
- [ ] A short press on **Hold to stop tracking** does nothing; holding ~1.5 s stops tracking and opens the night's graph
- [ ] Back from Bedtime returns to the Alarms tab while tracking continues; the big button then reads "Tracking your sleep" and reopens Bedtime

## Short test night (30–60 min, set an alarm 45 min ahead)
- [ ] Start sleep, put the phone face down on the bed, move it now and then, lock the screen
- [ ] When the alarm rings, **Snooze** once, then **I'm up**: after unlocking, the Morning screen opens ("Saving last night…" for a moment is OK)
- [ ] Morning: date range, tiles Tracked / Started / Woke; the graph shows bars where you moved, an accent line and dot at the alarm, time labels every 2 h
- [ ] Event list (newest first): Alarm dismissed, Snoozed 9 min, Alarm rang, Tracking started · battery …%
- [ ] "How did you wake up?" is greyed with "Arrives in v0.5"
- [ ] The Alarms tab shows a "Last night" card (tracked time, start – end, gaps) that opens Morning
- [ ] The tracking notification is gone

## Robustness
- [ ] Kill the app while tracking (Settings › Apps › LyzLarm › Force stop is too strong; instead swipe it away in Recents): tracking continues or comes back within 15 min
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
- [ ] Android Settings › Apps › LyzLarm › Permissions shows no network access (the build also checks this)
