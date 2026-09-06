/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.profile;

import static org.junit.Assert.*;
import com.romraider.logger.ecu.profile.xml.UserProfileHandler;
import com.romraider.logger.ecu.ui.swing.menubar.util.FileHelper;
import com.romraider.util.SaxParserFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class UserProfilePersistenceTest {
    @Test public void unicodeAndXmlAttributesRoundTripWithTheCapturedProtocol() throws Exception {
        String id = "P<&\"'𝄞";
        String units = "λ ≤ µs & <rich> \"quoted\" 'single'\t\n\r";
        UserProfile source = profile("MUT2", id, units);
        byte[] bytes = source.getBytes();
        assertTrue(new String(bytes, StandardCharsets.UTF_8).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        UserProfile roundTrip = parse(bytes);
        assertEquals("MUT2", roundTrip.getProtocol());
        assertArrayEquals(bytes, roundTrip.getBytes());
        List<Map<String, String>> elements = elements(bytes);
        assertEquals("MUT2", elements.get(0).get("protocol"));
        assertEquals(id, elements.get(2).get("id"));
        assertEquals(units, elements.get(2).get("units"));
    }

    @Test public void snapshotsDoNotBorrowMutableMapsOrItems() throws Exception {
        String[] units = {"°C"};
        boolean[] selected = {true};
        UserProfileItem mutable = new UserProfileItem() {
            public String getUnits() { return units[0]; }
            public boolean isLiveDataSelected() { return selected[0]; }
            public boolean isGraphSelected() { return false; }
            public boolean isDashSelected() { return false; }
        };
        Map<String, UserProfileItem> parameters = new LinkedHashMap<>();
        parameters.put("P9", mutable); parameters.put("P1", item("V", false));
        UserProfile snapshot = new UserProfileImpl(parameters, Collections.emptyMap(), Collections.emptyMap(), "SSM");
        byte[] original = snapshot.getBytes();
        parameters.clear(); units[0] = "wrong"; selected[0] = false;
        assertArrayEquals(original, snapshot.getBytes());
        assertArrayEquals(original, parse(original).getBytes());
        List<Map<String, String>> elements = elements(original);
        assertEquals("P9", elements.get(2).get("id"));
        assertEquals("P1", elements.get(3).get("id"));
        assertEquals("selected", elements.get(2).get("livedata"));
        assertFalse(elements.get(3).containsKey("livedata"));
    }

    @Test public void switchAndExternalUnitsAndIndependentTabFlagsSurvive() throws Exception {
        UserProfile source = new UserProfileImpl(Collections.emptyMap(),
                Collections.singletonMap("S1", new UserProfileItemImpl("On & off", false, true, false)),
                Collections.singletonMap("E1", new UserProfileItemImpl("λ", false, false, true)), "SSM");
        assertArrayEquals(source.getBytes(), parse(source.getBytes()).getBytes());
        List<Map<String, String>> elements = elements(source.getBytes());
        Map<String, String> toggle = elements.stream().filter(e -> "S1".equals(e.get("id"))).findFirst().get();
        assertEquals("On & off", toggle.get("units")); assertEquals("selected", toggle.get("graph"));
        assertFalse(toggle.containsKey("livedata")); assertFalse(toggle.containsKey("dash"));
    }

    @Test public void legacyLatin1RemainsReadableAndMissingProtocolIsNotGuessed() throws Exception {
        byte[] legacy = ("<?xml version='1.0' encoding='ISO-8859-1'?><profile protocol='SSM'><parameters>"
                + "<parameter id='P1' livedata='selected' units='°C'/></parameters></profile>")
                .getBytes(StandardCharsets.ISO_8859_1);
        UserProfile parsed = parse(legacy);
        assertEquals("°C", elements(parsed.getBytes()).get(2).get("units"));
        UserProfile unspecified = parse("<profile/>".getBytes(StandardCharsets.UTF_8));
        assertEquals("", unspecified.getProtocol());
        assertEquals("", elements(unspecified.getBytes()).get(0).get("protocol"));
    }

    @Test public void invalidXmlTextIsRejectedBeforeAnyDestinationChange() throws Exception {
        Path directory = Files.createTempDirectory("rr2-profile-invalid-");
        Path target = directory.resolve("saved.xml"); byte[] previous = "previous".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(target, previous);
            for (String invalid : new String[] {"\u0000", "\u0001", "\ud800", "\udc00", "\ufffe"}) {
                for (UserProfile source : new UserProfile[] {profile(invalid, "P1", "V"),
                        profile("SSM", "P" + invalid, "V"), profile("SSM", "P1", invalid)}) {
                    try { UserProfileWriter.save(source, target); fail("Accepted unrepresentable XML text"); }
                    catch (IllegalArgumentException expected) { }
                    assertArrayEquals(previous, Files.readAllBytes(target));
                }
            }
            try (java.util.stream.Stream<Path> entries = Files.list(directory)) { assertEquals(1, entries.count()); }
        } finally { Files.deleteIfExists(target); Files.delete(directory); }
    }

    @Test public void atomicReplacementFailurePreservesOriginalAndCleansTemporaryFile() throws Exception {
        Path directory = Files.createTempDirectory("rr2-profile-write-");
        Path target = directory.resolve("saved.xml"); byte[] previous = "previous".getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(target, previous);
            UserProfile source = profile("SSM", "P1", "λ");
            try {
                UserProfileWriter.save(source, target, (temporary, destination) -> {
                    assertArrayEquals(source.getBytes(), Files.readAllBytes(temporary));
                    assertArrayEquals(previous, Files.readAllBytes(destination));
                    throw new AtomicMoveNotSupportedException(temporary.toString(), destination.toString(), "synthetic");
                });
                fail("Non-atomic replacement was accepted");
            } catch (AtomicMoveNotSupportedException expected) { }
            assertArrayEquals(previous, Files.readAllBytes(target));
            try (java.util.stream.Stream<Path> entries = Files.list(directory)) { assertEquals(1, entries.count()); }
            UserProfileWriter.save(source, target);
            assertArrayEquals(source.getBytes(), Files.readAllBytes(target));
            assertEquals("SSM", new UserProfileLoaderImpl().loadProfile(target.toString()).getProtocol());
        } finally { Files.deleteIfExists(target); Files.delete(directory); }
    }

    @Test public void profileLoadingNeverRewritesTheSourceIncludingLegacyDtdReferences() throws Exception {
        Path directory = Files.createTempDirectory("rr2-profile-read-"); Path target = directory.resolve("profile.dtd.xml");
        try {
            byte[] source = ("<!DOCTYPE profile SYSTEM 'file:///nonexistent/profile.dtd'>"
                    + "<profile protocol='MUT2'><parameters><parameter id='P9' livedata='selected' units='V'/>"
                    + "</parameters></profile>").getBytes(StandardCharsets.UTF_8);
            Files.write(target, source);
            assertEquals("MUT2", new UserProfileLoaderImpl().loadProfile(target.toString()).getProtocol());
            assertArrayEquals(source, Files.readAllBytes(target));
            Files.write(target, new byte[] {1, 2, 3});
            assertNull(new UserProfileLoaderImpl().loadProfile(target.toString()));
            assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(target));
        } finally { Files.deleteIfExists(target); Files.delete(directory); }
    }

    @Test public void saveHelperPreservesUppercaseExtensionAndRejectsNonRegularTargets() throws Exception {
        Path directory = Files.createTempDirectory("rr2-profile-target-"); Path target = directory.resolve("saved.XML");
        Path child = directory.resolve("folder.xml"); Path link = directory.resolve("link.xml");
        try {
            UserProfile source = profile("SSM", "P1", "V");
            assertEquals(target.toString(), FileHelper.saveProfileToFile(source, target.toFile()));
            assertFalse(Files.exists(directory.resolve("saved.XML.xml")));
            Files.createDirectory(child);
            try { UserProfileWriter.save(source, child); fail("Directory target accepted"); } catch (IOException expected) { }
            try { UserProfileWriter.save(source, directory.resolve("log.csv")); fail("CSV target accepted"); } catch (IOException expected) { }
            if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
                Files.createSymbolicLink(link, target);
                try { UserProfileWriter.save(source, link); fail("Symbolic link accepted"); } catch (IOException expected) { }
                assertTrue(Files.isSymbolicLink(link));
            }
            assertArrayEquals(source.getBytes(), Files.readAllBytes(target));
        } finally { Files.deleteIfExists(link); Files.deleteIfExists(child); Files.deleteIfExists(target); Files.delete(directory); }
    }

    @Test public void aDefinitionOrNestedProfileCannotMasqueradeAsAnEmptyProfile() throws Exception {
        for (String xml : new String[] {"<logger><parameters/></logger>", "<profiles><profile/></profiles>",
                "<profile><profile/></profile>"}) {
            try { parse(xml.getBytes(StandardCharsets.UTF_8)); fail("Accepted a non-profile document"); }
            catch (org.xml.sax.SAXException expected) { }
        }
    }

    private static UserProfile profile(String protocol, String id, String units) {
        return new UserProfileImpl(Collections.singletonMap(id, item(units, true)),
                Collections.emptyMap(), Collections.emptyMap(), protocol);
    }
    private static UserProfileItem item(String units, boolean selected) { return new UserProfileItemImpl(units, selected, false, false); }
    private static UserProfile parse(byte[] bytes) throws Exception {
        UserProfileHandler handler = new UserProfileHandler();
        SaxParserFactory.getSaxParser().parse(new ByteArrayInputStream(bytes), handler);
        return handler.getUserProfile();
    }
    private static List<Map<String, String>> elements(byte[] bytes) throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        SaxParserFactory.getSaxParser().parse(new ByteArrayInputStream(bytes), new DefaultHandler() {
            @Override public void startElement(String uri, String local, String name, Attributes attributes) {
                Map<String, String> element = new LinkedHashMap<>();
                for (int i = 0; i < attributes.getLength(); i++) element.put(attributes.getQName(i), attributes.getValue(i));
                result.add(element);
            }
        });
        return result;
    }
}
