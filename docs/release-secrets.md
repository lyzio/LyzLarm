# Release secrets and Google Play setup (v0.4)

The **Release** workflow (`.github/workflows/release.yml`) runs when you push a tag like
`v0.4.0`. It needs these GitHub Secrets (repo **Settings › Secrets and variables › Actions ›
New repository secret**):

| Secret | What | Needed for |
|---|---|---|
| `LYZLARM_KEYSTORE_BASE64` | App signing keystore (`lyzlarm-release.jks`), base64 | APK (already set since v0.1) |
| `LYZLARM_KEYSTORE_PASSWORD` | Its keystore password | APK (already set) |
| `LYZLARM_KEY_ALIAS` | Its alias, `lyzlarm` | APK (already set) |
| `LYZLARM_KEY_PASSWORD` | Its key password | APK (already set) |
| `UPLOAD_KEYSTORE_BASE64` | Upload keystore, base64 | Play bundle (AAB) |
| `UPLOAD_KEYSTORE_PASSWORD` | Upload keystore password | AAB |
| `UPLOAD_KEY_ALIAS` | Upload key alias, `upload` | AAB |
| `UPLOAD_KEY_PASSWORD` | Upload key password | AAB |
| `PLAY_SERVICE_ACCOUNT_JSON` | Service account key file (the JSON text itself, not base64) | Upload to Play |

What happens when some are missing:
- No `UPLOAD_*`: the APK is released on GitHub as before; no AAB (a warning says so).
- No `PLAY_SERVICE_ACCOUNT_JSON`: APK and AAB are on the GitHub Release; the Play upload job is skipped.

Optional repository **variable** (same page, Variables tab): `PLAY_RELEASE_STATUS`. Leave it
unset (= `completed`, testers get the build at once). If the upload fails with "Only releases
with status draft may be created on draft app", set it to `draft` until Play has finished
setting the app up, then delete it.

You need `keytool` (comes with any Java JDK; Android Studio includes one) and, for step 3,
Java to run Google's `pepk.jar`.

## Two keys, why

- **App signing key** (`lyzlarm-release.jks`, from v0.1): signs the APK on GitHub and, via
  Play, every install from Play. Same key everywhere, so all builds can update each other.
  If you lose it, existing installs can never be updated. Keep the offline backup.
- **Upload key** (new): signs only the bundle you send to Play. Play checks it, then re-signs
  with the app signing key. If the upload key leaks or is lost, Google can replace it; the app
  signing key never needs to leave your machine again after step 3.

## 1. Create the upload key (once)

```sh
keytool -genkeypair -v \
  -keystore anatole-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and a name (anything, e.g. "Anatole upload"). Use the same password
for keystore and key if asked. Back up `anatole-upload.jks` and the password offline too.

Export its certificate (Play needs it in step 3):

```sh
keytool -export -rfc -keystore anatole-upload.jks -alias upload -file upload_certificate.pem
```

## 2. Add the upload key secrets

```sh
# macOS / Linux
base64 -i anatole-upload.jks | tr -d '\n' > upload.txt
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("anatole-upload.jks")) | Out-File upload.txt -NoNewline
```

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | contents of `upload.txt` |
| `UPLOAD_KEYSTORE_PASSWORD` | the password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the password |

Then delete `upload.txt`.

## 3. Create the app in Play Console and give Play the app signing key

1. Play Console › **Create app** (answers in [play-console.md](play-console.md)). Package
   name is set by the first upload: `se.lyzio.anatole`. It can never change afterwards.
2. **Test and release › Setup › App signing** (or, the first time, when you create the first
   release): choose **Use a different key / Export and upload a key from Java keystore**.
3. Play shows a download link for `pepk.jar`, an encryption public key and the exact command.
   It looks like this (use the one Play shows):
   ```sh
   java -jar pepk.jar \
     --keystore=lyzlarm-release.jks \
     --alias=lyzlarm \
     --output=encrypted-app-signing-key.zip \
     --include-cert \
     --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```
   It asks for the keystore and key passwords of `lyzlarm-release.jks`. The zip is encrypted
   for Google only. Upload it.
4. On the same page, **upload the upload key certificate**: `upload_certificate.pem` from step 1.
5. Delete `encrypted-app-signing-key.zip`. Your release key itself stays only in your backups.

## 4. First release (by hand)

Google's API cannot create an app, so the very first bundle goes up by hand:

1. Push the tag (or ask Claude to): `git tag v0.4.0 && git push origin v0.4.0`. With the
   `UPLOAD_*` secrets set, the GitHub Release gets `Anatole-0.4.0.aab`.
2. Download the `.aab` from the GitHub Release.
3. Play Console › **Testing › Internal testing › Create new release** › upload the `.aab` ›
   release notes from `fastlane/metadata/android/en-US/changelogs/400.txt` › **Save** ›
   **Review release** › **Start rollout to Internal testing**.

## 5. Service account for automatic uploads

1. <https://console.cloud.google.com> › create a project (e.g. "anatole-release").
2. **APIs & Services › Library** › "Google Play Android Developer API" › **Enable**.
3. **IAM & Admin › Service accounts › Create service account** (name "github-release"),
   no Cloud roles needed › Done.
4. Open it › **Keys › Add key › Create new key › JSON**. A `.json` file downloads.
5. Play Console › **Users and permissions › Invite new users** › paste the service account's
   email (`github-release@….iam.gserviceaccount.com`) › **App permissions › Add app ›
   Anatole** with: *View app information*, *Release apps to testing tracks*, *Manage testing
   tracks and edit tester lists*, *Manage store presence* › Invite user.
6. GitHub secret `PLAY_SERVICE_ACCOUNT_JSON` = the whole contents of the `.json` file
   (open it in a text editor, copy everything). Then delete the file.

From now on every tag `v*`:
- builds and signs APK + AAB, renders the store screenshots,
- creates the GitHub Release (APK, checksum, AAB),
- uploads the AAB to **internal testing** with release notes from
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, and updates the listing
  text and images from `fastlane/metadata/android/en-US/`.

Version code = major × 10000 + minor × 100 + patch (0.4.0 → 400, 0.4.1 → 401). Name the
changelog file after it. Promotion to closed testing and production stays manual in Play
Console.

If the Play job fails (e.g. a new permission needs a Console declaration first), fix it in
Console and use **Re-run failed jobs** on the workflow run; the GitHub Release is not
affected.
