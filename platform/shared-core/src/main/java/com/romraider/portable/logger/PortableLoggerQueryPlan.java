/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.romraider.portable.logger.definition.PortableSelectedParameter;

/** Deduplicated, bounded query plan and response decoder for selected parameters. */
public final class PortableLoggerQueryPlan {
    private static final int MAX_ADDRESSES = 64;

    private final List<PortableSelectedParameter> selections;
    private final List<PortableParameterConverter> converters;
    private final List<PortableLoggerQueryBatch> batches;

    private PortableLoggerQueryPlan(List<PortableSelectedParameter> selections,
            List<PortableParameterConverter> converters,
            List<PortableLoggerQueryBatch> batches) {
        this.selections = Collections.unmodifiableList(
                new ArrayList<PortableSelectedParameter>(selections));
        this.converters = Collections.unmodifiableList(
                new ArrayList<PortableParameterConverter>(converters));
        this.batches = Collections.unmodifiableList(
                new ArrayList<PortableLoggerQueryBatch>(batches));
    }

    public static PortableLoggerQueryPlan create(
            List<PortableSelectedParameter> selections) {
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("At least one logger parameter is required");
        }
        List<Group> groups = connectedGroups(selections);
        List<LinkedHashSet<Integer>> packed = new ArrayList<>();
        for (Group group : groups) {
            if (group.addresses.size() > MAX_ADDRESSES) {
                throw new IllegalArgumentException(
                        "Overlapping logger parameters exceed one SSM request");
            }
            LinkedHashSet<Integer> destination = null;
            for (LinkedHashSet<Integer> candidate : packed) {
                if (candidate.size() + group.addresses.size() <= MAX_ADDRESSES) {
                    destination = candidate;
                    break;
                }
            }
            if (destination == null) {
                destination = new LinkedHashSet<>();
                packed.add(destination);
            }
            destination.addAll(group.addresses);
        }
        List<PortableLoggerQueryBatch> batches = new ArrayList<>();
        for (LinkedHashSet<Integer> addresses : packed) {
            batches.add(new PortableLoggerQueryBatch(toArray(addresses)));
        }
        List<PortableParameterConverter> converters = new ArrayList<>();
        for (PortableSelectedParameter selection : selections) {
            converters.add(new PortableParameterConverter(
                    selection.getConversion()));
        }
        return new PortableLoggerQueryPlan(selections, converters, batches);
    }

    public List<PortableLoggerQueryBatch> batches() {
        return batches;
    }

    public List<PortableLoggerValue> decode(List<byte[]> batchValues) {
        if (batchValues == null || batchValues.size() != batches.size()) {
            throw new IllegalArgumentException(
                    "Logger response count does not match query plan");
        }
        Map<Integer, Byte> valuesByAddress = new LinkedHashMap<>();
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            int[] addresses = batches.get(batchIndex).getAddresses();
            byte[] values = batchValues.get(batchIndex);
            if (values == null || values.length != addresses.length) {
                throw new IllegalArgumentException(
                        "Logger response length does not match query batch");
            }
            for (int index = 0; index < addresses.length; index++) {
                valuesByAddress.put(addresses[index], values[index]);
            }
        }
        List<PortableLoggerValue> result = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            PortableSelectedParameter selection = selections.get(index);
            int[] addresses = selection.getAddresses();
            byte[] raw = new byte[addresses.length];
            for (int valueIndex = 0; valueIndex < addresses.length; valueIndex++) {
                Byte value = valuesByAddress.get(addresses[valueIndex]);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Logger response omitted a selected address");
                }
                raw[valueIndex] = value;
            }
            result.add(new PortableLoggerValue(selection,
                    converters.get(index).convert(raw)));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Group> connectedGroups(
            List<PortableSelectedParameter> selections) {
        List<Group> groups = new ArrayList<>();
        for (PortableSelectedParameter selection : selections) {
            Group merged = new Group(selection.getAddresses());
            for (int index = groups.size() - 1; index >= 0; index--) {
                Group existing = groups.get(index);
                if (intersects(merged.addresses, existing.addresses)) {
                    merged.addresses.addAll(existing.addresses);
                    groups.remove(index);
                }
            }
            groups.add(merged);
        }
        return groups;
    }

    private static boolean intersects(Set<Integer> left, Set<Integer> right) {
        Set<Integer> smaller = left.size() <= right.size() ? left : right;
        Set<Integer> larger = smaller == left ? right : left;
        for (Integer value : smaller) if (larger.contains(value)) return true;
        return false;
    }

    private static int[] toArray(Set<Integer> values) {
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values) result[index++] = value;
        return result;
    }

    private static final class Group {
        private final LinkedHashSet<Integer> addresses = new LinkedHashSet<>();

        private Group(int[] source) {
            for (int address : source) addresses.add(address);
        }
    }
}
