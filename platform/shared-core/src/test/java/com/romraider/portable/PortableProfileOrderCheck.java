/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;
import com.romraider.portable.logger.definition.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class PortableProfileOrderCheck {
    public static void main(String[] args) throws Exception {
        PortableLoggerProfile ordered = read("<parameters><parameter id='P1' livedata='selected' rr2-order='2'/>"
                + "<parameter id='P2' livedata='selected' rr2-order='0'/></parameters>"
                + "<switches><switch id='S1' dash='selected' rr2-order='1'/></switches>");
        require(ids(ordered).equals(Arrays.asList("P2", "S1", "P1")), "Portable reader lost cross-category order");
        PortableLoggerProfile legacy = read("<switches><switch id='S1' dash='selected'/></switches>"
                + "<parameters><parameter id='P1' graph='selected'/></parameters>");
        require(ids(legacy).equals(Arrays.asList("S1", "P1")), "Legacy encounter order changed");
        for (String invalid : new String[] {
                "<parameter id='P1' livedata='selected' rr2-order='1'/>",
                "<parameter id='P1' livedata='selected' rr2-order='0'/><parameter id='P2' livedata='selected'/>",
                "<parameter id='P1' livedata='selected' rr2-order='0'/><parameter id='P2' livedata='selected' rr2-order='0'/>",
                "<parameter id='P1' rr2-order='0'/>",
                "<parameter id='P1' livedata='selected' rr2-order='-1'/>",
                "<parameter id='P1' livedata='selected' rr2-order='0'/><parameter id='P1' livedata='selected' rr2-order='1'/>"}) {
            try { read("<parameters>" + invalid + "</parameters>"); throw new AssertionError("Accepted invalid portable profile order"); }
            catch (IOException expected) { }
        }
        PortableLoggerProfile external = read("<externals><external id='E1' livedata='selected' rr2-order='0'/></externals>"
                + "<parameters><parameter id='P1' livedata='selected' rr2-order='1'/></parameters>");
        require(ids(external).equals(Collections.singletonList("P1")) && external.unsupported().size() == 1,
                "External rank was silently dropped from validation");
        StringBuilder tooMany = new StringBuilder("<externals><external id='E1' livedata='selected'/></externals><parameters>");
        for (int i = 0; i < 256; i++) tooMany.append("<parameter id='P").append(i).append("' livedata='selected'/>");
        tooMany.append("</parameters>");
        try { read(tooMany.toString()); throw new AssertionError("Combined selection bound depended on category order"); }
        catch (IOException expected) { }
        System.out.println("Portable profile-order checks passed (10 cases)");
    }
    private static List<String> ids(PortableLoggerProfile profile) {
        List<String> result = new ArrayList<>(); for (var choice : profile.selections()) result.add(choice.getId()); return result;
    }
    private static PortableLoggerProfile read(String contents) throws IOException {
        return PortableLoggerProfileReader.read(new ByteArrayInputStream(("<profile protocol='SSM'>" + contents + "</profile>").getBytes(StandardCharsets.UTF_8)));
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
