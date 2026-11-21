# Release signing: one-time setup

Release APKs are signed with one key. Android only installs an update if it is signed
with the same key as the installed app, so **losing this key means existing installs can
never be updated**. Keep the keystore file and its passwords backed up offline (for
example on a USB stick and in your password manager). Never commit it to the repo.

## 1. Create the keystore (once)

You need `keytool`, which comes with any Java JDK (Android Studio includes one).

```sh
keytool -genkeypair -v \
  -keystore lyzlarm-release.jks \
  -alias lyzlarm \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a keystore password, your name (anything, e.g. "LyzLarm") and a key
password. Using the same password for both is fine.

## 2. Back it up

Copy `lyzlarm-release.jks` and the passwords somewhere safe and offline.

## 3. Add four secrets to GitHub

Turn the keystore into text:

```sh
# macOS / Linux
base64 -i lyzlarm-release.jks | tr -d '\n' > keystore.txt
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("lyzlarm-release.jks")) | Out-File keystore.txt -NoNewline
```

On GitHub: **Settings › Secrets and variables › Actions › New repository secret**, add:

| Name | Value |
|---|---|
| `LYZLARM_KEYSTORE_BASE64` | contents of `keystore.txt` |
| `LYZLARM_KEYSTORE_PASSWORD` | the keystore password |
| `LYZLARM_KEY_ALIAS` | `lyzlarm` |
| `LYZLARM_KEY_PASSWORD` | the key password |

Then delete `keystore.txt`.

## 4. Make a release

After the v0.1 pull request is merged:

```sh
git checkout master && git pull
git tag v0.1.0
git push origin v0.1.0
```

No terminal? Ask Claude to push the tag for you. Don't create the release by hand on the
Releases page; the workflow creates it.

The **Release** workflow builds, signs and verifies the APK, then creates the GitHub
Release with `LyzLarm-0.1.0.apk` attached. The website updates itself after that.

## Note on debug vs release

Debug APKs from every push install as a separate app (**LyzLarm** with package
`se.lyzio.lyzlarm.debug`), so you can test builds without touching your real alarms.
