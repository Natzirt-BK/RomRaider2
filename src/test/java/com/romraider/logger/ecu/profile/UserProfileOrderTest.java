/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.profile;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.romraider.logger.ecu.profile.xml.UserProfileHandler;
import com.romraider.util.SaxParserFactory;
import org.junit.Test;

public class UserProfileOrderTest {
    @Test public void crossCategoryOrderSurvivesProfileSerialization() throws Exception {
        Map<String, UserProfileItem> parameters = new LinkedHashMap<>();
        parameters.put("P1", selected("V")); parameters.put("P2", selected("rpm"));
        List<String> order = new ArrayList<>(List.of("P2", "S1", "P1"));
        UserProfile profile = new UserProfileImpl(parameters, Map.of("S1", selected("On/off")), Map.of(), "SSM", order);
        order.clear();
        assertEquals(List.of("P2", "S1", "P1"), profile.getSelectedIds());
        assertEquals(profile.getSelectedIds(), parse(profile.getBytes()).getSelectedIds());
        try { profile.getSelectedIds().clear(); fail("Mutable order"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void legacyOrderUsesDocumentEncounterOrder() throws Exception {
        UserProfile profile = parse(("<profile><switches><switch id='S1' dash='selected'/></switches>"
                + "<parameters><parameter id='P2' livedata='selected'/><parameter id='P1' graph='selected'/>"
                + "<parameter id='P0'/></parameters></profile>").getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("S1", "P2", "P1"), profile.getSelectedIds());
        assertEquals(profile.getSelectedIds(), parse(profile.getBytes()).getSelectedIds());
    }
    @Test public void incompleteDuplicateAndUnselectedRanksReject() throws Exception {
        for (String entries : new String[] {
                "<parameter id='P1' livedata='selected' rr2-order='1'/>",
                "<parameter id='P1' livedata='selected' rr2-order='0'/><parameter id='P2' livedata='selected'/>",
                "<parameter id='P1' livedata='selected' rr2-order='0'/><parameter id='P2' livedata='selected' rr2-order='0'/>",
                "<parameter id='P1' rr2-order='0'/>",
                "<parameter id='P1' livedata='selected' rr2-order='-1'/>",
                "<parameter id='P1'/><parameter id='P1'/>"}) {
            try { parse(("<profile><parameters>" + entries + "</parameters></profile>").getBytes(StandardCharsets.UTF_8)); fail("Accepted invalid order"); }
            catch (IllegalArgumentException | org.xml.sax.SAXException expected) { }
        }
    }
    private static UserProfileItem selected(String units) { return new UserProfileItemImpl(units, true, false, false); }
    private static UserProfile parse(byte[] bytes) throws Exception {
        UserProfileHandler handler = new UserProfileHandler();
        SaxParserFactory.getSaxParser().parse(new ByteArrayInputStream(bytes), handler);
        return handler.getUserProfile();
    }
}
