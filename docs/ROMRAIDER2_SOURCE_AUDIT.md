# RomRaider2 source audit

Last reviewed: 2026-08-31

This file records what was checked in the old forks and branches so useful work
does not get forgotten or merged blindly later.

## Source points checked

- DimeSPb/RomRaider `0389d6c969aabb698caffcd9a4c75528fe15c4f1`
- RomRaider/RomRaider `dafe0c36c1a68efadbeedb2825f3855463fdbc35`
- blicraft/RomRaider `a0ad916dae533cecd4ef7ec1e496796e13624531`
- NatZirt MUT-Raider-II `e070002b5c4cd479f363d297f4b0daa00af9cb75`
- DimeSPb historical branches, including `alpha`, `dev_road_dyno`,
  `dev_olmaf`, `dev_gui_def_overhaul`, and `thegris`

## DimeMod

The DimeSPb baseline retains the useful DimeMod discovery and Logger work:

- DM2.x structure and feature discovery;
- DimeMod-specific SSM queries and address planning;
- diagnostic-code parsing;
- extra Logger parameter and address types;
- DM2.3.100 tip-in and cranking multiplier channels.

The installed DM20 package and archived branch have the same public `DmInit`
surface as the current code. Synthetic tests now cover DM20 version/address
parsing, runtime channel creation, known error bits, bad structure signatures,
and unsupported major versions. Private ROMs, definitions, profiles, and logs
were not copied into the repository.

## blicraft review

Recovered or already covered:

- Offline analysis and linked custom graphs were reimplemented around the
  current Logger data model.
- Logger workspace selection was already saved and restored.
- Ctrl+1 through Ctrl+7 Logger workspace shortcuts were recovered with a new
  focused test.
- Runtime theme changes are handled by the current theme service.
- Diagnostic logging is always available locally, its level can be changed,
  and its folder can be opened from the Logger. A separate on/off file toggle
  would make support logs less reliable, so it was not carried over.

Deferred:

- The ELM327 PID-bitmask and variable-address changes need their own protocol
  tests and real ELM/vehicle checks. They are not required for the Subaru RC3
  path and were not merged as an unverified side change.

Rejected:

- ISO15765 memory writes, ECU reset additions, and automatic tuning
  recommendations. They do not meet RomRaider2's write-safety boundary.

## Historical branch review

- `dev_road_dyno`: its OpenPort 2.0 lowercase `time` CSV fix is already in the
  current Dyno loader.
- `dev_olmaf`: this is an unfinished 2010 open-loop MAF experiment. It contains
  commented-out controls, assumes specific parameter IDs, and has no tests. It
  needs a fresh design and wideband validation rather than a direct merge.
- `dev_gui_def_overhaul` and `dev_xmlmerge`: incomplete metadata experiments
  that are superseded by the current definition loader and manager.
- `thegris`: an unfinished gauge-warning/profile experiment. Warning behavior
  should be rebuilt as tested, tab-specific Logger settings instead of taking
  the old branch wholesale.
- Old installer-tool branches are unrelated to the current Java 21 application
  images.

## Mitsubishi Lancer Evolution

The MUT-Raider-II work supplied a small read-only MUT-II protocol path with
synthetic tests. It does not add ECU reset or write behavior. Vehicle
definitions and profiles stay outside this software repository. The protocol
still needs an in-car qualification pass before the README can call it fully
supported.
