# SteamOS foundation

SteamOS Desktop Mode can use the existing Linux x64 Java 21 application image.
The first SteamOS target is offline ROM editing, log review, and logging where
the required Linux device access is available. ECU writing is not included.

`packaging/steamos/build-steamos-bundle.sh` wraps the audited Linux image with:

- a path-independent launcher suitable for adding as a non-Steam application;
- an optional Desktop Mode shortcut installer;
- a short, Valve-supported setup path for adding the installed shortcut to
  Steam so it appears under Game Mode's Non-Steam library;
- an application entry whose generic section name is `ECU Tools`;
- one dark handheld profile with larger touch targets and no theme picker;
- a suggested Steam Input mapping for mouse, activation, directional focus,
  and closing dialogs;
- maximized Editor and Logger windows for the Deck's 1280x800 display;
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

The first package remains a preview, but it can be launched from Game Mode
after the user adds its Desktop Mode shortcut as a Non-Steam application.
RomRaider2 does not rewrite Steam's shortcut database. A Flatpak remains a
later packaging option after native-device checks pass.
