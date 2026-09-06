/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.portable.logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;

import com.romraider.portable.logger.definition.PortableSelectedParameter;

/** Deduplicated, bounded query plan and response decoder for selected parameters. */
public final class PortableLoggerQueryPlan {
    private static final int MAX_ADDRESSES = 64;

    private final List<PortableSelectedParameter> selections;
    private final List<PortableSelectedParameter> evaluations;
    private final List<PortableSelectedParameter> readParameters;
    private final List<PortableParameterConverter> converters;
    private final List<PortableLoggerQueryBatch> batches;

    private PortableLoggerQueryPlan(List<PortableSelectedParameter> selections,
            List<PortableSelectedParameter> evaluations, List<PortableSelectedParameter> readParameters,
            List<PortableParameterConverter> converters,
            List<PortableLoggerQueryBatch> batches) {
        this.selections = Collections.unmodifiableList(
                new ArrayList<PortableSelectedParameter>(selections));
        this.evaluations = Collections.unmodifiableList(new ArrayList<>(evaluations));
        this.readParameters = Collections.unmodifiableList(new ArrayList<>(readParameters));
        this.converters = Collections.unmodifiableList(
                new ArrayList<PortableParameterConverter>(converters));
        this.batches = Collections.unmodifiableList(
                new ArrayList<PortableLoggerQueryBatch>(batches));
    }

    public static PortableLoggerQueryPlan create(
            List<PortableSelectedParameter> selections) {
        return create(selections, PortableLoggerProtocol.SSM);
    }

    public static PortableLoggerQueryPlan create(List<PortableSelectedParameter> selections,
            PortableLoggerProtocol protocol) {
        if (protocol == null) throw new IllegalArgumentException("Logger protocol is required");
        if (selections == null || selections.isEmpty()) {
            throw new IllegalArgumentException("At least one logger parameter is required");
        }
        Set<PortableSelectedParameter> nodes = new LinkedHashSet<>();
        for (PortableSelectedParameter selection : selections) collect(selection, nodes, new LinkedHashSet<>(), 0);
        List<PortableSelectedParameter> evaluations = new ArrayList<>(nodes), leaves = new ArrayList<>();
        for (PortableSelectedParameter node : evaluations) if (!node.isCalculated()) leaves.add(node);
        if (leaves.isEmpty()) throw new IllegalArgumentException("The logger plan contains no concrete reads");
        List<Group> groups = connectedGroups(leaves);
        int maxAddresses = protocol == PortableLoggerProtocol.MUT2 ? 256 : MAX_ADDRESSES;
        List<LinkedHashSet<Integer>> packed = new ArrayList<>();
        for (Group group : groups) {
            if (group.addresses.size() > maxAddresses) {
                throw new IllegalArgumentException(
                        "Overlapping logger parameters exceed the " + protocol + " address limit");
            }
            LinkedHashSet<Integer> destination = null;
            for (LinkedHashSet<Integer> candidate : packed) {
                if (candidate.size() + group.addresses.size() <= maxAddresses) {
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
            if (protocol == PortableLoggerProtocol.MUT2) {
                for (int pid : addresses) {
                    ReadOnlyMut2Protocol.request(pid);
                    batches.add(new PortableLoggerQueryBatch(new int[] {pid}, protocol));
                }
            } else batches.add(new PortableLoggerQueryBatch(toArray(addresses), protocol));
        }
        List<PortableParameterConverter> converters = new ArrayList<>();
        for (PortableSelectedParameter selection : evaluations) {
            converters.add(selection.isCalculated() ? null : new PortableParameterConverter(selection.getConversion()));
        }
        return new PortableLoggerQueryPlan(selections, evaluations, leaves, converters, batches);
    }

    public List<PortableLoggerQueryBatch> batches() {
        return batches;
    }

    /** Concrete inputs, including hidden dependencies, for simulation/inspection. */
    public List<PortableSelectedParameter> readParameters() { return readParameters; }

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
        // Fresh cycle-local values only. Nothing survives a failed or missing cycle.
        Map<PortableSelectedParameter, Double> evaluated = new IdentityHashMap<>();
        for (int index = 0; index < evaluations.size(); index++) {
            PortableSelectedParameter selection = evaluations.get(index);
            if (selection.isCalculated()) {
                Map<String, Double> inputs = new LinkedHashMap<>();
                for (Map.Entry<String, PortableSelectedParameter> entry : selection.getInputs().entrySet()) {
                    inputs.put(entry.getKey(), evaluated.get(entry.getValue()));
                }
                evaluated.put(selection, selection.getCalculation().evaluate(inputs));
                continue;
            }
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
            evaluated.put(selection, converters.get(index).convert(raw));
        }
        List<PortableLoggerValue> result = new ArrayList<>();
        for (PortableSelectedParameter selection : selections) result.add(new PortableLoggerValue(selection, evaluated.get(selection)));
        return Collections.unmodifiableList(result);
    }

    private static void collect(PortableSelectedParameter node, Set<PortableSelectedParameter> ordered,
            Set<PortableSelectedParameter> path, int depth) {
        if (node == null) throw new IllegalArgumentException("Null logger selection");
        if (depth > 32 || path.contains(node)) throw new IllegalArgumentException("Invalid/deep calculated dependency graph");
        if (ordered.contains(node)) return;
        if (ordered.size() + path.size() >= 4096) throw new IllegalArgumentException("Logger dependency graph exceeds 4096 nodes");
        path.add(node);
        for (PortableSelectedParameter input : node.getInputs().values()) collect(input, ordered, path, depth + 1);
        path.remove(node); ordered.add(node);
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
