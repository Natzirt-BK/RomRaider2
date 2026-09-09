package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxLoggerConnectionChoicesTest {
    @TempDir Path folder;
    private static final String XML = "<logger><protocols>"
            + protocol("SSM", transport("iso9141", "ecu") + transport("iso15765", "tcu") + transport("invalid", "ecu"))
            + protocol("MUT2", transport("iso9141", "ecu") + transport("iso15765", "ecu"))
            + protocol("UNIMPLEMENTED", transport("iso9141", "ecu"))
            + "</protocols></logger>";

    @Test void listsOnlyDefinitionCombinationsWithPackagedImplementations() throws Exception {
        Path path = folder.resolve("logger.xml"); Files.writeString(path, XML);
        var choices = FxLoggerConnectionChoices.read(path.toString());
        assertEquals(List.of("SSM", "MUT2"), choices.protocols());
        assertEquals(List.of("ISO9141", "ISO15765"), choices.transports("SSM"));
        assertEquals(List.of("ISO9141"), choices.transports("MUT2"));
        assertEquals(List.of("tcu"), choices.modules("SSM", "ISO15765"));
        assertTrue(choices.transports(null).isEmpty());
        assertTrue(choices.modules(null, null).isEmpty());
        assertTrue(choices.modules("MUT2", "ISO15765").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> choices.protocols().add("OTHER"));
    }

    @Test void fastPollingRequiresExplicitSupportOnTheSelectedModule() throws Exception {
        Path path = folder.resolve("fast.xml");
        Files.writeString(path, "<logger><protocols><protocol id='SSM'><transports><transport id='ISO9141'>"
                + "<module id='ecu' fastpoll='true'/><module id='tcu' fastpoll='false'/>"
                + "</transport></transports></protocol></protocols></logger>");
        var choices = FxLoggerConnectionChoices.read(path.toString());
        assertTrue(choices.supportsFastPolling("SSM", "ISO9141", "ecu"));
        assertFalse(choices.supportsFastPolling("SSM", "ISO9141", "tcu"));
        assertFalse(choices.supportsFastPolling("SSM", "ISO15765", "ecu"));
        assertFalse(choices.supportsFastPolling(null, null, null));
    }

    @Test void rejectsEditorXmlMalformedXmlAndDoctypes() throws Exception {
        for (String xml : List.of("<roms/>", "<logger>", "<logger/>",
                "<!DOCTYPE logger [<!ENTITY bad SYSTEM 'file:///missing/rr2-test'>]><logger>&bad;</logger>")) {
            Path path = folder.resolve("invalid.xml"); Files.writeString(path, xml);
            assertThrows(Exception.class, () -> FxLoggerConnectionChoices.read(path.toString()));
        }
        assertThrows(Exception.class, () -> FxLoggerConnectionChoices.read(""));
    }

    @Test void selectorsNeverOfferUnknownSavedValuesOrSilentlyChooseAnotherTransport() throws Exception {
        FxTestRuntime.run(() -> {
            var selector = FxLoggerSetup.connectionSelector("Select transport", "Explanation");
            assertFalse(selector.isEditable());
            FxLoggerSetup.select(selector, List.of("ISO9141"), "iso9141");
            assertEquals("ISO9141", selector.getValue());
            FxLoggerSetup.select(selector, List.of("ISO9141"), "ISO15765");
            assertNull(selector.getValue());
            assertEquals(List.of("ISO9141"), selector.getItems());
            FxLoggerSetup.select(selector, List.of(), "ISO9141");
            assertTrue(selector.isDisabled()); assertNull(selector.getValue());
        });
    }

    @Test void backgroundChoicesCascadeAndClearInvalidDefinitions() throws Exception {
        Path path = folder.resolve("logger.xml"); Files.writeString(path, XML);
        AtomicReference<FxLoggerSetup.SetupChoices> controller = new AtomicReference<>();
        AtomicReference<TextField> definition = new AtomicReference<>();
        AtomicReference<ComboBox<String>> protocol = new AtomicReference<>(), transport = new AtomicReference<>(), target = new AtomicReference<>();
        AtomicReference<Button> save = new AtomicReference<>();
        try {
            FxTestRuntime.run(() -> {
                definition.set(new TextField(path.toString()));
                protocol.set(new ComboBox<>()); transport.set(new ComboBox<>()); target.set(new ComboBox<>()); save.set(new Button());
                controller.set(new FxLoggerSetup.SetupChoices(definition.get(), protocol.get(), transport.get(), target.get(), save.get(), "SSM", "iso9141", "ECU"));
                controller.get().refresh();
                assertTrue(save.get().isDisabled());
            });
            await(() -> !protocol.get().getItems().isEmpty());
            FxTestRuntime.run(() -> {
                assertEquals("SSM", protocol.get().getValue()); assertEquals("ISO9141", transport.get().getValue());
                assertEquals("ecu", target.get().getValue()); assertFalse(save.get().isDisabled());
                transport.get().setValue("ISO15765");
                assertEquals(List.of("tcu"), target.get().getItems()); assertNull(target.get().getValue());
                assertTrue(save.get().isDisabled());
                target.get().setValue("tcu"); assertFalse(save.get().isDisabled());
                protocol.get().setValue("MUT2");
                assertEquals(List.of("ISO9141"), transport.get().getItems()); assertNull(transport.get().getValue());
                assertTrue(save.get().isDisabled());
                definition.get().setText(folder.resolve("missing.xml").toString()); controller.get().refresh();
                assertTrue(protocol.get().getItems().isEmpty());
            });
            await(() -> definition.get().getTooltip() != null);
            FxTestRuntime.run(() -> {
                assertTrue(save.get().isDisabled()); assertTrue(transport.get().getItems().isEmpty());
                definition.get().setText(path.toString()); controller.get().refresh(); controller.get().close();
            });
            Thread.sleep(400);
            FxTestRuntime.run(() -> assertTrue(protocol.get().getItems().isEmpty(), "Closed dialog must not accept a pending refresh"));
        } finally {
            FxTestRuntime.run(() -> { if (controller.get() != null) controller.get().close(); });
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            FxTestRuntime.run(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        fail("Setup choices did not finish loading");
    }

    private static String protocol(String id, String transports) {
        return "<protocol id='" + id + "'><transports>" + transports + "</transports></protocol>";
    }
    private static String transport(String id, String module) {
        return "<transport id='" + id + "'><module id='" + module + "'/></transport>";
    }
}
