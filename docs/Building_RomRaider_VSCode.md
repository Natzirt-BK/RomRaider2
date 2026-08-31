# Building RomRaider2 in Visual Studio Code

RomRaider2 uses a 64-bit Java 21 JDK as its development and release
baseline. A separate Java installation is not required by end users because
release images include their own runtime.

## Development setup

1. Install Git, a 64-bit Java 21 JDK, Apache Ant 1.10.x, and Visual Studio
   Code with the Java Extension Pack.
2. Set `JAVA_HOME` to the Java 21 JDK and add Ant's `bin` directory to `PATH`.
   `JRE_DIR` is no longer needed.
3. Clone the repository and open its root folder in Visual Studio Code.
4. Run `ant unittest` for the complete build and regression suite, or
   `ant build` for application JARs only. Use `ant clean build` for release
   artifacts so Ant cannot reuse stale class files.
5. Run `packaging/java21/build-linux-app-image.sh` after a successful build to
   create the self-contained Linux application image. Native packages must be
   built on their target operating system. Packaging verifies the hashes in
   `packaging/java21/audited-dependencies.sha256` before staging audited runtime
   dependencies.

The main classes for IDE launch configurations remain:

- Editor/application shell: `com.romraider.ECUExec`
- Standalone legacy logger entry point: `com.romraider.logger.ecu.EcuLoggerExec`

Java 8 is not a supported build or distribution runtime. New features, tests,
and hardware validation use Java 21. The packaging script checks for class-file
major version 65 and rejects stale artifacts compiled by an older JDK.
