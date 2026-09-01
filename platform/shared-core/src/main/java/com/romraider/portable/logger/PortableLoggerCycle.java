/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Executes one complete, read-only query plan against a portable data source. */
public final class PortableLoggerCycle {
    private final PortableLoggerQueryPlan plan;

    public PortableLoggerCycle(PortableLoggerQueryPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Logger query plan is required");
        }
        this.plan = plan;
    }

    public List<PortableLoggerValue> read(PortableLoggerDataSource source)
            throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Logger data source is required");
        }
        List<byte[]> values = new ArrayList<byte[]>();
        for (PortableLoggerQueryBatch batch : plan.batches()) {
            byte[] response = source.read(batch);
            int expected = batch.getAddresses().length;
            if (response == null || response.length != expected) {
                throw new IOException("Logger source returned "
                        + (response == null ? 0 : response.length)
                        + " values; expected " + expected);
            }
            values.add(response);
        }
        return plan.decode(values);
    }
}
