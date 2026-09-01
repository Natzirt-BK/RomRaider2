/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact ECU-definition match and the portable tables it exposes. */
public final class PortableEcuDefinition {
    private final String xmlId;
    private final String make;
    private final String model;
    private final String submodel;
    private final List<PortableRomTable> tables;

    PortableEcuDefinition(String xmlId, String make, String model,
            String submodel, List<PortableRomTable> tables) {
        this.xmlId = xmlId;
        this.make = make;
        this.model = model;
        this.submodel = submodel;
        this.tables = Collections.unmodifiableList(
                new ArrayList<PortableRomTable>(tables));
    }

    public String getXmlId() { return xmlId; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getSubmodel() { return submodel; }
    public List<PortableRomTable> getTables() { return tables; }

    public String vehicleName() {
        StringBuilder result = new StringBuilder();
        append(result, make);
        append(result, model);
        append(result, submodel);
        return result.length() == 0 ? "Unknown vehicle" : result.toString();
    }

    private static void append(StringBuilder target, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append(' ');
        target.append(value.trim());
    }
}
