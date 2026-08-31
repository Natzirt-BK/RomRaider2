# Open-source provenance and reuse register

## Policy

RomRaider2 researches proven open-source implementations before developing a
major ECU-specific feature from scratch. Researching a project does not grant
permission to reuse it and does not mean RomRaider2 has adopted its code.

Before code, constants, memory layouts, kernels, or substantial behavior are
reused or adapted, the contributor must:

1. identify the canonical repository and relevant commit or release;
2. read the repository license and check relevant files for different notices;
3. confirm compatibility with RomRaider2's GPL distribution and release plan;
4. identify transitive origins and licenses for code that project adapted;
5. record the exact files or concepts being considered;
6. review correctness and test against deterministic fixtures or controlled
   hardware rather than trusting the implementation implicitly;
7. adapt the work behind RomRaider2's common core abstractions;
8. retain required notices, copyright, source offers, and modification history;
9. update this register from `Candidate` to `Approved` before importing code;
10. record the final RomRaider2 files and modifications when adoption occurs.

Proprietary, leaked, decompiled, binary-derived, or ambiguously licensed code is
not eligible. Public availability alone is not an open-source license.

## Status meanings

- `Foundation`: upstream work from which this fork is derived.
- `Candidate`: legitimate public project worth technical and licensing review;
  no code adoption is implied.
- `Approved`: a specific reuse scope passed licensing and technical review.
- `Adopted`: approved material exists in RomRaider2 with attribution recorded.
- `Rejected`: unsuitable; the reason is retained to avoid repeating the review.

## Foundation

| Project | Repository | License | Use in RomRaider2 | Notes |
| --- | --- | --- | --- | --- |
| RomRaider | https://github.com/RomRaider/RomRaider | GPL-2.0; source headers state GPL-2.0-or-later | Foundation | Preserve upstream copyright and license notices. New architecture should reuse existing definitions, checksums, logging, and communications where sound. |

## Candidate research register

These entries are research leads only. No source, kernel, constants, or memory
layouts from them are approved or adopted by this table.

| Status | Project | Repository | Reported license | Relevant scope | Review notes |
| --- | --- | --- | --- | --- | --- |
| Candidate | FastECU | https://github.com/miikasyvanen/FastECU | GPL-3.0, with repository warning that some files may differ | Subaru ECU/TCU identification, K-line/CAN flashing, HC16, SH7055, SH7058/SH7058S, SH7059, SH72531/SH72543, recovery workflows | High-priority technical reference. It states that some source and kernels derive from nisprog/npkern forks, so per-file origin and license tracing is mandatory. GPLv3 combination implications require release licensing review before code reuse. |
| Candidate | npkern | https://github.com/fenugrec/npkern | GPL-3.0-or-later | RAM reflashing kernel, ISO14230/K-line command layer, SH7055/SH7058 flash backends, ROM/RAM reads, erase/program primitives | Relevant to Subaru-capable SH705x work and recovery design. Kernel binaries and source must be treated as code, not undocumented assets. No adoption until CPU/ECU applicability and license path are verified. |
| Candidate | FastECU M32R flasher | https://github.com/miikasyvanen/FastECU-m32r-flasher | GPL-3.0 | Bench recovery for specific early Subaru Hitachi ECUs | Recovery-focused and narrowly scoped. Hardware procedures and supported ECU identities must not be generalized. |
| Candidate | NikolaKozina j2534 | https://github.com/NikolaKozina/j2534 | BSD-3-Clause | OpenPort 2.0/libusb J2534 implementation used with RomRaider on Linux and Windows | Permissive candidate for transport work. Preserve BSD notice and review protocol coverage, error handling, concurrency, device ownership, voltage APIs, and platform/bitness behavior. |
| Adopted | j2534-bridge | https://github.com/mickeyl/j2534-bridge | MIT | Out-of-process J2534 bitness isolation | Revision `7234e12c280ae8e91467319a59856f36b81c0e16` is built as static-runtime x86 and x64 Windows helpers. A documented patch preserves raw transmit flags/timeouts and v04.04 ISO15765 flow-control messages required by RomRaider's existing J2534 facade. RomRaider2 supplies an independent Java named-pipe client and chooses the helper only for a mismatched vendor DLL. The vendor driver and registry entry remain unchanged. |
| Candidate — blocked | RomRaider Graph3d.jar | Distributed by https://github.com/RomRaider/RomRaider in `lib/` | No source or component-specific license record located | Existing Java 3D table visualization | The binary exposes an embeddable Swing panel backed by Java 3D, but RomRaider2 will not deepen or adapt this dependency until its canonical source, copyright, and license are identified. Existing standalone behavior remains unchanged. Java 3D itself is a separate dependency and does not establish the license of `Graph3d.jar`. |
| Candidate | Jzy3d | https://github.com/jzy3d/jzy3d-api | BSD-3-Clause | Integrated 3D calibration surface renderer | Upstream documents Swing/AWT support and native JOGL plus CPU-rendered paths. The current development line is snapshot-based and its own test configuration exports internal `sun.awt`/`sun.java2d` packages, so it is not being bundled blindly. Revisit a pinned stable release only after minimal dependency, Java 21, CachyOS/Windows, HiDPI, cleanup, and headless validation; preserve the BSD notice if adopted. |
| Evaluated — not selected | Orson Charts | https://github.com/jfree/orson-charts | GPL-3.0-or-later | Integrated 3D calibration surface renderer | Technically relevant pure-Java Swing renderer, but adopting it would raise the combined distribution's effective licensing floor to GPLv3. Not selected without an explicit project licensing decision. |
| Adopted | JNA | https://github.com/java-native-access/jna | Apache-2.0 OR LGPL-2.1-or-later; Apache-2.0 selected | JNA 5.19.1 core and platform artifacts for J2534 and Windows registry access | Unmodified upstream binaries replace 3.4.0. License notice and hashes are in `licenses/JNA-5.19.1.txt`; J2534 structures use the supported field-order API and have cross-platform layout tests. Linux linking is validated; Windows registry and hardware validation remain required. |
| Adopted | jSerialComm | https://github.com/Fazecast/jSerialComm | Apache-2.0 OR LGPL-3.0-or-later; Apache-2.0 selected | jSerialComm 2.11.4 for serial enumeration, ELM327, and external sensors | Unmodified upstream binary replaces 2.9.1. The complete selected license, Fazecast NOTICE, and artifact hashes are in `licenses/`. Java 21 native loading and enumeration are validated; live AEM and other serial hardware reruns remain required. |
| Candidate | JFreeChart | https://github.com/jfree/jfreechart | LGPL-2.1-or-later | Modern integrated datalog charts | Current upstream requires Java 11 and folds former JCommon APIs into JFreeChart. Adopt only as a dedicated source migration with chart interaction and performance tests. |
| Adopted | Apache Log4j 2 | https://logging.apache.org/log4j/2.x/ | Apache-2.0 | Log4j 2.26.1 API/Core backend and temporary official Log4j 1.2 API bridge | Replaces end-of-life 1.2.14. Configuration and runtime level control use Log4j 2 directly; existing call sites remain behind Apache's bridge for incremental migration. License, notices, and hashes are recorded in `licenses/Apache-Log4j-2.26.1.txt`. |
| Adopted | JEP Java Expression Parser | https://www.singularsys.com/jep/ | GPL-2.0 | JEP 2.4.1 for ROM and logger conversion expressions | Replaces the unversioned March 2006 binary with the official final 2.4.1 jar. The exact original source archive, GPL text, artifact hashes, and notice are retained in the repository. |

## Independent implementations after research

| Component | RomRaider2 files | External code used | Reason |
| --- | --- | --- | --- |
| Java2D calibration surface fallback | `Java2dSurfaceVisualizationProvider`, `Java2dSurfacePanel`, `MapVisualizationRegistry` | None | Provides a Java 21-safe integrated surface behind the replaceable renderer boundary. It uses only supported JDK Java2D APIs while Jzy3d remains under technical review and Orson requires a distribution-license decision. |

## Adopted components

| Field | j2534-bridge adoption record |
| --- | --- |
| Project and repository | j2534-bridge, https://github.com/mickeyl/j2534-bridge |
| Revision | `7234e12c280ae8e91467319a59856f36b81c0e16`, 2026-06-10 |
| License | MIT |
| Upstream files | Rust bridge process built with its locked Cargo dependency graph for `i686-pc-windows-msvc` and `x86_64-pc-windows-msvc` |
| RomRaider2 files | `packaging/java21/build-j2534-bridges.ps1`, `packaging/j2534-bridge/romraider2-j2534-options.patch`, the `BridgeJson`, `J2534BridgeClient`, and `BridgedJ2534` Java classes, and `licenses/j2534-bridge-MIT.txt` |
| Reuse type | Pinned upstream bridge executables with a narrowly scoped source patch plus an independent Java protocol client |
| Modifications | The patch makes the Windows helper console-free, adds optional raw transmit flags/write timeout fields and v04.04 flow-control bytes to the existing JSON protocol, then carries those values into `PassThruWriteMsgs` and `PassThruStartMsgFilter`. RomRaider2 also detects PE architecture, searches both Windows J2534 registry views, and selects direct or bridged loading. |
| Attribution | The complete MIT license and a source/revision record ship in the Windows release. |
| Validation | Java protocol/PE tests and Windows package presence checks; clean Windows x86-DLL/x64-host and real OpenPort connected tests remain required. |
| Reviewer/date | RomRaider2 project audit, 2026-08-31 |

| Field | JNA adoption record |
| --- | --- |
| Project and repository | Java Native Access (JNA), https://github.com/java-native-access/jna |
| Revision | Release 5.19.1 |
| License | Apache-2.0 OR LGPL-2.1-or-later; Apache-2.0 selected for distribution |
| Upstream files | Maven Central `jna-5.19.1.jar` and `jna-platform-5.19.1.jar`, bundled unmodified |
| RomRaider2 files | `lib/common/jna.jar`, `lib/common/platform.jar`, `licenses/JNA-5.19.1.txt` |
| Reuse type | Unmodified binary dependencies |
| Modifications | No upstream modifications. RomRaider2's four J2534 structures moved from removed `setFieldOrder` calls to `Structure.FieldOrder` annotations. |
| Attribution | Upstream `META-INF/LICENSE`, `META-INF/AL2.0`, and `META-INF/LGPL2.1` remain in both jars; release notice and artifact hashes are shipped separately. |
| Validation | Java 21 compile and unit suite; ABI offsets/sizes on the current platform; native J2534 library link without ECU session; Linux OpenPort hardware rerun still required after upgrade; Windows registry and hardware tests pending. |
| Reviewer/date | RomRaider2 project audit, 2026-08-28 |

| Field | jSerialComm adoption record |
| --- | --- |
| Project and repository | jSerialComm, https://github.com/Fazecast/jSerialComm |
| Revision | Release 2.11.4, tag `v2.11.4` |
| License | Apache-2.0 OR LGPL-3.0-or-later; Apache-2.0 selected for distribution |
| Upstream files | Maven Central `jSerialComm-2.11.4.jar`, bundled unmodified |
| RomRaider2 files | `lib/common/jSerialComm-2.11.4.jar` and the three `licenses/jSerialComm-2.11.4*` records |
| Reuse type | Unmodified binary dependency containing upstream platform-native libraries |
| Modifications | No upstream modifications; the runtime smoke test pins the reported provider version and enumerates without opening a port. |
| Attribution | Complete upstream Apache-2.0 license and Fazecast NOTICE are included in the application image. |
| Validation | Java 21 compile and unit suite; native provider load and enumeration on Linux; sustained AEM sampling and Windows serial-device tests pending. |
| Reviewer/date | RomRaider2 project audit, 2026-08-28 |

| Field | Apache Log4j adoption record |
| --- | --- |
| Project and repository | Apache Log4j, https://github.com/apache/logging-log4j2 |
| Revision | Release 2.26.1 |
| License | Apache-2.0 |
| Upstream files | Maven Central `log4j-api`, `log4j-core`, and `log4j-1.2-api` 2.26.1 jars, bundled unmodified |
| RomRaider2 files | Three `lib/common/log4j-*-2.26.1.jar` artifacts, `lib/log4j2.xml`, embedded `src/main/resources/log4j2.xml`, and `licenses/Apache-Log4j-2.26.1.txt` |
| Reuse type | Unmodified binary dependencies plus project-owned configuration |
| Modifications | No upstream modifications. Programmatic configuration and runtime level changes use Log4j 2 Core; Apache's bridge temporarily serves remaining Log4j 1 API calls. |
| Attribution | Each jar retains its complete `META-INF/LICENSE` and `META-INF/NOTICE`; artifact hashes and migration role are shipped separately. |
| Validation | Java 21 compile and unit suite; backend/configuration/bridge tests; application-image packaging; detailed live protocol-log inspection remains a connected test gate. |
| Reviewer/date | RomRaider2 project audit, 2026-08-28 |

| Field | JEP adoption record |
| --- | --- |
| Project and repository | JEP Java Expression Parser, https://www.singularsys.com/jep/ |
| Revision | Release 2.4.1, original GPL source archive dated 2007-04-30 |
| License | GPL-2.0 |
| Upstream files | `dist/jep-2.4.1.jar` and the complete `jep-2.4.1-ext-1.1.1-gpl.zip` source archive |
| RomRaider2 files | `lib/common/jep.jar`, `3rdparty/sources/jep-2.4.1-ext-1.1.1-gpl.zip`, and `licenses/JEP-2.4.1.txt` |
| Reuse type | Unmodified binary dependency and unmodified corresponding source |
| Modifications | None. The official final jar replaces RomRaider's unversioned March 2006 JEP binary while retaining the same `org.nfunk.jep` API. |
| Attribution | The vendor source archive contains copyright notices and the complete GPLv2 license; RomRaider2 adds a component notice and locked SHA-256 values. |
| Validation | Java 21 compile and complete unit suite; conversion-expression and ROM load/edit/save coverage. |
| Reviewer/date | RomRaider2 project audit, 2026-08-29 |

## Required adoption record

When a candidate becomes adopted, add an entry with all fields below:

| Field | Required information |
| --- | --- |
| Project and repository | Canonical name and URL |
| Revision | Commit hash, tag, or release |
| License | SPDX identifier and any file-level exceptions |
| Upstream files | Exact source files, kernels, data, or documentation used |
| RomRaider2 files | Exact destination files |
| Reuse type | Unmodified, modified, ported, or behavioral reference only |
| Modifications | Functional and structural changes made here |
| Attribution | Notices and copyright retained |
| Validation | Tests, fixtures, simulations, and hardware coverage |
| Reviewer/date | Person and review date |

Behavioral-reference-only work still gets an entry when it materially informs
protocol constants, state transitions, memory layouts, security behavior, or
recovery logic. This makes independent implementation claims and provenance
auditable.
