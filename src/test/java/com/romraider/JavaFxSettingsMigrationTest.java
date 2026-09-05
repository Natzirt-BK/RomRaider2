package com.romraider;

import static org.junit.Assert.*;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import com.romraider.swing.JProgressPane;

public class JavaFxSettingsMigrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Settings load(String xml) throws Exception {
        return new DOMSettingsUnmarshaller().unmarshallSettings(DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement());
    }
    @Test public void legacyTableDimensionsDoNotShrinkJavaFxDefaults() throws Exception {
        Settings settings = load("<settings><tabledisplay><cellsize width='42' height='18'/></tabledisplay></settings>");
        assertEquals(new Dimension(42, 18), settings.getCellSize());
        assertEquals(new Dimension(124, 34), settings.getJavaFxCellSize());
    }
    @Test public void dimensionsAreBoundedAndDefensivelyCopied() throws Exception {
        Settings settings = load("<settings><tabledisplay><cellsize fx-width='-20' fx-height='9999'/></tabledisplay></settings>");
        assertEquals(new Dimension(60, 120), settings.getJavaFxCellSize());
        settings.getJavaFxCellSize().width = 999;
        assertEquals(60, settings.getJavaFxCellSize().width);
    }
    @Test public void newDimensionsSurviveSettingsXmlRoundTrip() throws Exception {
        Settings original = new Settings(); original.setJavaFxCellSize(new Dimension(180, 48));
        var file = temporary.newFile("settings.xml");
        new DOMSettingsBuilder().buildSettings(original, file, new JProgressPane(), "test");
        Settings loaded = load(java.nio.file.Files.readString(file.toPath()));
        assertEquals(new Dimension(180, 48), loaded.getJavaFxCellSize());
        assertEquals(original.getCellSize(), loaded.getCellSize());
    }
}
