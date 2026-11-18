# Desktop manual tests: Windows and Linux

Unit tests run on GitHub's Windows and Linux runners (the `Desktop tests` job).
This checklist covers what they can't: the real window, the keychain, the
installers and playback. Run it on each platform before a release.

Use a test Tube Archivist server. Don't play a video to a point that changes
server state without expecting it, and rewind afterwards: watch progress is
shared by every user on the server.

Record each result as pass, or fail with what you saw.

## Setup

- [ ] **Windows:** install the `.msi` from the build. Check that the install
      folder contains `libvlc.dll`, `libvlccore.dll` and `plugins\`.
- [ ] **Linux:** install the `.deb` with `sudo apt install ./reliquary_*.deb`,
      which pulls in `libvlc5` and the VLC plugin packages. Check with
      `apt show reliquary` that the Depends line lists them.
- [ ] Start the app from the Start menu or application launcher. The window
      opens, the icon is right, and the app appears in the taskbar or dock.

## Sign-in

- [ ] Sign in with a server URL and admin or user credentials. It reaches the
      library.
- [ ] Quit and reopen. You're still signed in without typing the password.
- [ ] **Windows:** open Credential Manager (Windows Credentials). A
      `Reliquary` entry exists. Don't delete it: sign-in would then fail.
- [ ] **Linux:** with GNOME Keyring or KWallet running, the session key is
      stored there (Seahorse or KWalletManager shows it). Quit and reopen
      still works.
- [ ] **Linux without a keyring** (log out of the keyring session or stop the
      service): sign-in shows an error that names the missing keychain and
      stores nothing unencrypted.
- [ ] Sign out. The session is gone: reopening asks for sign-in.
- [ ] A wrong password shows an error and doesn't crash.

## Single instance

- [ ] With the app open, start it again. The original window comes to the
      front, and no second window or second process stays running.
- [ ] Quit, then start it again: it opens normally.

## Browsing

- [ ] Home, Channels, Playlists, Downloads, Search and Settings open and scroll.
- [ ] Thumbnails load (they come from your server, not YouTube).
- [ ] Search finds a video; its page opens.
- [ ] Resize the window narrow and wide: the layout changes with it and nothing
      is cut off.
- [ ] Text uses the right fonts and colours in light and dark mode (switch the
      system theme).

## Downloads

- [ ] Choose **Keep offline** on a short video. Its progress shows, then it
      moves to Downloads as complete.
- [ ] **Windows:** the file is in `%LOCALAPPDATA%\Reliquary\videos\`, named
      `<video id>.mp4`.
- [ ] **Linux:** the file is in `~/.local/share/reliquary/videos/`, or
      `$XDG_DATA_HOME/reliquary/videos/` when that's set.
- [ ] Stop your network mid-download and reconnect. It resumes rather than
      starting over.
- [ ] Remove a finished download. Its file is gone from the folder.
- [ ] **Windows, locked file:** start playing a downloaded video, then remove
      that download. The app stays responsive. If the file can't go, the app
      log says so (`Couldn't delete ...`) and the download disappears from the
      list. Close the player and check whether the file is still in the folder.
      Note what you see; it's a known gap.
- [ ] **Windows, replace:** download a video, play it, then download it again
      (keep it open in the player while the second download finishes). It
      should complete within a few seconds.
- [ ] Storage use and free space in Settings look right.

## Playback

- [ ] Play a streamed video. Video and audio play in sync, and seeking jumps
      to the right place.
- [ ] Play a VP9 or AV1 video (YouTube uploads often use these). It plays.
      This is the case the bundled libVLC is for.
- [ ] Play a downloaded video with no network: it plays from the file.
- [ ] Resume: play a video to about a minute, close the player, play it
      again. It resumes near where it stopped.
- [ ] Subtitles appear and can be turned on and off, if the video has them.
- [ ] Full screen works; Esc leaves it.
- [ ] SponsorBlock segments skip, if the server has them for the video.
- [ ] Play, pause and volume respond to the keyboard and the window controls.
- [ ] **Linux:** with `libvlc5` removed (`sudo apt remove libvlc5`, then put
      it back), the player says the VLC libraries are missing rather than
      crashing.

## Installers and upgrades

- [ ] **Windows:** install a newer `.msi` over the old one. It replaces the
      old version rather than installing beside it (one entry in Apps).
- [ ] **Windows:** uninstall from Apps. The program files go; your downloads
      and sign-in stay in `%LOCALAPPDATA%\Reliquary`.
- [ ] **Linux:** install a newer `.deb` over the old one. The app starts.
- [ ] **Linux:** remove the `.deb` with `sudo apt remove reliquary`. The app
      is gone from the launcher.

## Report

Note the platform, version (Settings or the `.msi`/`.deb` file name), and any
failures with the app log if there is one. Failures that involve files, keys
or playback are the ones to report first.
