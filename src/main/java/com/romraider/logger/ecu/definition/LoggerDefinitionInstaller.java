/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.definition;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Validates and installs a Logger definition into managed user storage. */
public final class LoggerDefinitionInstaller {
    static final String DEFINITION_RESOURCE = "/definitions/logger.dtd";
    static final long MAX_DEFINITION_BYTES = 32L * 1024L * 1024L;
    private static final String MANAGED_DIRECTORY = "definitions/logger";
    private static final String INSTALLED_FILE = "logger.xml";
    private static final String BACKUP_FILE = "logger.previous.xml";
    // Serialize managed-file commits/rollback across installer instances in this JVM.
    private static final Object INSTALL_LOCK = new Object();
    private static final Map<Path, WeakReference<Installation>> ACTIVE_INSTALLATIONS =
            new HashMap<Path, WeakReference<Installation>>();
    private static final Pattern LEGACY_DTD_IDENTIFIER = Pattern.compile(
            "(?s)(<!ATTLIST\\s+[^>]*?\\s)(?:IDREFS?|ID)"
            + "(\\s+(?:#REQUIRED|#IMPLIED|#FIXED))");

    public Installation install(Path source, Path settingsDirectory)
            throws Exception {
        return prepare(source).install(settingsDirectory);
    }

    /** Validate a frozen copy without replacing a managed definition or its backup. */
    public PreparedDefinition prepare(Path source) throws Exception {
        Path realSource = requireSource(source);
        Path staged = Files.createTempFile("rr2-logger-validation-", ".xml");
        try {
            Files.write(staged, readSnapshot(realSource));
            normalizeLegacyInternalDtd(staged);
            String version = validate(staged);
            return new PreparedDefinition(realSource, version, readSnapshot(staged));
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static final class PreparedDefinition {
        private final Path sourcePath;
        private final String version;
        private final byte[] snapshot;

        private PreparedDefinition(Path sourcePath, String version, byte[] snapshot) {
            this.sourcePath = sourcePath;
            this.version = version;
            this.snapshot = snapshot;
        }

        public Path sourcePath() { return sourcePath; }
        public String version() { return version; }
        public byte[] snapshot() { return snapshot.clone(); }

        /** Install exactly the validated bytes, without reopening the chosen source. */
        public Installation install(Path settingsDirectory) throws IOException {
            return installSnapshot(this, settingsDirectory);
        }
    }

    private static Installation installSnapshot(PreparedDefinition prepared, Path settingsDirectory)
            throws IOException {
        Path destinationDirectory = settingsDirectory.toAbsolutePath()
                .normalize().resolve(MANAGED_DIRECTORY);
        Files.createDirectories(destinationDirectory);
        destinationDirectory = destinationDirectory.toRealPath();

        Path staged = Files.createTempFile(destinationDirectory,
                "logger-install-", ".xml");
        try {
            Files.write(staged, prepared.snapshot);
            byte[] installedDigest = digest(prepared.snapshot);
            Object installedKey = Files.readAttributes(staged,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
            synchronized (INSTALL_LOCK) {
                Path destination = destinationDirectory.resolve(INSTALLED_FILE);
                Path backup = destinationDirectory.resolve(BACKUP_FILE);
                byte[] previous = Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                        ? readSnapshot(destination) : null;
                installDtd(destinationDirectory);
                if (previous != null) writeSnapshot(backup, previous);
                moveReplacing(staged, destination);
                Installation result = new Installation(destination, prepared.version,
                        previous == null ? null : backup, previous, installedDigest,
                        installedKey);
                ACTIVE_INSTALLATIONS.entrySet().removeIf(entry -> entry.getValue().get() == null);
                ACTIVE_INSTALLATIONS.put(destination, new WeakReference<Installation>(result));
                return result;
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static byte[] readSnapshot(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Managed Logger definition is not a regular file.");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes((int) MAX_DEFINITION_BYTES + 1);
            if (bytes.length > MAX_DEFINITION_BYTES)
                throw new IOException("Managed Logger definition exceeds the size limit.");
            return bytes;
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void writeSnapshot(Path destination, byte[] bytes) throws IOException {
        Path staged = Files.createTempFile(destination.getParent(), "logger-rollback-", ".xml");
        try {
            Files.write(staged, bytes);
            moveReplacing(staged, destination);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /**
     * Current Logger definitions legitimately reuse identifiers inside
     * separate protocols and transports. Older embedded DTDs declared those
     * attributes as XML ID/IDREF, which incorrectly makes them document-wide
     * and causes modern Xerces to reject official definitions. Normalize only
     * the managed copy's declarations to CDATA, matching the bundled DTD.
     */
    static void normalizeLegacyInternalDtd(Path definition)
            throws IOException {
        String xml = Files.readString(definition, UTF_8);
        String normalized = LEGACY_DTD_IDENTIFIER.matcher(xml)
                .replaceAll("$1CDATA$2");
        if (!normalized.equals(xml)) {
            Files.writeString(definition, normalized, UTF_8);
        }
    }

    private static Path requireSource(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Select an extracted Logger definition XML file.");
        }
        Path realSource = source.toRealPath();
        String fileName = realSource.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".xml")) {
            throw new IOException("Logger definitions must be XML files.");
        }
        long size = Files.size(realSource);
        if (size < 64L || size > MAX_DEFINITION_BYTES) {
            throw new IOException("The selected Logger definition has an unexpected size.");
        }
        return realSource;
    }

    static String validate(Path definition) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        // Logger's production loader is non-validating because official and
        // customized definitions contain protocol extensions that their
        // legacy embedded DTD does not declare. We still parse the complete
        // document, restrict entity resolution, and verify Logger structure.
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", true);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", true);
        javax.xml.parsers.SAXParser parser = factory.newSAXParser();
        // Xerces applies this access check after resolving our in-memory DTD.
        // Permit the synthetic file URI while the EntityResolver below still
        // rejects every system identifier except logger.dtd.
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "file");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DefinitionHandler handler = new DefinitionHandler();
        parser.parse(definition.toFile(), handler);
        if (!handler.loggerRoot || handler.protocolCount == 0) {
            throw new SAXException(
                    "The selected XML is not a RomRaider Logger definition.");
        }
        return handler.version == null || handler.version.trim().isEmpty()
                ? "unspecified" : handler.version.trim();
    }

    private static void installDtd(Path destinationDirectory)
            throws IOException {
        Path stagedDtd = Files.createTempFile(destinationDirectory,
                "logger-dtd-", ".tmp");
        try (InputStream input = openDtd()) {
            Files.copy(input, stagedDtd, StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(stagedDtd,
                    destinationDirectory.resolve("logger.dtd"));
        } finally {
            Files.deleteIfExists(stagedDtd);
        }
    }

    private static InputStream openDtd() throws IOException {
        InputStream input = LoggerDefinitionInstaller.class
                .getResourceAsStream(DEFINITION_RESOURCE);
        if (input == null) {
            throw new IOException("Bundled Logger definition schema is missing.");
        }
        return input;
    }

    private static void moveReplacing(Path source, Path destination)
            throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class Installation {
        private final Path installedFile;
        private final String version;
        private final Path backupFile;
        private final byte[] previousDefinition;
        private final byte[] installedDigest;
        private final Object installedKey;
        private boolean rolledBack;

        private Installation(Path installedFile, String version, Path backupFile,
                byte[] previousDefinition, byte[] installedDigest, Object installedKey) {
            this.installedFile = installedFile;
            this.version = version;
            this.backupFile = backupFile;
            this.previousDefinition = previousDefinition;
            this.installedDigest = installedDigest;
            this.installedKey = installedKey;
        }

        public Path installedFile() {
            return installedFile;
        }

        public String version() {
            return version;
        }

        public Path backupFile() {
            return backupFile;
        }

        /** Restores this installation's snapshot only while it still owns the managed file. */
        public void rollback() throws IOException {
            synchronized (INSTALL_LOCK) {
                if (rolledBack) return;
                WeakReference<Installation> current = ACTIVE_INSTALLATIONS.get(installedFile);
                if (current == null || current.get() != this)
                    throw new IOException("Logger definition installation was superseded; rollback was not applied.");
                BasicFileAttributes attributes = Files.readAttributes(installedFile,
                        BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if ((installedKey != null && !installedKey.equals(attributes.fileKey()))
                        || !MessageDigest.isEqual(installedDigest, digest(readSnapshot(installedFile))))
                    throw new IOException("Managed Logger definition changed; rollback was not applied.");
                if (previousDefinition == null) Files.delete(installedFile);
                else writeSnapshot(installedFile, previousDefinition);
                rolledBack = true;
                ACTIVE_INSTALLATIONS.remove(installedFile);
            }
        }
    }

    private static final class DefinitionHandler extends DefaultHandler {
        private boolean loggerRoot;
        private int protocolCount;
        private String version;

        @Override
        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            if (systemId == null || !systemId.replace('\\', '/')
                    .endsWith("/logger.dtd")) {
                throw new SAXException(
                        "Only the bundled Logger DTD is permitted.");
            }
            InputSource input = new InputSource(openDtd());
            input.setPublicId(publicId);
            input.setSystemId("file:///romraider2/logger.dtd");
            return input;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            String element = qName == null || qName.isEmpty()
                    ? localName : qName;
            if (!loggerRoot) {
                loggerRoot = "logger".equals(element);
                version = attributes.getValue("version");
            }
            if ("protocol".equals(element)) protocolCount++;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception)
                throws SAXException {
            throw exception;
        }
    }
}
