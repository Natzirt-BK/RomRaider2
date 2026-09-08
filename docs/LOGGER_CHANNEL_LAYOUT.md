# Desktop channel selection and connection wording

September 7, 2026. Local 1.1.5 follow-up; not yet published or installed.

The main desktop channel rail now lays out equal-width tiles across the available
viewport. Widening the rail adds columns; narrowing it wraps rows without changing
the selected channels. Pixel-rounded widths prevent the last column unexpectedly
wrapping. Each tile places the checkbox/name and its unit label or compact
conversion dropdown on the same row. Long names wrap, and conversion width is
bounded by both its text and the available tile width.

Minus/plus controls offer 80–160% channel sizing in 20% steps. Text, checkbox boxes,
row hit targets and conversion controls scale together. The checkbox label is also
clickable. Size is local to the open logger window; no persisted preference is
added in this change. Search/category/clear behavior and the recording-time unit
change guard are preserved. No selection or connection is triggered by resizing.

User-facing JavaFX wording is removed from the editor/logger titles, logger
heading, editor settings labels and application-provider display name. Internal
class names, service registration, package paths and toolkit documentation remain.

The query manager previously set reconnecting after any failed initialization,
including its first attempt. It now preserves initial Connecting state with
"Connection not established; retrying automatically ..." until a session has
actually initialized and returned from its read loop. Reconnect backoff and
automatic startup preferences are unchanged. This is not an instruction to
connect when merely opening channel controls.

Verification: full Ant unit suite and Linux core build; explicit synthetic
first-connection failure test using an injected connection factory; JavaFX channel
tests run with RR2_FX_WINDOW_SMOKE=1 on a private Xvfb display. The channel tests
cover category/search/clear/selection and unit guards, responsive placement,
inline dropdowns, bounded sizes and actual checkbox growth. Optional render captures
use RR2_CHANNEL_CAPTURE_DIR. No vehicle access is needed or performed.

The pre-existing unfinished LoggerDefinitionInstaller changes are separate from
this work. Previously built 1.1.5 packages do not automatically gain these changes.
