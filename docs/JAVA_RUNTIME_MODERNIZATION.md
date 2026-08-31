# Java runtime modernization audit

Status: Java 21-only build and reproducible Linux application-image checkpoint,
2026-08-28. Java 8 is not a supported build or end-user runtime.

## Decision

Java 21 64-bit remains the preferred application and distribution target. The
current evidence does not justify keeping RomRaider2 on Java 8. A Linux
Java 21 in-car checkpoint has now opened an OpenPort 2.0 through J2534,
identified the Subaru ECU, and received an AEM UEGO serial stream. RomRaider2
is not yet hardware-release ready: the corrected logger path needs a second
connected run, and Windows driver, disconnect, and interrupted-session tests
remain release gates. Those gates apply to the Java 21 product; they are not a
reason to retain Java 8.

The complete current source and 146-test suite also pass when compiled
directly with `--release 21`; the resulting application classes use class-file
major version 65. `jdeps --jdk-internals` and the Java 21 `jdeprscan` report no
application-JAR blockers. The Ant build therefore defaults to Java 21 source
and bytecode. Deprecated reflective construction, input masks, boxed-value
constructors, locale construction, rounding constants, and command-string
process launching have been replaced; the Java 21 compiler now reports no
deprecation or removal warnings in application source. Because old incremental
build directories can still contain stale
classes, the Java 21 packager rejects any application JAR whose entry point is
not class-file major version 65. Release builds must be clean builds.

The migration must keep communication and flashing code behind backend APIs.
Swing components may display state and progress, but must not own a J2534
handle, serial port, ECU session, or flash state machine.

## Verified baseline

The repository originally declared Java source and target `1.8`, forced the
`javac1.8` compiler adapter, supplied a Java 8 `rt.jar` boot classpath, and used
Ant's JavaScript task to uppercase the build date. The last item depends on
Nashorn, which was removed in Java 15.

The initial audit made the old Ant build dual-runtime capable long enough to
separate source compatibility from hardware compatibility:

- `javac.release=8` is used by JDK 9 and later; Ant ignores it on JDK 8 and
  retains the existing source, target, and bootclasspath behavior.
- the compiler adapter is selected by the running JDK instead of being forced
  to `javac1.8`;
- the machine-specific fallback JRE path is replaced by `${java.home}/lib`;
- the date label no longer needs a JavaScript engine and still renders as
  `BERGER-AUG28` rather than changing the visible version format;
- RomRaider's logger `Module` type is imported explicitly to avoid collision
  with `java.lang.Module` on Java 9 and later.

Evidence collected on Linux x86-64:

| Runtime/toolchain | Source compatibility | Runtime smoke result | Meaning |
| --- | --- | --- | --- |
| Java 8 | Final compatibility-only build succeeded | The 137-test suite passed before support was removed | Historical comparison evidence only; it is no longer a supported build. |
| Java 11 | 638 production sources previously compile with `--release 11` | Earlier 42-test focused suite passed | No Java 11 language/API blocker was found; rerun the expanded suite before calling this a release rung. |
| Java 17 | 638 production sources previously compile with `--release 17` | Earlier 42-test focused suite passed | No Java 17 language/API blocker was found; rerun the expanded suite before calling this a release rung. |
| Java 21 | Ant builds source and bytecode with `--release 21` | Current suite passes; the packaged app opened a real OpenPort/J2534 ISO9141 session and identified a Subaru ECU | Sole development, validation, and distribution baseline. |
| Java 26 | Not used as a build JDK | 39 editor/workspace tests pass; JNA 3.4 loads with a restricted-native-access warning | Useful forward signal, not a release target. |

The current test set covers modern editor/workspace behavior, flashing
contracts, runtime architecture, serial enumeration, and native bridge smoke
checks. In addition to automated native loading, an in-car checkpoint opened
an OpenPort 2.0 through the bundled Linux 64-bit `j2534.so`, established
J2534/ISO9141, and identified a Subaru ECU. This proves the Linux/Java 21
discovery and identification path. It does not yet
prove sustained corrected logging, voltage access, reconnect behavior, other
protocols, Windows drivers, or flash safety.

## Runtime and dependency inventory

### Application code

- Swing/AWT remains available through `java.desktop`. The current modern theme
  is RomRaider2 code using the basic Swing delegates; FlatLaf is not currently
  bundled and therefore is not a migration blocker.
- XML definitions and settings use JAXP DOM, SAX, XPath, and Transformer APIs
  from `java.xml`. There is no JAXB, Java EE, or Nashorn dependency in
  production code.
- ROM parsing, table editing, checksums, and settings pass the Java 11/17/21
  compile and runtime smoke set.
- Dynamic protocol, external sensor, and checksum loading uses reflection and
  `URLClassLoader`. These public APIs still work. Reflective construction now
  uses declared constructors instead of the deprecated `Class.newInstance()`.
- Architecture startup checks read `sun.arch.data.model`. This is a system
  property rather than an internal class API. The lookup and common `os.arch`
  fallbacks now live in the tested `RuntimeArchitecture` service instead of
  being duplicated in Editor and Logger.
- Existing logger and ROM loading code already uses `SwingWorker` or EDT
  dispatch in several places. New connection, read, write, verify, and recovery
  sessions must use backend executors and publish immutable state to Swing.

### Native and communication stack

| Component | Current state | Java 21 assessment and action |
| --- | --- | --- |
| JNA | 5.19.1 core and platform artifacts | Upgraded under the Apache-2.0 option. J2534 structure layout and native-library link tests protect the direct mapping. Re-test OpenPort on Linux and registry discovery/J2534 on Windows before release. |
| Linux J2534 | 32- and 64-bit `j2534.so`; 64-bit links to libusb and libudev | Java 21 x64 opened a real OpenPort 2.0 and identified a Subaru ECU over ISO9141. A profile-update race exposed concatenated SSM frames in the fixed-size reader; the reader now consumes one vehicle frame and resynchronizes query changes. Sustained logging, ISO14230/CAN, timeout, unplug, and reconnect still need connected validation. |
| Windows J2534 | Vendor DLL selected through the registry and JNA | A 64-bit JVM can only load a 64-bit DLL in process. Support for 32-bit-only vendor drivers requires an independently licensed out-of-process bridge or a separate helper process; never force the main suite back to 32-bit. |
| jSerialComm | 2.11.4 | Upgraded under the Apache-2.0 option. Java 21 loads the bundled native provider and enumerates ports without opening them. Earlier 2.9.1 evidence received clean AEM UEGO records; sustained in-app AEM sampling and Windows enumeration must be rerun on 2.11.4. |
| Phidget21 | optional Java/JNI plugin with 32- and 64-bit native libraries | Isolate as an optional plugin. Validate vendor license, Java 21 loading, discovery, sampling, disconnect, and absence of a device. It must not block the core suite migration. |
| COM4J | 2011-era jar plus x86/x64 Windows DLLs | Used by Windows Innovate/MTS integration. Its plugin is now explicitly Windows-only so it cannot initialize or install a failing shutdown hook on Linux. Test on Windows Java 21; keep optional and replace or isolate if it prevents the core runtime move. |
| Java3D/Graph3d | Retired from the Java 21 application image | Its renderer accessed internal AWT APIs and could freeze the Swing event thread. `MapVisualizationProvider` now supplies the supported Java2D surface, and packaging verifies that Graph3d, Java3D, vecmath, and its OpenGL native are absent. |

The Linux 64-bit J2534 and Phidget libraries resolve their system libusb/libudev
dependencies on the current machine. The legacy Java3D native library reports
unresolved `libjawt.so` and `libjvm.so` outside a configured JVM library path,
which reinforces its replacement priority.

### Bundled Java libraries

The current `lib/common` directory is a manually maintained binary collection.
Several libraries predate modern dependency metadata and only the repository's
top-level GPL text is packaged. Before a public runtime migration, produce a
locked dependency inventory and distribute every required license and notice.

Priority upgrades, each in its own compatibility change:

1. JNA 5.19.1 core and platform artifacts adopted; connected Linux and Windows validation remains.
2. jSerialComm 2.11.4 adopted; connected AEM and Windows validation remains.
3. Log4j 2.26.1 API/Core adopted with Apache's temporary Log4j 1.2 API bridge;
   migrate the remaining logging call sites incrementally and then remove the
   bridge.
4. JFreeChart 1.0.9/JCommon 1.0.12 to a maintained JFreeChart line when the
   integrated datalog viewer is connected. Current upstream requires Java 11
   and folds former JCommon APIs into JFreeChart, so this is a real source
   migration rather than a jar swap.
5. JUnit 4.12 and other test-only dependencies after production compatibility
   is stable.

Do not update all jars simultaneously. Each native-facing update needs its own
tests and hardware result so a regression can be attributed.

## Version-specific blockers

### Java 11

No remaining source or focused runtime blocker is known. Release qualification
still requires the same native hardware matrix as Java 21. Java 11 is a useful
diagnostic rung, not the intended final distribution.

### Java 17

No remaining source or focused runtime blocker is known. It is a suitable
intermediate CI runtime. The legacy Java3D path has been removed.

### Java 21

No core source/build blocker remains in this checkpoint. The release blockers
are hardware and dependency confidence:

- connected 64-bit OpenPort/J2534 testing on Linux and Windows;
- Windows 64-bit vendor driver discovery and registry handling;
- serial/K-line and external sensor plugin testing;
- upgrade and ABI regression testing for JNA;
- clean-machine tests of the self-contained package;
- full ROM load/edit/save/reload, definition, checksum, logger, and reconnect
  regression suites.

## 32-bit policy

The preferred RomRaider2 process is 64-bit. Java itself does not require a
32-bit runtime for ECU work. Bitness matters only at an in-process native
boundary: the JVM, JNA, and a J2534/vendor DLL must match. A protocol that only
has a 32-bit Windows driver should be exposed through a separate bridge/helper
process if a compatible, maintainable implementation is available. That keeps
the modern UI, ROM workspace, logger, and future flasher in the 64-bit process
and also isolates vendor-driver crashes.

## Packaging direction

The existing release path uses Launch4j/IzPack templates, an externally located
JRE, and Linux scripts that hardcode `lib/linux/32`. The foundation preview also
copies a bundled Java 8 runtime from a machine-local template. This is not the
long-term release design.

Use Java 21 `jpackage` to create a self-contained application image per target
OS. Oracle's Java 21 documentation confirms that `jpackage` supports
non-modular classpath applications and can create or accept a `jlink` runtime;
packages must be built on their target platform.

Proposed layout:

```text
RomRaider2/
  bin/                 platform launcher
  runtime/             bundled Java 21 x64 image
  app/                 RomRaider2 and third-party jars
  native/              platform/architecture-specific native libraries
  plugins/             optional sensor/communication plugins
  licenses/            GPL source offer plus all dependency notices
```

Start with a non-modular `jpackage --type app-image`; do not add `module-info`
until reflection, service loading, and native libraries are understood. Derive
the runtime module list with `jdeps`, then validate it by running the full suite
and all plugin paths. Current analysis identifies at least `java.base`,
`java.desktop`, `java.management`, `java.naming`, `java.sql`, and `java.xml`.
Do not minimize the image further until XML providers, desktop integration,
Windows registry access, serial extraction, and service providers are tested.

Windows and Linux images must be produced and tested separately. Native files
must live at a launcher-controlled path; users should never set `JAVA_HOME`,
install Java, or choose 32/64-bit manually.

The source ZIP launcher now prefers `runtime/bin/java`, falls back to PATH only
for development installs, and selects `lib/linux/64` explicitly. The repeatable
`packaging/java21/audit-runtime-modules.sh` command reports the JDK modules seen
by `jdeps`; its output remains an audit input rather than permission to remove
modules before plugin and hardware validation.

`packaging/java21/build-linux-app-image.sh` now builds a reproducible Linux
`jpackage --type app-image` using a Java 21 JDK. It verifies the expected module
audit, stages the application and common libraries, includes only Linux 64-bit
native libraries and external-plugin descriptors, and writes launcher-controlled
J2534, native-library, and plugin paths.
Before staging, the packager verifies the checked-in SHA-256 lock for the
audited JNA, jSerialComm, and Log4j artifacts. A missing, replaced, or stale
runtime dependency therefore fails the release build instead of silently
producing a package that differs from the tested and licensed set.
The application JAR embeds its localization bundles while retaining the
existing external `i18n` directory as a development override, so the packaged
application does not depend on the repository working directory.

The Linux application image is software-only: it does not contain vehicle ROMs,
editor/logger definitions, profiles, or captured logs. Definition-neutral
defaults use a dedicated `romraider2.settings.dir` override, so the installer
can isolate RomRaider2 state without changing `user.home` or sharing the
legacy RomRaider settings file. Users select separately distributed vehicle
content after installation.

On the current Java 21 build, `jdeps` reports the direct module roots
`java.base,java.compiler,java.desktop,java.management,java.naming,java.rmi,`
`java.scripting,java.sql,jdk.unsupported`. The maintained logging backend adds
the compiler, RMI, scripting, and supported-unsafe roots; they remain explicit
until a later direct-API migration proves they can be reduced safely. The
resulting Linux application image also includes required transitive modules
such as `java.xml` and `java.prefs`. Its
contents and launcher configuration have been inspected successfully. The
test suite passes on Java 21; the final Java 8 comparison run also passed
before that build path was removed. The packaged process has also
completed an OpenPort/ISO9141 identification checkpoint; corrected sustained
logger and AEM operation are the next connected checkpoint. This result does
not replace clean-OS, Windows, disconnect/reconnect, or other-protocol release
gates. Definitions, third-party notices, and source-offer materials are
deliberately not claimed as complete release contents yet.

`jdeps --jdk-internals` remains clean for RomRaider2 application classes.
Log4j Core 2.26.1 itself contains a guarded `sun.misc.Unsafe` optimization, so
the packaged runtime explicitly retains the supported `jdk.unsupported` module.
This is an audited upstream implementation detail rather than new internal-JDK
usage in RomRaider2 code; reassess it when the temporary Log4j 1 API bridge
is removed or Log4j is upgraded again.

References:

- Apache Ant `javac` release behavior: https://ant.apache.org/manual/Tasks/javac.html
- Java 21 `jpackage`: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html
- Java 21 `jlink`: https://docs.oracle.com/en/java/javase/21/docs/specs/man/jlink.html
- JNA upstream: https://github.com/java-native-access/jna
- jSerialComm upstream: https://github.com/Fazecast/jSerialComm
- JFreeChart upstream: https://github.com/jfree/jfreechart
- Apache Log4j maintenance status: https://logging.apache.org/log4j/2.x/download.html

## Incremental execution plan

1. Build, test, validate hardware, and package exclusively with Java 21.
2. Add deterministic ROM/XML/checksum/save-reload and native-link smoke suites.
3. Validate JNA 5.19.1 with Linux/Windows J2534 and OpenPort hardware.
4. Validate jSerialComm 2.11.4 with connected AEM and Windows serial hardware.
5. Replace the unverified Java3D/Graph3d path through the renderer adapter. (Complete.)
6. Run the full editor/logger suite on Java 21, then raise the compiled release
   level in a dedicated commit.
7. Produce Java 21 x64 `jpackage` app images on Linux and Windows. (Linux
   application image complete; repeatable Windows packaging and CI are ready;
   Windows artifact execution and hardware qualification are pending.)
8. Validate on clean machines with no Java installed and with real supported
   hardware before making the bundled runtime the default release.

No ECU write protocol may be enabled as part of runtime modernization. Flash
read/write/recovery validation remains a separate safety program under the
modular flashing architecture.
