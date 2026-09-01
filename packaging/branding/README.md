# RomRaider2 visual resources

This archive contains the approved lower-shine, satin/matte RomRaider2 branding set plus build-ready derivatives.

## Approved visual direction

Use the matte/satin assets in this archive as the current source of truth. Do not substitute the earlier high-gloss versions.

The application name is **RomRaider2**.

## Primary assets

- `01_master_assets/romraider2_logo_horizontal_master.png`
  - Master transparent horizontal wordmark.
- `01_master_assets/romraider2_app_icon_master.png`
  - Master transparent R2 application icon.
- `01_master_assets/romraider2_rom_file_icon_master.png`
  - Master transparent `.ROM` file association icon.
- `01_master_assets/romraider2_splash_master.png`
  - Master splash/about/installer artwork.

## Ready-to-use output

- `03_application_icons/png/`
  - Application icons from 16 through 1024 pixels.
- `04_rom_file_icons/png/`
  - `.ROM` file icons from 16 through 1024 pixels.
- `05_platform_packages/windows/`
  - Multi-resolution Windows `.ico` files.
- `05_platform_packages/macos/`
  - macOS `.icns` files when supported by the packaging runtime.
- `05_platform_packages/linux/hicolor/`
  - Standard Linux hicolor app and MIME icon layout.
- `06_java_drop_in/src/main/resources/`
  - A Java resource tree that can be copied directly into the project.
- `resource-mapping.properties`
  - Suggested classpath paths for the Java build.

## Integration direction

1. Preserve aspect ratio. Never stretch the horizontal logo.
2. Use the square R2 icon for the JFrame icon list, taskbar, launcher, installer, shortcuts, and package metadata.
3. Give Java/Swing several icon sizes through `Window#setIconImages(...)` so the OS can select an appropriate resolution.
4. Use the `.ROM` icon only for ROM file associations and ROM-oriented file views.
5. Use the splash artwork for startup, installer, about screen, release pages, or marketing material. Do not leave the splash visible during slow ECU operations.
6. Keep toolbar/navigation icons as clean vector SVGs where possible. These branding images are raster PNG masters and should not be mistaken for editable vectors.
7. Do not add extra glow, chrome shine, racing flames, or unrelated automotive imagery. The approved finish is restrained satin/matte graphite with red accents.
8. Verify the `RomRaider2` project name and any upstream naming/trademark obligations before public distribution.

## Suggested Java icon loading

```java
List<Image> icons = List.of(
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-16.png"),
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-32.png"),
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-48.png"),
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-64.png"),
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-128.png"),
    loadImage("/com/romraider2/ui/assets/icons/app/romraider2-app-256.png")
);
frame.setIconImages(icons);
```

Adapt paths to the actual package structure rather than hard-coding filesystem locations.
