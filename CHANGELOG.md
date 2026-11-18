# Changelog

## Unreleased

### Added

- **Add channels.** Admins can subscribe to a channel from the Channels tab by
  pasting its YouTube link, @handle or ID, and subscribe or unsubscribe on its
  page. Tube Archivist then queues its new videos. (#24)
- **Channel settings.** Admins can change a channel's own settings on the
  server from its page: page sizes for videos, shorts and live streams,
  download format, auto delete, playlist indexing and SponsorBlock. (#26)
- **Add playlists.** Admins can add a playlist from the Playlists tab by
  pasting its YouTube link or ID, and subscribe or unsubscribe on its page.
  Tube Archivist then queues its new videos. (#25)
- **Queue.** A new tab for admins with the server's download queue: rescan
  subscriptions, download everything or one video next, ignore or remove
  videos, add a video, channel or playlist by link, and follow the server's
  progress. Downloads stays for videos saved on the phone. (#23)
- **Desktop app (in progress).** Reliquary now also runs on macOS, Windows and
  Linux, sharing its data, sync and screens with the phone app. So far you can
  sign in, browse your library and play videos, with the same resume,
  progress sync and SponsorBlock skips as the phone. (#2)
- **Desktop releases.** Each release now attaches the desktop app: a zipped
  macOS app for Apple silicon and one for Intel, a Windows installer and a
  Linux .deb, plus the libVLC source the bundled player needs. The builds are
  unsigned, so macOS asks you to right-click the app and choose Open, or run
  `xattr -dr com.apple.quarantine Reliquary.app`, and Windows shows a
  SmartScreen warning: choose More info, then Run anyway. (#29)

### Updated

- **Wide screens.** On tablets, foldables and wide desktop windows, Channels
  and Playlists sit beside the channel or playlist you open. The player shows
  the video with its details and comments below it and similar videos beside
  it, and a theatre button widens the video to the full width. Phones in
  portrait are unchanged. (#11)
- **Smaller desktop download.** The macOS and Windows apps bundle only the
  libVLC plugins the player uses, so the VLC part of the app is about 22 MB
  instead of 91 MB. (#30)

### Fixed

- **Desktop downloads on Windows.** A finished download waits a few seconds
  for a file that a player still has open, rather than failing. Removing a
  download that's still open in a player leaves its file behind, and the app
  logs it. (#31)
- **Offline videos play on desktop.** Saved videos open in the player from
  any folder, including paths with spaces or non-English characters.

## v0.1.0

### Added

- **Initial release.** An Android app for a Tube Archivist (v0.6.0 or later)
  server: browse channels, playlists and search, sign in with a password or an
  API token.
- **Offline downloads.** Download single videos, or keep a channel or playlist
  downloaded automatically. Downloads resume after interruptions, can wait for
  Wi-Fi, and save the thumbnail and subtitles so they're complete offline. The
  Downloads tab shows progress, speed and time left. (#1, #3, #4)
- **Watch progress that syncs.** Your place is saved on the device and sent to
  the server when it can be reached, including progress made offline.
- **Player.** Picks up where you left off, plays playlists as a queue, and
  minimises into a mini-player. Subtitles, SponsorBlock skipping with Undo,
  picture-in-picture, and the comments and similar videos your server saved.
  (#5, #6, #7, #8, #9, #13, #14, #15)
