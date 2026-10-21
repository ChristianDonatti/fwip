# Releasing Fwip

Test builds (every push to `main`) are signed with the public development key in `keystore/`, so they always install over each other. Official releases are signed with a private key that only exists in the repository's Actions secrets.

## One-time setup

In the repository on GitHub: **Settings > Secrets and variables > Actions > New repository secret**, and add:

| Secret | Value |
| --- | --- |
| `FWIP_KEYSTORE_BASE64` | the release keystore, base64 encoded |
| `FWIP_KEYSTORE_PASSWORD` | the keystore password |
| `FWIP_KEY_ALIAS` | `fwip` |
| `FWIP_KEY_PASSWORD` | the key password |

Keep a copy of the keystore and its password somewhere safe. If the key is lost, installed copies can no longer be updated by new releases.

## Making a release

1. Write the changelog in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (and `pt-BR`), where versionCode is `major * 10000 + minor * 100 + patch` (0.3.0 is 300).
2. Push a tag: `git tag v0.3.0 && git push origin v0.3.0`.
3. The Release workflow builds `fwip-0.3.0.apk`, signs it and publishes it on the Releases page.

A build signed with the release key cannot update a test build (different signatures): uninstall the test build first, after saving a backup from **Library folders > Sync and backup**.

## F-Droid

The store listing (texts, icon, feature graphic, screenshots, changelogs) is in `fastlane/metadata/android`, which F-Droid reads directly. To be included, open a request for packaging at https://gitlab.com/fdroid/rfp. Their review may ask about the prebuilt LiteRT runtime and the 7-Zip-JBinding native library, which F-Droid prefers to build from source.
