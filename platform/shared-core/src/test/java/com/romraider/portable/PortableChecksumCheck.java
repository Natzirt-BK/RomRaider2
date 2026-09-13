/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable;

import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomChecksum;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class PortableChecksumCheck {
    private static final String XML = "<roms><rom><romid><xmlid>BASE</xmlid><make>Subaru</make></romid>"
            + "<table name='Checksum Fix' type='Switch' sizey='24'/>"
            + "<table name='Checksum Fix 2' type='Switch' sizey='12'/>"
            + "<table name='Value' type='2D' sizey='1' storagetype='uint8' storageaddress='40'>"
            + "<scaling expression='x' to_byte='x'/></table></rom>"
            + "<rom base='BASE'><romid><xmlid>TEST</xmlid><internalidaddress>0</internalidaddress>"
            + "<internalidstring>TEST</internalidstring><filesize>256</filesize></romid>"
            + "<table name='Checksum Fix' storageaddress='80'/></rom></roms>";

    public static void main(String[] args) throws Exception {
        byte[] bytes = fixture();
        byte[] original = bytes.clone();
        PortableRomChecksum checksum = read(XML, bytes);
        require(checksum.validate(bytes) == PortableRomChecksum.Status.NEEDS_CORRECTION, "initial checksum");
        byte[] fixed = checksum.prepareCopy(bytes);
        require(Arrays.equals(bytes, original), "source modified");
        require(checksum.validate(fixed) == PortableRomChecksum.Status.VALID, "corrected checksum");
        require(ByteBuffer.wrap(fixed).getInt(136) == 0x5AA5A55B, "32-bit wrap");
        require(Arrays.equals(fixed, checksum.prepareCopy(fixed)), "idempotence");
        for (int i = 0; i < bytes.length; i++) {
            if ((i < 136 || i > 139) && (i < 148 || i > 151))
                require(bytes[i] == fixed[i], "non-checksum byte changed");
        }
        fixed[64]++;
        require(checksum.validate(fixed) == PortableRomChecksum.Status.NEEDS_CORRECTION, "edit detection");
        fixed[131] = 68;
        require(checksum.validate(fixed) == PortableRomChecksum.Status.INVALID, "edited range headers");
        try { checksum.prepareCopy(fixed); throw new AssertionError("invalid save allowed"); }
        catch (IllegalArgumentException expected) { }

        for (int[] range : new int[][] {{-4, 4}, {64, 260}, {68, 64}, {65, 68}, {128, 140}}) {
            byte[] malformed = fixture();
            ByteBuffer.wrap(malformed).putInt(128, range[0]).putInt(132, range[1]);
            require(read(XML, malformed).validate(malformed) == PortableRomChecksum.Status.INVALID, "bad range accepted");
        }
        byte[] unalignedEnd = fixture();
        ByteBuffer.wrap(unalignedEnd).putInt(132, 71);
        PortableRomChecksum unalignedPlan = read(XML, unalignedEnd);
        require(unalignedPlan.validate(unalignedPlan.prepareCopy(unalignedEnd))
                == PortableRomChecksum.Status.VALID, "stock unaligned end marker rejected");
        byte[] shortRom = Arrays.copyOf(fixture(), 255);
        ByteBuffer.wrap(shortRom).putInt(128, 252).putInt(132, 255);
        require(read(XML.replace("<filesize>256", "<filesize>255"), shortRom).validate(shortRom)
                == PortableRomChecksum.Status.INVALID, "partial final word read beyond ROM");
        for (String address : new String[] {"ff", "100", "7f"}) {
            require(read(XML.replace("storageaddress='80'", "storageaddress='" + address + "'"), bytes)
                    .validate(bytes) == PortableRomChecksum.Status.INVALID, "bad table accepted");
        }
        byte[] disabled = fixture();
        ByteBuffer.wrap(disabled).putInt(128, 0).putInt(132, 0).putInt(136, 0x5AA5A55A);
        PortableRomChecksum disabledPlan = read(XML, disabled);
        require(disabledPlan.validate(disabled) == PortableRomChecksum.Status.DISABLED, "disabled detection");
        require(Arrays.equals(disabled, disabledPlan.prepareCopy(disabled)), "disabled modified");
        for (String unsupported : new String[] {
                XML.replace("Subaru", "Other"), XML.replace("<rom base='BASE'>", "<rom base='BASE'><checksum type='custom'/>"),
                XML.replace("storageaddress='80'", ""), XML.replace("sizey='24'", "sizey='bad'"),
                XML.replace("<rom><romid>", "<rom base='MISSING'><romid>")}) {
            PortableRomChecksum plan = read(unsupported, bytes);
            require(plan.validate(bytes) == PortableRomChecksum.Status.UNSUPPORTED, "unsupported scheme accepted");
            require(Arrays.equals(bytes, plan.prepareCopy(bytes)), "unsupported modified");
        }
        String multiple = XML.replace("<table name='Checksum Fix' storageaddress='80'/>",
                "<table name='Checksum Fix' storageaddress='80'/><table name='Checksum Fix 2' storageaddress='a0'/>");
        byte[] multiBytes = fixture();
        ByteBuffer.wrap(multiBytes).putInt(160, 64).putInt(164, 72);
        PortableRomChecksum multi = read(multiple, multiBytes);
        require(multi.validate(multi.prepareCopy(multiBytes)) == PortableRomChecksum.Status.VALID, "multiple blocks");
        require(read(multiple.replace("storageaddress='a0'", "storageaddress='8c'"), multiBytes)
                .validate(multiBytes) == PortableRomChecksum.Status.INVALID, "overlapping blocks");

        PortableRomDocument document = new PortableRomDocument("test.bin", bytes);
        byte[] corrected = checksum.prepareCopy(bytes);
        require(document.acceptSavedCopy(bytes, corrected), "save acceptance");
        require(!document.hasChanges() && Arrays.equals(corrected, document.snapshot()), "saved correction state");
        document.replace(64, new byte[] {9});
        require(!document.acceptSavedCopy(bytes, corrected) && document.hasChanges(), "concurrent edit lost");
        System.out.println("PASS: definition-backed Subaru checksum correction, boundaries, disabled/unsupported preservation and save state");
    }

    private static byte[] fixture() {
        byte[] bytes = new byte[256];
        System.arraycopy("TEST".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        ByteBuffer.wrap(bytes).putInt(64, 0x7fffffff).putInt(68, 0x80000000)
                .putInt(128, 64).putInt(132, 72).putInt(140, 72).putInt(144, 80);
        return bytes;
    }

    private static PortableRomChecksum read(String xml, byte[] bytes) throws Exception {
        return PortableEcuDefinitionReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                new PortableRomDocument("test.bin", bytes)).getChecksum();
    }

    private static void require(boolean test, String message) {
        if (!test) throw new AssertionError(message);
    }
}
