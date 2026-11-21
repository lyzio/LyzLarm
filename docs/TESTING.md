# v0.1 test checklist (test phone: Fairphone 6, Android 16)

Install the debug APK from the pull request's latest **Build** run (Actions › run ›
Artifacts › `lyzlarm-debug-…`, unzip, open the `.apk` on the phone). It installs as a
separate app next to any release build. Tick each box; note anything odd in the PR.

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
- [ ] About shows version 0.1.0-debug, links open the browser, Open-source licenses lists the fonts

## Privacy
- [ ] Android Settings › Apps › LyzLarm › Permissions shows no network access (the build also checks this)
