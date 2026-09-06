# Shared analysis range — 1.1.3 development source

JavaFX can explicitly share one sample range between **Log Analysis**, **MAF**
and **Injector** for the same loaded CSV. This is separate from the MAF/Injector
[condition link](FUEL_LOG_ANALYSIS.md#linked-maf--injector-conditions-113-development-source).
Public 1.1.2 downloads are unchanged.

In Log Analysis, enter a valid inclusive range and select **Link sample range
with MAF / Injector…**. Review the visible Log Analysis range and confirm before
it replaces both fuel tabs' ranges. Cancellation changes neither fuel tab. The
review is scrollable and defaults to Cancel. A changed input, closed pane or
replaced dataset during review prevents activation.

## Draft, apply, then analyze

Once linked, editing the range fields in any of the three workspaces mirrors
the draft into all three. It cancels stale fuel jobs, clears fuel results/curve
sources and unit confirmation, pauses playback, and clears Log Analysis's table,
charts and statistics. Incomplete or invalid text is mirrored too; no workspace
quietly retains a different last-valid range while presenting new results.

Select **Apply range** in Log Analysis or **Apply shared range** in either fuel
tab. A valid range updates all three together, normalizes the displayed inclusive
sample numbers, rebuilds Log Analysis's views/statistics, and bounds its playback
cursor to that range. Confirm units separately before running either fuel
analysis. Applying does not start playback or launch fuel analysis automatically.
An invalid range leaves the draft visible and the dependent views unavailable.

**Start at cursor** and **End at cursor** edit range fields; the shared Apply
step is still required. **All samples** directly selects/applies that explicit
range across the linked workspaces. Playback navigation and new cursor markers
are unavailable while a shared draft is pending; marker jumps ask for the range
to be applied first. An out-of-range marker jump retains the existing all-samples
expansion, now shared with the fuel tabs. Range sharing does not rewrite markers.

## Same range does not mean the same accepted samples

| Workspace | Samples contributing to its results |
| --- | --- |
| Log Analysis | The applied range; statistics retain their existing finite-value rules per channel |
| MAF | The applied range, valid MAF projections, and that tab's enabled custom, named and rate conditions |
| Injector | The applied range, valid injector projections, and that tab's enabled conditions and fuel assumptions |

The range link copies no channel mappings, conditions, bin widths or fuel
assumptions. Log Analysis labels this as **range only** and explicitly states
that its statistics do not apply MAF/Injector filters. A three-row applied range
can legitimately produce statistics over three values but only one accepted MAF
sample. Sharing fuel conditions between MAF and Injector remains a separate,
explicitly reviewed option; both links can coexist without copying fuel filters
into Log Analysis or creating range-feedback loops.

This connects range selection with Log Analysis's existing cursor, tables,
charts and range statistics. Inspection/statistics of the exact noncontiguous
accepted fuel rows remains follow-up work, not a capability implied by this link.
Large-range statistics also still use the existing calculation path; this is not
the separate bounded-large-log review implementation.

## Lifetimes and preservation

Unchecking the range link preserves the visible range drafts rather than
reverting them. A pending Log Analysis draft remains unavailable until its local
**Apply range** succeeds; the fuel tabs can again validate/analyze their own
ranges independently. Unlinking does not change fuel filter settings.

A successful analysis-setup import disconnects the range link before resetting
the importing tab's range. A failed/wrong-kind import leaves the link intact.
The range-link option remains available for another explicit review after a
successful import. Replacing the CSV or closing a participating pane disposes
the old link; the logger creates a fresh, initially independent link for a newly
loaded CSV. Dataset identity is checked, not just matching filenames or row counts.

The link and sample indices are not saved in `.rr2analysis` files. It does not
modify a source CSV or ROM, start a vehicle connection, or send ECU commands.
Its invalidation guards also make a previously opened MAF-transfer review stale
when the shared range changes.

Synthetic native tests cover reviewed activation/cancellation, stale activation,
mirrored invalid drafts, playback suspension, explicit Apply, cursor bounds,
independent versus paired fuel filters, import/replacement lifetimes, actual
fuel counts versus range-only statistics and a 1,000 × 640 rendered workspace.
These are software checks, not vehicle or calibration acceptance.

Local qualification passed 126 JavaFX tests (including nine dedicated range-link
tests), 35 Compose tests, the portable-core checks and Linux JavaFX staging.
