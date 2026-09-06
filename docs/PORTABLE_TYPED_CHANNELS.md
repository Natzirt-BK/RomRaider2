# Portable typed logger channels

September 6, 2026 audit of 1.1.3 development source. This checkpoint adds
qualification tests and corrects the backlog; typed decoding was already
implemented. It does not add dynamic DimeMod discovery or publish a release.

## Supported decoding

The portable definition reader retains each conversion's `storagetype` and
`endian`. Profile resolution validates the conversion and its concrete addresses
before a query plan is created. Concrete inputs to calculated channels use the
same resolver and decoder as selected outputs.

| Definition | Behavior |
| --- | --- |
| Omitted storage, `uint`, `uint8`, `uint16`, `uint32` | Unsigned integer; 1, 2 or 4 address bytes determine width. |
| `int`, `int8`, `int16`, `int32` | Signed two's-complement integer; address bytes determine width. |
| `float` | Four-byte IEEE 754 single precision, promoted to double before conversion. |
| Omitted endian or `big` | Big-endian byte order. |
| `little` | Little-endian byte order. |

Storage and endian names are case-insensitive. Integer suffixes are historical
logger type labels, not a second enforced byte count: for example, desktop and
portable decoding both accept a two-byte `uint8` channel. Changing that rule
would be a compatibility change. Unsupported types, unsupported byte orders,
non-1/2/4-byte integers and non-four-byte floats are unavailable during profile
resolution, including when used as hidden dependencies.

Unsigned four-byte values retain the full range through 4,294,967,295. Float
subnormals and finite extremes remain measurements. NaN/infinity become missing
data before an expression can mask them with a constant; nonfinite conversion
results are also missing. Calculated outputs propagate missing inputs and resume
on fresh finite data. These rules do not create guessed addresses or turn the
sequential read cycle into a simultaneous ECU snapshot.

## Evidence

- `PortableTypedChannelCheck`, included in both shared-core build graphs, passes
  931 assertions through XML import, profile resolution and query decoding.
  Cases cover all supported integer labels at all three widths, sign/range
  boundaries, default/case-varied byte order, finite/nonfinite floats, hidden
  calculated inputs, invalid direct/hidden definitions and recovery.
- A twenty-float, mixed-endian fixture spans two SSM batches. Tests verify the
  64-address bound, byte reconstruction, output order and rejection of an
  incomplete cycle. All addresses and values are synthetic.
- `PortableTypedCompatibilityTest` passes three desktop JUnit tests comparing
  1,236 conversion results against the actual desktop/JEP converter, including
  sign boundaries, deterministic random integers, scaled values, float
  subnormals/extremes, nonfinite inputs and omitted defaults.
- Existing invalid-reading tests separately verify missing-value CSV fields;
  existing calculated-channel tests cover dependency units and cycle freshness.
  Shared-core checks, including the five-million-value / 64 MiB CSV check, pass.

This is source-level numeric/definition qualification, not physical Android,
OpenPort, ECU or release-package acceptance. No production decoder was changed
in this checkpoint.

The full display-enabled desktop run is **not all green**: 218 of 219 JavaFX
tests pass, including all three new numeric comparisons; one of twenty modal
window-placement repetitions fails. Its trace shows the initial 1,920 × 1,016
fit being replaced by a late 2,120 × 1,244 native size acknowledgement before
the ten-pulse observation. The other nineteen repetitions pass. This reopens
the window-placement investigation; the earlier timing-only test change does
not establish reliable native sizing. The retained 35 Compose results pass.
This finding must not be hidden by reporting only the focused numeric run.

## Still missing for dynamic DimeMod parity

Numeric support alone does not make DM019/DM911 available. Android still needs
a verified source of runtime/version/feature metadata, ECU-bound address
provenance and a discovery flow consistent with its read-only contract. The
legacy desktop discovery handshake sends writes and must not be silently
ported under that label. See the [DimeMod channel audit](DIMEMOD_CHANNEL_AUDIT.md).
