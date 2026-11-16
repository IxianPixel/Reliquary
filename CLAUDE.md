# CLAUDE.md

Guidance for Claude Code (and humans) working in this repo.

## What this is

Reliquary is an Android client for [Tube Archivist](https://www.tubearchivist.com)
(TA) v0.6.0+, the self-hosted YouTube archive. Its reason to exist is
**offline**: download videos to the phone, watch them without a connection, and
sync watch progress back to the server later. Kotlin, Jetpack Compose,
Material 3 Expressive, Catppuccin colours (Latte light, Mocha dark, Mauve accent).

## Commands

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug   # what CI's "Build, test and lint" runs
./gradlew :core:data:testDebugUnitTest                # one module's tests
./gradlew assembleRelease                             # R8 build; unsigned without a keystore
python3 scripts/prepare-release.py 0.2.0              # on a release/* branch only (see Releases)
```

Needs JDK 21. On a Mac without a system JDK, use Android Studio's:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Layout

| Module | Role |
|---|---|
| `core:model` | Plain Kotlin domain types (`Video`, `Download`, `Comment`, `SponsorSegment`…). No Android. |
| `core:network` | Retrofit API (`TubeArchivistApi`), DTOs, auth interceptor, `ServerUrl`, `LocalNetwork`. Retrofit stays internal: wrap `Response<T>` calls here (see `ProgressCalls.kt`). |
| `core:database` | Room. Schema JSON is exported to `core/database/schemas/`. |
| `core:data` | Repositories, mappers (DTO → entity → model), `ProgressResolver`, settings (DataStore), download files. |
| `core:sync` | WorkManager workers: progress sync, library sync (keep-offline rules, filling in missing download extras). |
| `core:download` | `DownloadWorker` and `MediaDownloader` (resumable plain-MP4 downloads). |
| `core:designsystem` | Theme, colours, `ReliquaryIcons` (vector drawables). |
| `core:ui` | Shared composables: video rows, thumbnails, Keep offline sheet, `StatusBarScrim`, local network prompt. |
| `feature:*` | One screen each. `feature:player` also owns `PlaybackService` (media3 `MediaSessionService`) and the player sheet. |
| `app` | Shell: `NavigationSuiteScaffold`, Navigation 3 back stack, player sheet over everything, Hilt app module. |
| `build-logic` | Convention plugins; versions live in `gradle/libs.versions.toml`. |

## Invariants: don't break these

- **Room is the source of truth.** Screens observe Room `Flow`s; the network
  refreshes Room. Anything that works offline must read from the cache.
- **Migrations are append-only.** Bump the version, add an `AutoMigration`,
  commit the exported schema JSON. Never reset or edit an old schema.
- **Local progress wins** unless the server marked the video watched after the
  local session started (`ProgressResolver`). Unsynced progress lives in
  `pending_progress` and is shown over the server's values.
- **Never mark anything watched or unwatched implicitly.** Watched state is
  global across all users on a TA server.
- **Downloads resume with `Range: bytes=N-` and never send `If-Range`** (TA then
  sends the whole file). No ETag: a total that doesn't match `media_size` means
  start over.
- **Credentials only go to the signed-in server's origin** (`AuthInterceptor`).
- **The app talks only to the TA server.** No third-party requests (e.g. comment
  avatars are deliberately not loaded from YouTube), no analytics.
- **Keep `core:model`, `core:network`, `core:database` and `core:data` free of
  Android UI types** so the data layer can move to Kotlin Multiplatform (#2).
  Put platform bits behind interfaces (`DownloadScheduler`, `NotificationIcon`).

## Tube Archivist facts that shape the design

- `POST /api/user/login/` returns session + CSRF cookies, no token. Unsafe
  methods need `X-CSRFToken`, `Origin` and `Referer`. Admins can use a token.
- Progress: `POST /api/video/<id>/progress/`; positions of 5 s or less are
  ignored, so "start over" is a `DELETE`. There's no GET: read `player.position`.
- `watch=continue` with nothing in progress returns a bare `[]`; an empty page
  returns 404. Comments return 404 when none were saved: that means "none".
- Media URLs are server-relative and need auth. Subtitles, comments and
  SponsorBlock data only exist for videos downloaded or refreshed after they
  were enabled in TA's settings.

## Testing

- Unit tests: MockWebServer for repositories and the downloader, Robolectric +
  in-memory Room where the database is involved. Add one for any data-layer
  behaviour change.
- Real-device testing: always ask the user before installing on or driving
  their phone, and never play videos to a point that changes server state
  without saying so (rewind afterwards).

## Workflow

- Work happens on `develop`. `master` only takes PRs and needs the
  "Build, test and lint" check; don't rename that CI job.
- **The user makes every commit.** Don't commit, stage or push; offer a
  one-line commit message instead.
- **Add a CHANGELOG.md entry under `## Unreleased` with every user-visible
  change**, in the same change as the code. Sections: Added, Updated, Fixed,
  Removed. Bold lead sentence, short prose, issue number at the end: `(#12)`.
- GitHub issues use `Kind/*` and `Priority/*` labels and version milestones.
  Close an issue only after the user has committed the fix.

## Releases

1. `release/x.y.z` from `develop`: run `scripts/prepare-release.py x.y.z`
   (renames the changelog section, bumps `versionName`/`versionCode`, writes the
   F-Droid changelog), commit, PR into `master`.
2. After merging, tag master `vx.y.z`. `.github/workflows/release.yml` checks
   the tag, builds the signed APK and publishes a GitHub release whose notes are
   that version's CHANGELOG section.

## Known pins

- **KSP is pinned to 2.3.12.** 2.3.13 loses library types when KSP tasks run
  concurrently (google/ksp#3286, our #16). Upgrade once a release includes the
  fix, then consider removing `ksp.incremental=false`.

## Style

- UI copy: plain, short, UK English. Comments explain why, not what.
- Compose: Material 3 Expressive components; colours from the theme, never
  hard-coded (overlays on video use `OverlayColors`).
