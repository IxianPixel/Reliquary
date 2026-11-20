Reliquary for Linux, portable

Reliquary is a client for Tube Archivist. This folder runs on its own: unpack it
anywhere and start bin/Reliquary. Java is included.

The video player uses your system's VLC 3 (libVLC), so install it first:

  Debian, Ubuntu:   sudo apt install libvlc5 vlc-plugin-base vlc-plugin-video-output
  Fedora:           sudo dnf install vlc   (from RPM Fusion)
  Arch, CachyOS:    sudo pacman -S vlc     (and the plugin packages, if your VLC is split)
  openSUSE:         sudo zypper install vlc

Sign-in is kept in your desktop's keyring (GNOME Keyring or KWallet), so one
needs to be running. Your data and downloads are in ~/.local/share/reliquary
(or $XDG_DATA_HOME/reliquary). Deleting this folder doesn't remove them.

https://github.com/IxianPixel/Reliquary
