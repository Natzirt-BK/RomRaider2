/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.editor.calibration;

import com.romraider.Settings;
import com.romraider.maps.*;
import com.romraider.maps.history.*;
import com.romraider.util.JEPUtil;
import com.romraider.util.SettingsManager;
import com.romraider.xml.RomAttributeParser;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleUnaryOperator;

/** Immutable byte-checked MAF correction proposal. Call from the editor's serialized UI command thread. */
public final class ReviewedMafTransfer {
    public record Row(int index, double volts, double original, double correctionPercent,
            double requested, double stored, double originalRaw, double storedRaw, boolean covered) {
        public boolean changes() { return covered && Double.compare(originalRaw, storedRaw) != 0; }
    }
    private final Table2D table;
    private final Rom rom;
    private final byte[] buffer, before, expected;
    private final List<Row> rows;
    private final List<DataCell> cells;
    private final List<Object> tableState, axisState;
    private final String source;
    private boolean attempted;

    private ReviewedMafTransfer(Table2D table, DoubleUnaryOperator correction, String source) {
        this.table = table; this.rom = table.getRom(); this.source = Objects.requireNonNull(source);
        requireTarget(table);
        if (rom.getBinary() == null || rom.getBinary().length > 32 * 1024 * 1024) throw invalid("ROM size is unavailable or exceeds the review limit.");
        buffer = rom.getBinary(); before = buffer.clone(); expected = before.clone();
        cells = List.copyOf(Arrays.asList(table.getData()));
        tableState = state(table); axisState = state(table.getAxis());
        List<Row> proposed = new ArrayList<>();
        Set<Integer> changedAddresses = new HashSet<>();
        int width = width(table);
        for (int i = 0; i < table.getDataSize(); i++) {
            DataCell cell = table.getDataCell(i);
            int address = DataCell.getMemoryStartAddress(cell);
            checkRange(address, width, before.length);
            if (Double.compare(cell.getBinValue(), decode(table, Arrays.copyOfRange(before, address, address + width))) != 0)
                throw invalid("Cached table values do not match ROM bytes; refresh the table before reviewing.");
            if (!Arrays.equals(Arrays.copyOfRange(before, address, address + width), encode(table, cell.getBinValue())))
                throw invalid("An original value cannot round-trip through the undo writer.");
            for (int byteIndex = address; byteIndex < address + width; byteIndex++) {
                if (!changedAddresses.add(byteIndex)) throw invalid("Target cells overlap in memory.");
            }
            double volts = axisValue(table.getAxis().getDataCell(i));
            double original = cell.getRealValue();
            if (!Double.isFinite(volts) || volts < 0 || !Double.isFinite(original) || original < 0) throw invalid("Axis or flow values are invalid.");
            double percent = correction.applyAsDouble(volts);
            if (Double.isNaN(percent)) {
                proposed.add(new Row(i, volts, original, Double.NaN, original, original, cell.getBinValue(), cell.getBinValue(), false));
                continue;
            }
            if (!Double.isFinite(percent)) throw invalid("The correction is infinite.");
            double multiplier = 1 + percent / 100, requested = original * multiplier;
            if (!Double.isFinite(multiplier) || multiplier <= 0 || !Double.isFinite(requested)) throw invalid("A covered correction would produce invalid flow.");
            Scale scale = table.getCurrentScale();
            double raw = "x".equalsIgnoreCase(scale.getExpression().trim()) ? requested
                    : JEPUtil.evaluate(scale.getByteExpression(), requested);
            if (!Double.isFinite(raw) || raw < cell.getMinAllowedBin() || raw > cell.getMaxAllowedBin()) throw invalid("A correction exceeds storage limits; clipping is not allowed.");
            boolean floating = table.getStorageType() == Settings.STORAGE_TYPE_FLOAT;
            double storedRaw = floating ? (double) (float) raw : Math.round(raw);
            byte[] encoded = encode(table, storedRaw);
            double roundTrip = decode(table, encoded);
            if (!Double.isFinite(storedRaw) || Double.compare(storedRaw, roundTrip) != 0
                    || storedRaw < cell.getMinAllowedBin() || storedRaw > cell.getMaxAllowedBin()) {
                throw invalid("A proposed value cannot round-trip through the current storage writer.");
            }
            double stored = JEPUtil.evaluate(scale.getExpression(), storedRaw);
            if (!Double.isFinite(stored) || stored < 0) throw invalid("A stored correction has invalid scaled flow.");
            System.arraycopy(encoded, 0, expected, address, width);
            proposed.add(new Row(i, volts, original, percent, requested, stored, cell.getBinValue(), storedRaw, true));
        }
        if (!table.getAxis().isStaticDataTable()) {
            for (DataCell cell : table.getAxis().getData()) {
                int address = DataCell.getMemoryStartAddress(cell), axisWidth = width(table.getAxis());
                checkRange(address, axisWidth, before.length);
                if (Double.compare(cell.getBinValue(), decode(table.getAxis(), Arrays.copyOfRange(before, address, address + axisWidth))) != 0)
                    throw invalid("Cached axis values do not match ROM bytes; refresh the table before reviewing.");
                for (int i = address; i < address + axisWidth; i++) if (changedAddresses.contains(i)) throw invalid("Flow values overlap the voltage axis.");
            }
        }
        rows = List.copyOf(proposed);
        if (rows.stream().noneMatch(Row::changes)) throw invalid("No covered, representable cell changes are available.");
        verifyCurrent(); // Also reject a callback that changed the target while evaluating the curve.
    }

    public static ReviewedMafTransfer preview(Table2D table, DoubleUnaryOperator correction, String source) {
        if (table == null || correction == null) throw invalid("Select a target table and a completed MAF curve.");
        return new ReviewedMafTransfer(table, correction, source);
    }
    public List<Row> getRows() { return rows; }
    public Table2D getTable() { return table; }
    public String getSource() { return source; }
    public long getChangedCount() { return rows.stream().filter(Row::changes).count(); }
    public long getUncoveredCount() { return rows.stream().filter(row -> !row.covered()).count(); }

    /** The caller additionally checks active document/table and analysis generations before accepting. */
    public void apply(BooleanSupplier contextStillCurrent) {
        if (attempted) throw invalid("This proposal has already been attempted; create a fresh review.");
        if (contextStillCurrent == null || !contextStillCurrent.getAsBoolean()) throw invalid("The editor or analysis context changed; review again.");
        verifyCurrent(); attempted = true;
        RomEditHistory history = RomEditHistory.getInstance();
        try (EditTransaction transaction = history.begin(table, "Apply reviewed MAF correction")) {
            // Pre-register intent so rollback can remove the whole batch, even if
            // a presentation callback fails before DataCell records its change.
            for (Row row : rows) if (row.changes()) history.recordChange(rom, cells.get(row.index()), row.originalRaw(), row.storedRaw());
            try {
                for (Row row : rows) if (row.changes()) cells.get(row.index()).setBinValue(row.storedRaw());
                if (!Arrays.equals(buffer, expected) || rom.getBinary() != buffer) throw invalid("Applied bytes did not match the reviewed proposal.");
            } catch (Exception failure) {
                System.arraycopy(before, 0, buffer, 0, before.length);
                for (Row row : rows) if (row.changes()) {
                    DataCell cell = cells.get(row.index());
                    history.recordChange(rom, cell, row.storedRaw(), row.originalRaw());
                    try { if (rom.getBinary() == buffer) cell.checkForDataUpdates(); }
                    catch (RuntimeException refreshFailure) { if (failure != refreshFailure) failure.addSuppressed(refreshFailure); }
                }
                throw new IllegalArgumentException(rom.getBinary() == buffer
                        ? "The reviewed edit failed; original ROM bytes were restored."
                        : "The ROM buffer was replaced during the edit. The captured buffer was restored; the replacement was not overwritten.", failure);
            }
        }
    }

    private void verifyCurrent() {
        requireTarget(table);
        if (table.getRom() != rom || rom.getBinary() != buffer || !Arrays.equals(buffer, before)
                || !tableState.equals(state(table)) || !axisState.equals(state(table.getAxis()))) {
            throw invalid("ROM bytes, table, axis or scaling changed after review. Create a fresh proposal.");
        }
    }
    private static void requireTarget(Table2D table) {
        if (table.getRom() == null || table.isLocked() || table.isStaticDataTable()
                || (table.getName() != null && table.getName().contains("Checksum Fix"))) throw invalid("Select an editable numeric MAF table in an open ROM.");
        int level = table.getUserLevel(); Settings settings = SettingsManager.getSettings();
        if (level > settings.getUserLevel() || (level >= 5 && !settings.isSaveDebugTables())) throw invalid("Current user-level settings do not permit this table edit.");
        if (table.getData() == null || table.getAxis() == null || table.getAxis().getData() == null)
            throw invalid("The target and axis must be populated.");
        if (table.getDataSize() < 1 || table.getDataSize() > 2048
                || table.getAxis().getDataSize() != table.getDataSize()) throw invalid("A matching one-dimensional voltage axis is required.");
        if (table.getDataLayout() != Table.DataLayout.DEFAULT || table.getBitMask() != 0) throw invalid("Masked or coupled-layout tables require a separate transfer implementation.");
        width(table);
        for (DataCell cell : table.getData()) if (cell == null || cell.getTable() != table || cell.getBitMask() != 0
                || cell.getBinary() != table.getRom().getBinary()) throw invalid("Missing, foreign or masked cells cannot use this transfer.");
        if (table.getAxis().getDataLayout() != Table.DataLayout.DEFAULT || table.getAxis().getBitMask() != 0)
            throw invalid("A plain voltage axis is required.");
        for (DataCell cell : table.getAxis().getData()) if (cell == null || cell.getTable() != table.getAxis() || cell.getBitMask() != 0
                || cell.getBinary() != table.getRom().getBinary()) throw invalid("Missing, foreign or masked axis cells cannot use this transfer.");
        Scale flow = table.getCurrentScale(), axis = table.getAxis().getCurrentScale();
        if (flow == null || axis == null || !Set.of("v", "volt", "volts").contains(unit(axis.getUnit()))
                || !Set.of("g/s", "g/sec", "g/sec.", "lb/min", "lbs/min").contains(unit(flow.getUnit()))) {
            throw invalid("Select voltage (V) and recognized mass-flow display units; units are not guessed or converted.");
        }
        if (flow.getExpression() == null || (!"x".equalsIgnoreCase(flow.getExpression().trim())
                && (flow.getByteExpression() == null || flow.getByteExpression().isBlank()))) throw invalid("An explicit inverse scaling expression is required for stored-value review.");
    }
    private static String unit(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static int width(Table table) {
        int storage = table.getStorageType();
        if (storage == Settings.STORAGE_TYPE_FLOAT) return 4;
        if (storage == 1 || storage == 2 || storage == 4) return storage;
        throw invalid("This transfer requires ordinary 1/2/4-byte integer or float storage.");
    }
    private static double axisValue(DataCell cell) {
        if (!cell.getTable().isStaticDataTable()) return cell.getRealValue();
        try { return Double.parseDouble(cell.getStaticText().trim()); }
        catch (RuntimeException failure) { throw invalid("Static voltage axis values must be finite numbers."); }
    }
    private static List<Object> state(Table table) {
        Scale scale = table.getCurrentScale();
        List<Object> state = new ArrayList<>(Arrays.asList(new Identity(table), new Identity(table.getRom()), table.getName(), table.getStorageType(),
                table.getStorageAddress(), table.getRamOffset(), table.getEndian(), table.getMemModelEndian(), table.isSignedData(),
                table.isLocked(), table.isStaticDataTable(), table.getDataLayout(), table.getBitMask(), table.getUserLevel(), table.getDataSize(),
                new Identity(scale), scale.getExpression(), scale.getByteExpression(), scale.getUnit()));
        for (DataCell cell : table.getData()) state.add(Arrays.asList(new Identity(cell), cell.getBitMask(), cell.getBinValue(), cell.getRealValue(), cell.getStaticText(),
                cell.getMinAllowedBin(), cell.getMaxAllowedBin()));
        return Collections.unmodifiableList(state);
    }
    private record Identity(Object value) {
        @Override public boolean equals(Object other) { return other instanceof Identity identity && value == identity.value; }
        @Override public int hashCode() { return System.identityHashCode(value); }
    }
    private static double decode(Table table, byte[] bytes) {
        return table.getStorageType() == Settings.STORAGE_TYPE_FLOAT
                ? RomAttributeParser.byteToFloat(bytes, table.getEndian(), table.getMemModelEndian())
                : RomAttributeParser.parseByteValue(bytes, table.getEndian(), 0, table.getStorageType(), table.isSignedData());
    }
    private static byte[] encode(Table table, double raw) {
        return table.getStorageType() == Settings.STORAGE_TYPE_FLOAT
                ? RomAttributeParser.floatToByte((float) raw, table.getEndian(), table.getMemModelEndian())
                : RomAttributeParser.parseIntegerValue((int) raw, table.getEndian(), table.getStorageType());
    }
    private static void checkRange(int address, int width, int length) {
        if (address < 0 || address > length - width) throw invalid("A target or axis address is outside the ROM.");
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
