/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.definition;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final Pattern LEGACY_DTD_IDENTIFIER = Pattern.compile(
            "(?s)(<!ATTLIST\\s+[^>]*?\\s)(?:IDREFS?|ID)"
            + "(\\s+(?:#REQUIRED|#IMPLIED|#FIXED))");

    public Installation install(Path source, Path settingsDirectory)
            throws Exception {
        Path realSource = requireSource(source);
        Path destinationDirectory = settingsDirectory.toAbsolutePath()
                .normalize().resolve(MANAGED_DIRECTORY);
        Files.createDirectories(destinationDirectory);

        Path staged = Files.createTempFile(destinationDirectory,
                "logger-install-", ".xml");
        try {
            Files.copy(realSource, staged, StandardCopyOption.REPLACE_EXISTING);
            normalizeLegacyInternalDtd(staged);
            String version = validate(staged);
            installDtd(destinationDirectory);

            Path destination = destinationDirectory.resolve(INSTALLED_FILE);
            Path backup = destinationDirectory.resolve(BACKUP_FILE);
            boolean backupCreated = false;
            if (Files.isRegularFile(destination)) {
                Files.copy(destination, backup,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                backupCreated = true;
            }
            moveReplacing(staged, destination);
            return new Installation(destination, version,
                    backupCreated ? backup : null);
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

        Installation(Path installedFile, String version, Path backupFile) {
            this.installedFile = installedFile;
            this.version = version;
            this.backupFile = backupFile;
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

        /** Restores the definition state that existed before this installation. */
        public void rollback() throws IOException {
            if (backupFile == null) {
                Files.deleteIfExists(installedFile);
                return;
            }
            Path staged = Files.createTempFile(installedFile.getParent(),
                    "logger-rollback-", ".xml");
            try {
                Files.copy(backupFile, staged,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                moveReplacing(staged, installedFile);
            } finally {
                Files.deleteIfExists(staged);
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
