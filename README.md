# Anatole – Sleep tracker & alarm

A fully offline smart alarm for Android with sleep tracking. Anatole wakes you at the
lightest moment of a wake window, based on how you actually slept, and nothing ever
leaves the phone: the app has no internet permission at all.

**Status: v0.4** — ready for Google Play: new package name `se.lyzio.anatole`, signed APK
and Play bundle from every release tag, store listing kept in this repo, night import.
v0.3 brought smart wake (rings at the first clear light sleep inside your wake window, always
by your latest time), time asleep, an estimated sleep-stage curve and the optional Snooze lock.
See the [roadmap](https://lyzio.github.io/Anatole/#roadmap).

## Install

Requires Android 8.0 or newer. All three ways use the same signing key, so you can switch
between them later without losing data.

- **Google Play**: in testing (internal, then a closed test). The public listing follows after
  the closed test.
- **GitHub**: download `Anatole-<version>.apk` from the
  [latest release](https://github.com/lyzio/Anatole/releases/latest) on your phone, open it
  and allow your browser or file manager to install apps once.
- **F-Droid**: planned. The store text and images are already in `fastlane/metadata/`.

Then open Anatole and follow the setup, including the test alarm.

**Coming from v0.3 or earlier?** v0.4 has a new package name, so it installs as a new app.
In the old app, export each night (Past nights › night › Export › Save to file). Install
v0.4, import them in **Settings › Data › Import night (CSV)**, then uninstall the old app.

## Privacy

- No `INTERNET` permission. The build checks this on every push.
- No accounts, no ads, no analytics, no Google Play Services.
- Android cloud backup is off. Your data stays on this phone.
- [Privacy policy](https://lyzio.github.io/Anatole/privacy.html).

## Building

GitHub Actions builds a debug APK on every push (see the **Actions** tab, download the
`anatole-debug-…` artifact), checks that the Play bundle builds, and renders the store
screenshots from the real screens (artifact `anatole-store-images-…`).

Pushing a tag like `v0.4.0` builds the signed APK (GitHub Release) and the Play bundle (AAB),
and uploads the bundle, release notes and listing to Google Play's internal test track.
Version code = major × 10000 + minor × 100 + patch (0.4.0 → 400). One-time setup:
[docs/release-secrets.md](docs/release-secrets.md); Play Console answers:
[docs/play-console.md](docs/play-console.md).

Local build: install JDK 17 and the Android SDK (or Android Studio), then

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest :sleep-engine:test
```

## Project layout

```
app/            UI, alarm scheduling and ringing, database
sleep-engine/   pure Kotlin: movement scores, gaps, bedtime learning (later: sleep curve, wake decision)
site/           the website at lyzio.github.io/Anatole
docs/           release secrets, Play Console answers, test checklist
fastlane/       store listing (title, descriptions, images, release notes) for Play and F-Droid
buildSrc/       version name and code from the release tag
```

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Fonts: Inter and Space Grotesk (SIL Open Font License 1.1).

## The name

Anatole is the Greek goddess of sunrise, one of the Horai (the goddesses of the hours).
The app was called LyzLarm until v0.2.
