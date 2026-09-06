# Active work plan — September 6, 2026

Owner priority: finish the gauge work first and show actual application renders,
then continue the remaining automated work. Physical in-car tests are deferred
until the owner is available; never initiate hardware polling to fill that gap.

1. Nine additional, original gauge designs (owner expanded the original three
   by six): Rally Precision, Circuit Stack, Retro VFD, Club Sport, Sweep Ribbon,
   Twin Arc, Amber Matrix, Vector HUD and Turbo Pod.
   Research Subaru/Mitsubishi and aftermarket instruments; preserve
   clear numeric readings, units, scale labels and unavailable-data states.
   Owner approved these nine and requested a further premium Subaru/Mitsubishi
   pass: STI logo/red night-cluster styling, Evolution-inspired styling, richer
   materials/illumination and carefully bounded visual motion. Keep all nine.
2. Gauges-only view on Android phones/tablets and SteamOS handheld mode, also
   accessible on desktop. Switching views must preserve the same logger session
   and recording. Setup controls stay outside the mounted display. Distinguish
   simulated, live, stale and stopped states. Verify portrait/landscape, small
   windows, view switching and invalid readings without a real ECU.
3. Show actual rendered designs; then checkpoint/push tested work and run fresh
   platform builds. Prepare numeric 1.1.2 publication with Android migration
   guidance, preserving the user's recordings and unsaved ROM work.
4. Desktop editor commands: multiply selection, custom fine/coarse steps,
   reload/revert and ROM properties, with undo/dirty-state safeguards.
5. MAF/injector follow-ups: synchronized filters, interpolation and fitting,
   saved analysis setups, reviewed transfer into ROM tables (not vehicle writes).
6. Read-only log-to-map tracing, binned 2D/3D analysis and run comparisons.
7. Android background-recording design, large-log review and portable setup
   export/import. Gauges-only view switching is not background recording.
   Audit follow-up: portable calculated Logger channels and verified DimeMod
   runtime discovery are needed for full desktop Shinji-profile parity;
   external serial AEM input is not currently supported on Android.
8. Remaining software platform/fork-adoption and scoped dependency audit work.
   Owner follow-up: include ECUFlash and the exact Forester/EVO definition sets,
   then clean up GitHub language, formatting and release/documentation consistency.
9. Later supervised hardware acceptance: intended Forester profile and sustained
   logging, exact EVO/MUT-II/OpenPort/USB-C, Windows adapters, Deck and Macs.
10. Production live tuning/flashing remain target-specific bench-qualified
    milestones, not something to enable merely because automated tests pass.

Checkpoints before gauge development: `79ad42f9` (approved compact editor)
and `a4aca11f` (initial read-only fuel-log analysis), pushed to GitHub `master`
and the development branch. These are source checkpoints, not public releases.

Gauge checkpoint `294791f6` adds the nine approved faces, native mounted views,
unit-aware scales, foreground/session protections and Android CSV continuity
tests. It is on GitHub `master`; hosted desktop builds and Android regressions
passed. The premium STI/Evolution refinement follows this checkpoint.

Premium checkpoint `f75ebba2` adds STI Night and Evolution Night; `e64b3234`
fixes the Compose runtime staging dependency found by clean hosted packaging.
[Platform package run 34022049448](https://github.com/Natzirt-BK/RomRaider2/actions/runs/34022049448)
passed Android, SteamOS and both macOS architectures. These are source/build
checkpoints, not a new public release.

The JavaFX editor command pass completed item 4's selection multiplier,
session-local fine/coarse steps, protected saved-ROM reload and read-only ROM
properties. See [command semantics and test scope](DESKTOP_EDITOR_COMMANDS.md).
The remaining analysis/mobile work in items 5–7 is still pending; neither these
editor commands nor the gauges imply production live-tune qualification.

Items 1, 2 and 4 are implemented and verified: eleven added faces including the
premium STI/Evolution pair, retained-session gauges-only views and the protected
editor commands. Item 3 is complete: [1.1.2 is published](RELEASE_1_1_2.md) with all
seven platform packages and their checksum sidecars. The offline ECUFlash/Forester/EVO and companion-installer audit
is recorded in [the follow-up audit](GAUGE_EDITOR_AUDIT_2026-09-06.md).
Use [the post-1.1.2 plan](POST_1_1_2_WORK_PLAN.md) for concrete next steps and test
gates: reusable analysis setups first, then calculated mobile channels and the
remaining analysis/mobile backlog. Do not treat release publication as completion
of items 5–7 or as hardware qualification.
