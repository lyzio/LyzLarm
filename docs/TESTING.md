# v0.4 test checklist (test phone: Fairphone 6, Android 16)

Install the debug APK from the pull request's latest **Build** run (Actions › run ›
Artifacts › `anatole-debug-…`, unzip, open the `.apk`). v0.4 has a new package name, so it
installs as a **new app** (`se.lyzio.anatole.debug`) next to the v0.3 test build. Each
check below is also a "To Test" issue under milestone v0.4; close it when it passes, or
comment what went wrong. The v0.3 checklist is in the v0.3 pull request (#106).

## Before you start: move your nights
In the v0.3 app, export every night you want to keep: Past nights › night › Export ›
**Save to file** (one CSV per night).

## Package name and import
- [ ] v0.4 installs beside v0.3 (two Anatole icons for now); About shows version 0.4.0
- [ ] Settings › Data › **Import night (CSV)**: pick all exported files at once; a message says "N nights imported"; Past nights lists them with time asleep and in bed
- [ ] An imported night opens with the graph, stages and events as in v0.3
- [ ] Importing the same files again says "already saved" and adds no duplicates
- [ ] Picking a file that is not a night export says "not readable" and adds nothing
- [ ] Alarms set again in v0.4 ring (run Settings › Test alarm once)

## Tracking follows the next alarm
Set two alarms, A at 07:00 and B at 07:30, both with a wake window. Start sleep (tracks A).
- [ ] Turn A off on the Alarms tab: Bedtime and the tracking notification switch to B's time and window at once
- [ ] Turn A on again: back to A
- [ ] Edit A to 08:00: Bedtime switches to B (now the earlier one)
- [ ] Add a new alarm C at 06:45: Bedtime switches to C; delete C: back to the next one
- [ ] Hold to stop before any alarm rang: the alarm shown on Bedtime (not the one at Start sleep) is turned off for that morning
- [ ] Overnight with a change after Start sleep: smart wake rings inside the followed alarm's window; the Morning graph shows that window

## Bedtime layout
- [ ] Rows: labels like "Smart wake window" and "Out of bed" never break mid-word; long values (e.g. "06:30–07:00 · Cautious") wrap neatly on the right
- [ ] Display size / font size at the largest setting: the content scrolls and "Hold to stop tracking" stays full height at the bottom
- [ ] The Sound row says "Arrives in v0.5"

## Store and release (owner, in Play Console and GitHub)
- [ ] Store images (Build run › Artifacts › `anatole-store-images-…`): icon, feature graphic and 6 screenshots look right
- [ ] Privacy policy page opens from About › Privacy policy (after the website is rebuilt on merge)
- [ ] Secrets added following docs/release-secrets.md; tag `v0.4.0` creates the GitHub Release with APK, checksum and AAB
- [ ] First AAB uploaded by hand to Internal testing; Play's app signing certificate matches the APK's
- [ ] With `PLAY_SERVICE_ACCOUNT_JSON` set, a later tag uploads to Internal testing by itself, with release notes and listing
- [ ] Play Console App content forms filled from docs/play-console.md (Data safety, Health apps, foreground services with videos, full-screen intent, exact alarms, content rating, target audience 18+)
