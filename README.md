# HoursGram for Android

<p align="center">
  <a href="https://github.com/plshours/HoursGram---Android/actions"><img src="https://github.com/plshours/HoursGram---Android/actions/workflows/release.yml/badge.svg" alt="APK build"></a>
  <img src="https://img.shields.io/badge/version-1.0.0-8993D8?style=flat-square" alt="Version 1.0.0">
  <img src="https://img.shields.io/badge/license-GPLv2-8993D8?style=flat-square" alt="License GPLv2">
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 5.0+">
</p>

HoursGram for Android is an **unofficial** Telegram client. It is a fork of **[AyuGram4A](https://github.com/AyuGram/AyuGram4A)** by Radolyn, which is a fork of **[exteraGram](https://github.com/exteraSquad/exteraGram)** with patches from **[Telegraher](https://github.com/nikitasius/Telegraher)**, which in turn is built on **[Telegram for Android](https://github.com/DrKLO/Telegram)** by Telegram Messenger LLP.

It is **not affiliated with, endorsed by or sponsored by Telegram FZ-LLC or Telegram Messenger Inc.** "Telegram" is a trademark of its owner.

## What HoursGram adds

- the HoursGram palette, themes (`hoursgram.attheme`, `hoursgram-dark.attheme`) and Montserrat fonts;
- the HoursGram branding and a profile studio;
- a music player with playlists and line-by-line lyrics;
- everything AyuGram4A already has: ghost mode, saved deleted and edited messages, filters and the rest of [its feature list](docs/UPSTREAM_README_AyuGram4A.md).

## Source, licence and attribution

This is a derivative work and it keeps the notices of what it is built from. The people named have not approved this client and did not write the changes made here.

| Project | Author | Licence |
|---|---|---|
| [AyuGram4A](https://github.com/AyuGram/AyuGram4A) | © Radolyn | GPLv2 |
| [exteraGram](https://github.com/exteraSquad/exteraGram) | © exteraSquad | GPLv2 |
| [Telegraher](https://github.com/nikitasius/Telegraher) | © nikitasius | GPLv2 |
| [Telegram for Android](https://github.com/DrKLO/Telegram) | © Telegram Messenger LLP, Nikolai Kudashov | GPLv2 |
| [Montserrat](https://github.com/JulietaUla/Montserrat) | © The Montserrat Project Authors | SIL OFL 1.1, texts in `TMessagesProj/src/main/assets/fonts/hoursgram/` |

Also credited by AyuGram4A: [Cherrygram](https://github.com/arsLan4k1390/Cherrygram), [Nagram](https://github.com/NextAlone/Nagram), [Telegram FOSS](https://github.com/Telegram-FOSS-Team/Telegram-FOSS).

- Source files taken from those projects keep their original headers. This repository starts from a snapshot of AyuGram4A (`rewrite`, commit 701314567) with HoursGram's changes applied; the history and the full list of its authors are in [the upstream repository](https://github.com/AyuGram/AyuGram4A/commits/rewrite).
- HoursGram is licensed under **GPLv2**, the same terms, see [LICENSE](LICENSE). The complete source of every APK is this repository at the commit the build names.
- What was changed is listed in [NOTICE.md](NOTICE.md). The original AyuGram4A README is kept in [docs/UPSTREAM_README_AyuGram4A.md](docs/UPSTREAM_README_AyuGram4A.md).
- Telegram asks every client to use its own `api_id` and `api_hash` (<https://my.telegram.org>). Set the secrets `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` before handing builds to other people.
- The application id is `com.hoursgram.messenger`, not AyuGram's, so the two can be installed side by side and are not mistaken for each other. `TMessagesProj/google-services.json` is a placeholder: the Firebase project of AyuGram is not used. To get Google push notifications, create your own Firebase project and put its `google-services.json` into the secret `GOOGLE_SERVICES_JSON`.

## Build HorusGram for Android

Use Android Studio with JDK 17 and install these SDK components from SDK Manager:

- Android SDK Platform 33
- Android SDK Build-Tools 33.0.1
- Android NDK 21.4.7075529
- CMake 3.10.2.4988404

Clone this repository and open its root folder in Android Studio. The private AyuGram
submodule is optional; this fork includes open stubs for builds without it.

Create an `API_KEYS` file in the repository root. It is ignored by Git. Keep your own
signing keystore outside the repository and point `SIGNING_KEY_STORE` to it so repeated
local builds can update the same installation on your phone:

```powershell
keytool -genkeypair -v -keystore "C:\Users\you\keys\horusgram.jks" -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 -alias horusgram
```

Use the keystore and key passwords entered by `keytool` in the `API_KEYS` file below.

```properties
APP_ID = 6
APP_HASH = "eb06d4abfb49dc3eeb1aeb98ae0f581e"
MAPS_V2_API =
SIGNING_KEY_STORE = C:/Users/you/keys/horusgram.jks
SIGNING_KEY_PASSWORD = your-key-password
SIGNING_KEY_ALIAS = your-key-alias
SIGNING_KEY_STORE_PASSWORD = your-store-password
```

For the fastest phone preview, connect an Android device with USB debugging enabled,
select the `betaDebug` variant, and run:

```powershell
.\gradlew.bat installBetaDebug
```

Or build the APK without installing it:

```powershell
.\gradlew.bat assembleBetaDebug
```

The preview APK is written to
`TMessagesProj/build/outputs/apk/beta/debug/` and includes ARM 32-bit and 64-bit
libraries. `betaDebug` skips release shrinking, so use this for design iteration.
On Android 8.0 or newer, Android Studio's **Apply Changes** can update supported
code edits while the app is running; manifest, native-library, and some resource
changes still need a normal Run/restart.

For the full release APK, which takes longer because it builds all four ABIs and
shrinks the app, run:

```powershell
.\gradlew.bat assembleAfatRelease
```

The release APK is written to `TMessagesProj/build/outputs/apk/afat/release/`.

For repeatable GitHub Actions installs, add these repository secrets:
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and
`ANDROID_KEY_PASSWORD`. Encode the private keystore for the first secret with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\you\keys\horusgram.jks"))
```

Without these secrets, Actions signs each APK with a new temporary key; Android
will reject installing it over a previous CI build.

To inspect the actual native Android UI, run the app on the phone and open Android
Studio's **Layout Inspector** from the Running Devices window. It can inspect this
project's runtime Views, which are built in Java rather than Compose.


## Credits

- **[AyuGram4A](https://github.com/AyuGram/AyuGram4A)**, the base of this client, and its authors
- **[exteraGram](https://github.com/exteraSquad/exteraGram)**
- [Telegraher](https://github.com/nikitasius/Telegraher)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [Telegram FOSS](https://github.com/Telegram-FOSS-Team/Telegram-FOSS)
- [Telegram for Android](https://github.com/DrKLO/Telegram)
