/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu.comms.readcodes;

import com.romraider.logger.ecu.comms.io.connection.LoggerConnection;
import com.romraider.logger.ecu.comms.manager.PollingStateImpl;
import com.romraider.logger.ecu.comms.query.EcuQuery;
import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.definition.EcuSwitch;
import com.romraider.logger.ecu.definition.EcuSwitchImpl;
import com.romraider.logger.ecu.definition.EcuAddressImpl;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.exception.InvalidResponseException;
import com.romraider.util.HexUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One standard-code read. Keeps the existing initialization-length limits. */
final class DtcReadPlan {
    private final List<ReplyQuery> queries;
    private boolean attempted;

    private DtcReadPlan(List<ReplyQuery> queries) { this.queries = List.copyOf(queries); }

    static DtcReadPlan prepare(List<EcuSwitch> definitions, int initializationLength) {
        if (initializationLength <= 0)
            throw new IllegalArgumentException("DTC reading requires ECU identification");
        if (definitions == null || definitions.isEmpty())
            throw new IllegalArgumentException("The logger definition contains no DTC entries");
        int exclusiveLimit = initializationLength < 56 ? 256 : initializationLength < 104 ? 488 : Integer.MAX_VALUE;
        List<ReplyQuery> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (EcuSwitch definition : definitions) {
            if (definition == null || definition.getId() == null || definition.getId().isBlank())
                throw new IllegalArgumentException("The logger definition contains an unnamed DTC entry");
            String id = definition.getId();
            if (!ids.add(id)) throw new IllegalArgumentException("The logger definition contains duplicate DTC IDs");
            if (exclusiveLimit != Integer.MAX_VALUE) {
                // The legacy boundary IDs are indices, not list terminators.
                // Sparse/reordered lists must not expand the permitted range.
                if (!id.matches("D(0|[1-9][0-9]*)"))
                    throw new IllegalArgumentException("Limited DTC reading requires numeric D-prefixed definition IDs");
                final int index;
                try { index = Integer.parseInt(id.substring(1)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("DTC definition index is out of range", e); }
                if (index >= exclusiveLimit) continue;
            }
            selected.add(new ReplyQuery(definition));
        }
        if (selected.isEmpty())
            throw new IllegalArgumentException("The logger definition contains no DTC entries in this ECU's supported range");
        return new DtcReadPlan(selected);
    }

    ArrayList<EcuQuery> read(LoggerConnection connection, Module module, Runnable requireCurrent) throws InterruptedException {
        if (attempted) throw new IllegalStateException("A DTC read plan cannot be reused");
        attempted = true;
        int available = 0;
        ArrayList<EcuQuery> active = new ArrayList<>();
        for (int offset = 0; offset < queries.size(); offset += 150) {
            checkCurrent(requireCurrent);
            List<ReplyQuery> batch = queries.subList(offset, Math.min(offset + 150, queries.size()));
            connection.sendAddressReads(new ArrayList<EcuQuery>(batch), module, new PollingStateImpl());
            checkCurrent(requireCurrent);
            for (ReplyQuery query : batch) {
                if (!query.received) throw new InvalidResponseException("The ECU did not answer every DTC request");
                double value = query.getResponse();
                if (value == -1) continue; // Existing converter's unsupported-entry marker.
                if (value != 0 && value != 1 && value != 2 && value != 3)
                    throw new InvalidResponseException("The ECU returned an invalid DTC value");
                available++;
                if (value != 0) active.add(query);
            }
        }
        if (available == 0) throw new InvalidResponseException("The ECU returned no available standard DTC entries");
        return active;
    }

    private static void checkCurrent(Runnable requireCurrent) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("DTC read cancelled");
        requireCurrent.run();
    }

    private static final class ReplyQuery implements EcuQuery {
        private final EcuSwitch definition;
        private final EcuDataConvertor converter;
        private final String[] addresses;
        private final byte[] addressBytes;
        private boolean received;
        private double response = Double.NaN;

        ReplyQuery(EcuSwitch definition) {
            converter = definition.getSelectedConvertor();
            addresses = definition.getAddress().getAddresses().clone();
            addressBytes = definition.getAddress().getBytes().clone();
            if (addresses.length != 2 || converter == null)
                throw new IllegalArgumentException("A DTC entry requires current and memorized byte addresses and a converter");
            // Some protocol optimizers mutate EcuData addresses. Keep those
            // mutations away from the live catalog and the frozen request.
            this.definition = new EcuSwitchImpl(definition.getId(), definition.getName(),
                    definition.getDescription(), new EcuAddressImpl(addresses.clone()),
                    definition.getGroup(), definition.getSubgroup(), Integer.toString(definition.getGroupSize()),
                    new EcuDataConvertor[] {converter});
        }

        public LoggerData getLoggerData() { return definition; }
        public String[] getAddresses() { return addresses.clone(); }
        public byte[] getBytes() { return addressBytes.clone(); }
        public String getHex() { return HexUtil.asHex(addressBytes); }
        public boolean equals(Object other) { return other instanceof ReplyQuery query && getHex().equals(query.getHex()); }
        public int hashCode() { return getHex().hashCode(); }
        public double getResponse() { return response; }
        public void setResponse(byte[] bytes) {
            if (received || bytes == null || bytes.length != 2)
                throw new InvalidResponseException("A DTC request requires exactly one two-byte reply");
            response = converter.convert(bytes.clone());
            received = true;
        }
    }
}
