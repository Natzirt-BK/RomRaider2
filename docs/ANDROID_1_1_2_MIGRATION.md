# Android installation notes

This page previously documented a pre-launch signing-key transition. It is
retained as a link target; normal setup instructions are in the
[Android guide](ANDROID_PREVIEW_TESTING.md).

Back up recordings and saved work before replacing an installation. If Android
reports a signature mismatch, do not clear storage or uninstall before checking
those backups. The separate OpenPort Test app does not copy the main app's data.

Maintainers should preserve the existing signing identity and follow the
[build-signing guide](ANDROID_UPDATE_RELIABILITY.md). Historical package
verification remains in the [dated audit](GAUGE_EDITOR_AUDIT_2026-09-06.md).
