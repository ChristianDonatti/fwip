# fwip

A comic reader for Android tablets and phones, inspired by Panels on iOS.
Fluid, simple and made to feel like reading a real comic book.

> Early development. Expect rough edges and frequent changes.

## Features

**Working now**
- Library built from your own folders (subfolders included): CBZ, CBR (including solid archives), CB7 and PDF
- Launcher-style home: cover carousel with an ambient glow that follows the selected comic
- Organization: automatic series, folders, your own collections, read/unread, filters and sorting
- Paper page curl that follows your finger, with the back of the sheet and soft shadows; in landscape on tablets the page turns over the spine of a double spread
- Guided reading, panel by panel or balloon by balloon: panels are found from the page gutters (with the ML model as a fallback) and speech balloons by an on-device model, both in reading order with a spotlight; double tap a balloon to zoom straight into it
- ComicInfo.xml metadata: series and issue numbers, credits, publisher, date, synopsis; manga flagged as right to left opens in manga mode
- Komga, Kavita and OPDS servers: browse and search catalogs, read online page by page (progress synced back to Komga) or download for offline reading
- Next issue card at the end of a comic, wide double-page images shown whole, automatic margin cropping
- Library search, multi-select, pinch and double-tap zoom, manga mode (right to left), volume keys
- English and Portuguese (follows the system, or pick the app language on Android 13+)

## Install a test build

1. On your device, open the [latest test build](../../releases/tag/teste) and download `fwip.apk`.
2. Open the file and allow installing from this source when Android asks.
3. Open Fwip, tap **Choose folder** and pick the folder where your comics live, or connect a server in the **Servers** tab.

Every push to `main` builds a new APK automatically, and it installs over the previous one keeping your library and progress.

## Translations

Texts live in `app/src/main/res/values/strings.xml` (English) and `values-pt/strings.xml` (Portuguese). To add a language, copy the English file to `values-<code>/strings.xml`, translate it and add the locale to `res/xml/locales_config.xml`.

## Building

Requires JDK 17 and the Android SDK.

```
./gradlew assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`.

Test builds are signed with the development key in `keystore/`. It is public on purpose so that anyone's builds can update each other; official store releases will use a private key.

## Tech

Kotlin, Jetpack Compose, LiteRT (TensorFlow Lite), Apache Commons Compress, junrar, Android PdfRenderer, Coil.

## License

Fwip is free software, released under the [GNU General Public License v3.0](LICENSE) or later.
You can use, study, share and modify it, as long as derived versions stay under the same license.

## Credits

Speech balloon detection uses the YOLOv4-tiny model trained by [Seeneva](https://github.com/Seeneva/seeneva-reader-android) (GPL-3.0-or-later, Copyright 2021-2023 Sergei Solodovnikov), see `app/src/main/assets/balloons/NOTICE.txt`.

Fonts: [Unbounded](https://github.com/googlefonts/unbounded) and [Manrope](https://github.com/sharanda/manrope), both under the SIL Open Font License 1.1 (see `FONTS-OFL.txt`).
