# Notice

HoursGram for Android is a modified version of other people's free software. This file says whose, what was changed, and how to reach us.

## Where this code comes from

1. **Telegram for Android** — https://github.com/DrKLO/Telegram  
   Copyright (c) Telegram Messenger LLP and Nikolai Kudashov. GNU GPL version 2.
2. **exteraGram** — https://github.com/exteraSquad/exteraGram  
   Copyright (c) exteraSquad. GNU GPL version 2. Its sources are in `com.exteragram.messenger`.
3. **Telegraher** — https://github.com/nikitasius/Telegraher  
   Patches by nikitasius, carried by AyuGram4A.
4. **AyuGram4A** — https://github.com/AyuGram/AyuGram4A  
   Copyright (c) Radolyn. GNU GPL version 2. Its sources are in `com.radolyn.ayugram`. This repository starts from a snapshot of AyuGram4A (branch `rewrite`, last commit 701314567 of 28 July 2023) with HoursGram's changes applied; the full history with all its authors is in the upstream repository.
5. **Montserrat** (fonts) — Copyright (c) The Montserrat Project Authors. SIL Open Font License 1.1, texts in `TMessagesProj/src/main/assets/fonts/hoursgram/`. The font files are used unmodified.

Cherrygram, Nagram and Telegram FOSS are credited by AyuGram4A for code it carries, and are credited here for the same reason. Third-party libraries keep their own licences, as in the upstream projects.

## What HoursGram changed

Dates are those of the commits in the git history, from October 2026.

- the name, icons, version number (1.0.0) and the Android package build configuration;
- the HoursGram palette, themes and the Montserrat fonts, applied to the interface;
- a profile studio and a music player with playlists and lyrics;
- build files (`.github/workflows`) that produce universal and arm64 APKs.

Files that were changed keep the header of their origin. Files that are entirely new carry the HoursGram header.

## Licence of this work

HoursGram for Android is distributed under the **GNU General Public License version 2**. You may use, study, change and share it under those terms. The complete corresponding source of every APK is this repository, at the commit named by the build. This program comes with **no warranty**, to the extent permitted by law.

## Not Telegram

HoursGram is an independent, unofficial client. It is not made, approved or supported by Telegram FZ-LLC or Telegram Messenger Inc. Telegram is a trademark of its owner. This project does not use the Telegram logo as its own.

## Telegram API credentials

Telegram asks every client to use its own `api_id` and `api_hash`, issued at <https://my.telegram.org>. The workflow reads them from the repository secrets `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH`. Builds made without them should not be given to other people.

## If you are an author and something is missing

If you wrote part of this code and think your credit, your licence or your terms are not respected here, open an issue on this repository or write to the maintainer through the profile page. We will correct it quickly, and remove the code if you ask us to.
