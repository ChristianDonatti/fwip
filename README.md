# fwip

A comic reader for Android tablets and phones, inspired by Panels on iOS.
Fluid, simple and made to feel like reading a real comic book.

> Early development. Expect rough edges and frequent changes.

## Features

**Working now**
- Library built from your own folders (subfolders included)
- CBZ, CBR, CB7 and PDF
- Launcher-style home: cover carousel with an ambient glow that follows the selected comic
- Double-page spreads on tablets in landscape, single pages in portrait and on phones
- Page turn animation (flip or slide), pinch and double-tap zoom
- Manga mode (right to left)
- Volume keys turn pages
- Reading progress saved per comic

**Planned**
- Realistic page curl rendered with shaders
- Bubble zoom: tap a speech balloon to zoom into it and step through balloons in reading order (on-device ML)
- Panel-by-panel guided view
- ComicInfo.xml metadata, series and collections
- OPDS, Komga and Kavita servers
- Wide (double) page detection

## Install a test build

1. On your device, open the [latest test build](../../releases/tag/teste) and download `fwip.apk`.
2. Open the file and allow installing from this source when Android asks.
3. Open Fwip, tap **Choose folder** and pick the folder where your comics live.

Every push to `main` builds a new APK automatically, and it installs over the previous one keeping your library and progress.

## Building

Requires JDK 17 and the Android SDK.

```
./gradlew assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`.

Test builds are signed with the development key in `keystore/`. It is public on purpose so that anyone's builds can update each other; official store releases will use a private key.

## Tech

Kotlin, Jetpack Compose, Apache Commons Compress, junrar, Android PdfRenderer, Coil.

## License

Fwip is free software, released under the [GNU General Public License v3.0](LICENSE) or later.
You can use, study, share and modify it, as long as derived versions stay under the same license.

## Credits

Fonts: [Unbounded](https://github.com/googlefonts/unbounded) and [Manrope](https://github.com/sharanda/manrope), both under the SIL Open Font License 1.1 (see `FONTS-OFL.txt`).
