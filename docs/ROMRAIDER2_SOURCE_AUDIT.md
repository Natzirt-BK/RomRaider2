# RomRaider2 source audit

Audit date: 2026-08-27

## Pinned inputs

- DimeSPb/RomRaider `0389d6c969aabb698caffcd9a4c75528fe15c4f1`
- RomRaider/RomRaider `dafe0c36c1a68efadbeedb2825f3855463fdbc35`
- blicraft/RomRaider `a0ad916dae533cecd4ef7ec1e496796e13624531`
- NatZirt MUT-Raider-II source `e070002b5c4cd479f363d297f4b0daa00af9cb75`
- NatZirt installer `df6a496d04a02f7c07800ec36fefc0e9f3db1733`

## DimeSPb baseline

The pinned DimeSPb commit merges official RomRaider release commit
`88d9fbd4` and retains 29 DimeMod-side commits. Relative to the official
release, the DimeMod delta changes 38 files with 1,816 insertions and 211
deletions.

The primary DimeMod surface includes:

- `DmInit` capability and runtime structure discovery;
- DimeMod-specific SSM query handling and optimized address planning;
- DimeMod diagnostic-code parsing and display;
- extended logger parameter/address types;
- RAM-tune research/test changes.

The unmodified pinned baseline compiled successfully on the target Linux host
with Java 8. Its non-GUI unit tests passed. The legacy
`XDFConversionLayerTest` requires a real graphical display and fails in a
headless test process; this behavior was present before RomRaider2 changes.

## Official forward port

Only one official commit was newer than the DimeSPb merge base:

- `dafe0c36` fixes the duplicate minus-key mapping and adds bracket keys for
  fine cell decrement/increment.

It was cherry-picked without conflict as RomRaider2 commit `d72eecb7`.

## blicraft disposition

Worthwhile later references:

- analysis and custom-graph tabs;
- logger tab shortcuts and preference persistence;
- debug file logging toggle;
- centralized runtime look-and-feel updates;
- improved ELM327 parsing and associated tests.

Not accepted into the foundation:

- ISO15765 memory writes;
- ECU reset additions;
- unattended or directly applied tuning recommendations.

Those features require the handoff's capability gates, typed realtime service,
identity validation, whitelists, and readback verification before integration.

## Evo VIII / IX MUT-II disposition

The NatZirt MUT-II implementation is a compact protocol addition with seven
dedicated tests. It adds an ISO9141 15,625-baud read-only logger path,
generic definition-parser coverage, and connection initialization. It does not
add ECU write/reset capability. Vehicle definitions and profiles are maintained
outside the software repository.

## Migration safety boundary

RomRaider2 is developed on branch `feature/romraider2-foundation`. The
legacy MUT-Raider-II fork remains listed
above solely as a pinned, audited source input; its separate package and
installer path are retired. DimeMod remains an external Subaru validation
fallback. Historical EvoScan behavior may be used as a comparison reference,
but RomRaider2 does not depend on or package EvoScan.
