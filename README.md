# LyzLarm

A fully offline smart alarm for Android with sleep tracking. LyzLarm wakes you at the
lightest moment of a wake window, based on how you actually slept, and nothing ever
leaves the phone: the app has no internet permission at all.

**Status: v0.1** — a reliable alarm that rings on time with the screen locked, also after
a restart. Sleep tracking arrives in v0.2 and the smart wake window in v0.3. See the
[roadmap](https://lyzio.github.io/LyzLarm/#roadmap).

## Install

1. Download the APK from the [latest release](https://github.com/lyzio/LyzLarm/releases/latest) on your phone.
2. Open it and allow your browser or file manager to install apps once.
3. Open LyzLarm and follow the setup, including the test alarm.

Requires Android 8.0 or newer.

## Privacy

- No `INTERNET` permission. The build checks this on every push.
- No accounts, no ads, no analytics, no Google Play Services.
- Android cloud backup is off. Your data stays on this phone.

## Building

GitHub Actions builds a debug APK on every push (see the **Actions** tab, download the
`lyzlarm-debug-…` artifact). Pushing a tag like `v0.1.0` builds a signed release APK
and attaches it to a GitHub Release. See [docs/SIGNING.md](docs/SIGNING.md) for the
one-time signing setup.

Local build: install JDK 17 and the Android SDK (or Android Studio), then

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest :sleep-engine:test
```

## Project layout

```
app/            UI, alarm scheduling and ringing, database
sleep-engine/   pure Kotlin: activity scores, sleep curve, wake decision, learning
site/           the website at lyzio.github.io/LyzLarm
docs/           signing guide, test checklist
```

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Fonts: Inter and Space Grotesk (SIL Open Font License 1.1).
