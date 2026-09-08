# 5EAT, Atlas and ecuEdit integration review

September 8, 2026. Feasibility review, not implemented support.
RR2 source checkpoint: `d19ae662`. No vehicle commands, application installs,
firmware redistribution or release changes were performed.

## TomFLV 5EAT

Reviewed [upstream](https://github.com/TomFLV/5eat-tcu-reverse-engineering/tree/44b5e8b8fc407883b0cce0123509f93eaf06a59b)
at `44b5e8b8fc407883b0cce0123509f93eaf06a59b`: repository/license,
logger XML, checksum and shift-curve patch sections, simulator orchestration
and SSM transport, plus build documentation. This is not an independent
revalidation of every firmware/table or a completed patch review.

### Useful integration candidates

- Importable Hitachi M32R and Denso SH705x editor definitions, selected by exact
  firmware identity rather than vehicle model alone.
- A separate TCU logger catalog and profile, feeding existing Data, Graph,
  Dashboard and CSV surfaces. Useful channels include gear, ATF temperatures,
  turbine speed and wheel speeds; validate each scale and availability.
- Family-specific checksum handlers, rather than treating a TCU file as an
  engine ROM. The M32R implementation handles additive and balance checks with
  image-dependent variants; Denso uses a separate implementation.
- A shift-schedule curve editor tied to the existing edit/undo/save commands.
  Upstream constrains dragging to stored speed values, keeps pedal-axis values
  separate and omits nonexistent cells. Preserve those semantics when adapting
  the Swing design to RR2's modern desktop UI.
- A headless load/edit/save/reload qualification harness using the application's
  real parser and checksum path. Do not rely solely on an independent Python
  validator reporting success.

See the [editor patch](https://github.com/TomFLV/5eat-tcu-reverse-engineering/blob/44b5e8b8fc407883b0cce0123509f93eaf06a59b/romraider-5eat/patches/romraider-5eat.patch)
and [editor documentation](https://github.com/TomFLV/5eat-tcu-reverse-engineering/blob/44b5e8b8fc407883b0cce0123509f93eaf06a59b/romraider-5eat/README.md).

### Concrete RR2 findings

An offline Java probe using the locally built `shared-core-1.1.5.jar` parsed
the pinned `definitions/5eat_tcu_logger.xml`:

| Check | Result |
| --- | --- |
| Catalog parsed | 136 parameters |
| Parameters targeted at TCU | 136 |
| Parameters with addresses for `ACD1A06000` | 133 |
| Current portable SSM initialization destination | `0x10` (engine), not `0x18` (TCU) |

The reader accepting XML is not runtime logging support. The mobile protocol
builder hardcodes the engine destination while its reply validator currently
accepts engine or TCU sources. Carry an explicit module identity through
initialization, selection and every read, and require replies from that module.
The portable selection service already supports target-bit filtering.

The reviewed RR2 tree has neither `ChecksumSUBARUTCU` nor
`ChecksumSUBARUTCUDENSO`. Its checksum factory dynamically resolves those names;
simply adding editor XML is therefore insufficient. A missing/invalid handler
must not result in a supposedly ready-to-flash save. Test corrupted input,
ambiguous variants, untouched bytes, edit/undo, output reload and both families.
Do not blindly apply the entire upstream patch: it also changes startup,
settings, branding and Swing UI that RR2 has independently modified.

Keep engine and TCU catalogs/profiles distinct initially. Parameter IDs are
catalog-local; merging identically named IDs is not a safe multi-module design.
Simultaneous ECU/TCU logging requires scheduling and module-qualified identities,
not merely adding a second dropdown entry.

### Simulation and licensing limits

Use the simulator as a separate offline test source first. Its mock transport
synthesizes memory from a vehicle model; this is not evidence of instruction-level
M32R/SH705x ROM execution. The live serial transport is explicitly marked for
bench verification and its J2534 path is a stub. Do not replace RR2's working
OpenPort transport with that path. RAM pokes and CAN bench actuation stay outside
the read-only logger. See [transport source](https://github.com/TomFLV/5eat-tcu-reverse-engineering/blob/44b5e8b8fc407883b0cce0123509f93eaf06a59b/5eat-tcu-simulator/tcu_sim/ssm_transport.py).

The [upstream license](https://github.com/TomFLV/5eat-tcu-reverse-engineering/blob/44b5e8b8fc407883b0cce0123509f93eaf06a59b/LICENSE)
permits reuse of the original definitions/tools under MIT, with notices retained;
RomRaider patches have their own GPL terms. Firmware, decompilations, community
logs and forum material are excluded from the blanket MIT grant. Do not bundle
the whole repository in RR2. No third-party files were added to RR2 by this review.

## Atlas

The current product is NAMR Atlas, not the historical open-source fork found in
search results. The [official FAQ](https://motorsportsresearch.org/faq) says it
is closed-source, and the [current repository license](https://github.com/motorsportsresearch/atlas-public/blob/main/LICENSE)
reserves rights. Learn from documented behavior and implement RR2-native
features; do not copy current code, assets, definitions or firmware patches.
Any proposed reuse of an older open-source revision needs a separate provenance
and license review of that exact revision.

| Documented idea | Proposed RR2 application |
| --- | --- |
| Project-centered calibration workflow | A portable workspace linking ROM revisions, exact definition versions, logger profiles, gauge layouts and notes; keep a protected baseline. |
| Distinguish baseline changes from unsaved changes | Extend existing RR2 change summaries with clear badges and a review-before-save screen. Do not imply a last-flashed state without verified history. |
| Alarm-triggered logging with preceding history | A bounded pre-trigger buffer so a captured event includes its lead-up; preserve ordinary CSV output and missing-sample semantics. |
| Central Problems panel | Aggregate invalid mappings, missing units, unavailable adapters and checksum problems with links to the responsible setting/table. |
| Compare/apply import workflow | Preview individual table changes, verify identity/scaling/axes, and apply as an undoable transaction. Not automatic cross-ROM tune transfer. |

Sources: [feature overview](https://github.com/motorsportsresearch/atlas-public),
[2026.1 logging and change indicators](https://motorsportsresearch.atlassian.net/wiki/spaces/ATLAS/pages/44859393/Atlas+2026.1),
[2026.2 Problems panel and device selection](https://motorsportsresearch.atlassian.net/wiki/spaces/ATLAS/pages/138346497/Atlas+2026.2),
[compare/apply imports](https://motorsportsresearch.atlassian.net/wiki/spaces/ATLAS/pages/464715778/Import+Export+Features+Tables).

Its [current adapter matrix](https://motorsportsresearch.atlassian.net/wiki/spaces/ATLAS/pages/49610753/Supported+OBDII+Devices)
also makes OBDX Pro VX worth investigating alongside OBDLink EX. Their measured
rates and flashing support belong to Atlas's supported platforms, not RR2's
SSM/MUT-II implementations. Treat this as a research candidate, not a compatibility
promise. Live-tuning and compiled custom logic are later firmware-specific work,
not UI features that can simply be transplanted.

## ecuEdit

Assumed identity: epifanSoftware's ecuEdit, not the unrelated ECUEdit forum.
Reviewed the vendor feature matrix and tutorial index, not a locally installed
licensed application. The vendor's [edition matrix](https://www.epifansoft.com/ecuEdit-versions-prices.html)
and [tutorials](https://www.epifansoft.com/ecuEdit-video-tutorials.html) identify
LogLink/map tracing, map calculations, map comparisons, XML description editing,
custom logs and racing-video overlays as useful study areas. No open-source
reuse grant was established; use independently implemented behavior.

RR2 already has offline map tracing, comparisons, binned analysis and selection
math. Improve those existing paths rather than describing them as missing:

- A clearer log-to-table workflow: select a sample, inspect the corresponding
  map location and compare commanded versus logged values where mappings exist.
- User-defined calculated channels with units, dependency checks and explicit
  handling of missing values. Preserve raw data alongside calculations.
- A definition/scaling inspector with raw-to-engineering-value previews and
  inverse conversion checks. Similar-ROM discovery should produce reviewable
  candidates, not automatically accepted addresses.
- Save/reuse analysis arrangements and channel mappings. Video overlays are
  optional later work, behind reliable logging and calibration review.

## Recommended sequence

1. Continue adapter compatibility work while adding explicit module identity to
   the shared/mobile SSM design; test wrong-module and stale-response rejection.
2. Qualify a separate desktop 5EAT logger catalog/profile, then Android TCU
   logging. Require exact controller identification and supervised hardware tests.
3. Adapt checksum handlers and validate editor definitions through real RR2
   save/reload tests. Add the shift-curve editor after storage semantics pass.
4. Extend log-to-map analysis, calculated channels, pre-trigger recording and
   the Problems panel. Package workspace persistence as a separate feature.
5. Consider simulator integration and live tuning only after the read-only and
   offline-editing foundations are qualified. No TCU flashing is enabled here.
