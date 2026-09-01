# SteamOS foundation

SteamOS Desktop Mode can use the existing Linux x64 Java 21 application image.
The first SteamOS target is offline ROM editing, log review, and logging where
the required Linux device access is available. ECU writing is not included.

`packaging/steamos/build-steamos-bundle.sh` wraps the audited Linux image with:

- a path-independent launcher suitable for adding as a non-Steam application;
- an optional Desktop Mode shortcut installer;
- an application entry whose generic section name is `ECU Tools`;
- checks that the Linux Compose renderer is present and Windows/macOS renderers
  and vehicle files are absent.

The bundle does not modify the read-only SteamOS system partition. Its optional
shortcut installer writes only to the current user's XDG application directory.

## Still unverified on Steam Deck

- Desktop Mode launch and scaling on the built-in display.
- Touch, trackpad, controller, and on-screen keyboard behavior.
- Flatpak sandbox interaction if a future Flatpak package is added.
- USB permissions and OpenPort logging through a dock or USB-C adapter.
- Suspend/resume while a logging session is open.

The first package is a Desktop Mode preview. Game Mode presentation and a
Flatpak are later packaging steps after the native-device checks pass.
