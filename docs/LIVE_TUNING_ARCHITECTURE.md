# Live-tuning architecture

RomRaider2 is building live tuning around staged table changes rather than raw
memory commands. The first milestone is deliberately offline: it can plan,
check, apply, and verify changes against the mock ECU transport, but it cannot
write to a vehicle.

## Current boundary

The neutral `com.romraider.livetune` package provides:

- immutable changes containing the expected and replacement bytes;
- identity-free drafts that can be inspected before an ECU connection;
- plans tied to one exact ECU ID and ROM ID;
- 24-bit address, overlap, per-change, and whole-plan limits;
- explicit preflight checks suitable for a future Editor workspace;
- a session state machine from draft through verified or failed; and
- a mock-only executor with stale-value and readback verification.

The executor accepts `MockEcuTransport` directly. There is no overload for the
general ECU transport, so this milestone does not add a production write path.

The Editor Tune inspector projects the selected table or every changed table
into an identity-free draft. It shows address ranges, byte counts,
before/after values, and the currently known safety gates. The panel exposes
only scope and refresh controls; it cannot connect to an ECU or apply a plan.
Surface projections include X/Y axis edits and preserve definition row gaps,
so non-contiguous RAM regions remain separate staged changes.

## Required evidence

A plan is ready only when all of these checks pass:

1. The selected platform is Subaru.
2. The connected module is the engine ECU.
3. DimeMod was detected during the current ECU session.
4. The loaded definition maps RAM Tune tables.
5. The DimeMod runtime advertises RAM Tune.
6. The connected ECU ID and ROM ID exactly match the staged plan.

Before the mock executor writes anything, it reads every staged address and
compares the result with the value captured by the Editor. Each change is read
back immediately after it is applied. A stale value, identity change,
disconnect, rejection, timeout, or readback mismatch fails the session.

## Next steps

- Move the same preview contract into the replacement calibration workspace.
- Define address ranges from verified DimeMod metadata rather than accepting
  an arbitrary RAM address.
- Add cancellation, recovery, and transaction logging.
- Build a separately reviewed production executor only after connected testing
  and per-version DimeMod qualification.

Vehicle RAM writes remain an RC5-or-later qualification item.
