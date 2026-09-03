/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.io;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.romraider.Settings;
import com.romraider.io.BinaryFileIO;
import com.romraider.maps.Rom;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;
import com.romraider.util.XmlSecurity;
import com.romraider.xml.DOMRomUnmarshaller;
import com.romraider.xml.ConversionLayer.ConversionLayer;
import com.romraider.xml.ConversionLayer.ConversionLayerFactory;

/**
 * Toolkit-neutral definition matching and ROM population service.
 *
 * The caller owns threading and presents requests from {@link RomLoadInteraction}.
 */
public final class RomLoadService {
    static final long MAXIMUM_ROM_BYTES = 64L * 1024L * 1024L;
    private static final Logger LOGGER = Logger.getLogger(RomLoadService.class);
    private static final ResourceBundle RB = new ResourceUtil().getBundle(
            "com.romraider.editor.ecu.ECUEditor");

    private final List<File> configuredDefinitions;

    public RomLoadService() {
        this(SettingsManager.getSettings());
    }

    public RomLoadService(Settings settings) {
        if (settings == null) throw new IllegalArgumentException(
                "Settings are required");
        this.configuredDefinitions = settings.getEcuDefinitionFiles();
    }

    public RomLoadResult load(File image, RomLoadInteraction interaction)
            throws IOException {
        if (image == null || interaction == null) {
            throw new IllegalArgumentException("Image and interaction are required");
        }

        interaction.update(text("STATUSPARSING", "Parsing ECU definitions ..."),
                0);
        byte[] input = BinaryFileIO.read(image, MAXIMUM_ROM_BYTES);
        interaction.update(text("STATUSFINDING", "Finding ECU definition ..."),
                10);

        for (File definition : configuredDefinitions) {
            if (definition == null || !definition.exists()) {
                interaction.missingDefinition(definition);
                continue;
            }
            if (!DefinitionFileSupport.isSupported(definition)) {
                LOGGER.warn("Ignoring unsupported ECU definition file: "
                        + definition);
                continue;
            }
            DefinitionMatch match = match(definition, input, interaction);
            if (match == null || match.node == null) continue;
            return loadMatched(image, input, match, interaction);
        }

        File selected = interaction.chooseDefinition(image);
        if (selected == null) {
            return RomLoadResult.empty(RomLoadResult.Outcome.CANCELLED);
        }
        if (!DefinitionFileSupport.isSupported(selected)) {
            interaction.definitionLoadFailed(selected,
                    "Unsupported ECU definition file. Choose "
                            + DefinitionFileSupport.supportedTypes() + ".",
                    null);
            return RomLoadResult.empty(RomLoadResult.Outcome.FAILED);
        }
        DefinitionMatch match = match(selected, input, interaction);
        if (match == null) {
            return RomLoadResult.empty(RomLoadResult.Outcome.FAILED);
        }
        if (match.node == null) {
            if (!interaction.confirmForceLoad(selected)) {
                return RomLoadResult.empty(RomLoadResult.Outcome.NO_MATCH);
            }
            match = new DefinitionMatch(selected, match.document,
                    DOMRomUnmarshaller.findFirstRomNode(
                            match.document.getDocumentElement()));
            if (match.node == null) {
                interaction.definitionLoadFailed(selected,
                        text("UNREADABLEDEF", "No ROM definition was found."),
                        null);
                return RomLoadResult.empty(RomLoadResult.Outcome.FAILED);
            }
        }
        return loadMatched(image, input, match, interaction);
    }

    private RomLoadResult loadMatched(File image, byte[] input,
            DefinitionMatch match, RomLoadInteraction interaction) {
        try {
            DOMRomUnmarshaller unmarshaller = new DOMRomUnmarshaller();
            Rom rom = unmarshaller.unmarshallXMLDefinition(match.file,
                    match.document.getDocumentElement(), match.node, input,
                    interaction);
            rom.setDocument(match.document);
            rom.setDefinitionPath(match.file);
            rom.setFullFileName(image);

            interaction.update(text("POPULATING", "Populating tables ..."), 50);
            rom.populateTables(input, interaction);
            interaction.update(text("FINALIZING", "Finalizing ..."), 90);
            interaction.update(text("DONELOAD", "Done loading image ..."), 95);

            int total = rom.getTotalAmountOfChecksums();
            int valid = total == 0 ? 0 : rom.validateChecksum();
            return RomLoadResult.loaded(rom, valid, total);
        } catch (StackOverflowError failure) {
            LOGGER.error("Looped base definition while loading " + match.file,
                    failure);
            interaction.definitionLoadFailed(match.file,
                    text("LOOPEDBASE", "Looped base attribute in definition."),
                    failure);
        } catch (OutOfMemoryError failure) {
            LOGGER.error("Out of memory while loading " + image, failure);
            interaction.definitionLoadFailed(match.file,
                    text("OUTOFMEMORY", "Out of memory while loading image."),
                    failure);
        } catch (Exception failure) {
            reportDefinitionFailure(match.file, failure, interaction);
        }
        return RomLoadResult.empty(RomLoadResult.Outcome.FAILED);
    }

    private DefinitionMatch match(File definition, byte[] input,
            RomLoadInteraction interaction) {
        try {
            Document document = createDocument(definition);
            Node node = new DOMRomUnmarshaller().checkDefinitionMatch(
                    document.getDocumentElement(), input);
            return new DefinitionMatch(definition, document, node);
        } catch (Exception failure) {
            reportDefinitionFailure(definition, failure, interaction);
            return null;
        }
    }

    private void reportDefinitionFailure(File definition, Throwable failure,
            RomLoadInteraction interaction) {
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = text("LOADEXCEPTION", "Unknown definition error.");
        }
        interaction.definitionLoadFailed(definition, message, failure);
        if (failure instanceof SAXException) LOGGER.error(message);
        else LOGGER.error("Unable to load definition " + definition, failure);
    }

    private static Document createDocument(File definition) throws Exception {
        DocumentBuilderFactory factory = XmlSecurity.newDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        if (ConversionLayerFactory.requiresConversionLayer(definition)) {
            ConversionLayer layer =
                    ConversionLayerFactory.getConversionLayerForFile(definition);
            Document converted = layer == null
                    ? null : layer.convertToDocumentTree(definition);
            if (converted == null) {
                throw new SAXParseException(text("UNREADABLEDEF",
                        "Unable to read the definition."), null);
            }
            return converted;
        }
        try (java.io.FileInputStream stream =
                new java.io.FileInputStream(definition)) {
            return builder.parse(stream, definition.getAbsolutePath());
        }
    }

    private static String text(String key, String fallback) {
        try {
            return RB == null ? fallback : RB.getString(key);
        } catch (RuntimeException missing) {
            return fallback;
        }
    }

    private static final class DefinitionMatch {
        private final File file;
        private final Document document;
        private final Node node;

        private DefinitionMatch(File file, Document document, Node node) {
            this.file = file;
            this.document = document;
            this.node = node;
        }
    }
}
