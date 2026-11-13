# Reliquary

An Android app for [Tube Archivist](https://www.tubearchivist.com), the
self-hosted YouTube archive. Browse your archive, download videos to watch
offline, and keep your place in sync with the server. It needs your own Tube
Archivist server, v0.6.0 or later.

## AI disclaimer

> [!IMPORTANT]
> This app has been largely written with AI. I understand that not everyone is
> OK with using apps made this way, which is why this disclaimer is here. I'd
> rather be upfront and honest than pretend no AI was used.
>
> I review every change before it's committed, and each feature is tested on a
> real phone against a real Tube Archivist server.

## Install

Download the APK from the [latest release](https://github.com/IxianPixel/Reliquary/releases/latest),
or add this repository to [Obtainium](https://github.com/ImranR98/Obtainium)
to get updates automatically.

Requires Android 10 or later.

## Build

```bash
./gradlew assembleDebug
```

Needs JDK 21. A release build (`./gradlew assembleRelease`) is unsigned unless a
keystore is set up as below.

## Releasing

1. Add each user-visible change to `## Unreleased` in [CHANGELOG.md](CHANGELOG.md)
   as it lands on `develop`. Sections: Added, Updated, Fixed, Removed.
2. Branch `release/x.y.z` from `develop` and run
   `python3 scripts/prepare-release.py x.y.z`. It renames the changelog section,
   bumps `versionName` and `versionCode`, and writes the F-Droid changelog.
   Review, commit, and open a pull request into `master`.
3. Once it's merged, tag master: `git tag vx.y.z && git push origin vx.y.z`.
   The Release workflow checks the tag, builds the signed APK and publishes a
   GitHub release with that version's changelog as its notes.

The workflow needs these repository secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The release keystore, base64-encoded |
| `RELEASE_STORE_PASSWORD` | The keystore's password |
| `RELEASE_KEY_ALIAS` | The signing key's alias |
| `RELEASE_KEY_PASSWORD` | The signing key's password |

For signed release builds on your own machine, put the same values in a
git-ignored `app/keystore.properties` (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`).

## Licence

[GPL-3.0-or-later](LICENSE).
