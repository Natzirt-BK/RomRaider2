/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.analysis;

import java.util.List;
import java.util.Objects;

/** Portable inputs only: no captured data, file paths, sample indices or approval. */
public record FuelAnalysisSetup(Kind kind, Channel x, Channel y, Channel correction,
        double binWidth, double stoichAfr, double fuelDensity, List<Filter> filters) {
    public enum Kind { MAF, INJECTOR }

    public record Channel(String label, String units) {
        public Channel {
            if (label == null || label.isBlank() || label.length() > 512
                    || units == null || units.length() > 128
                    || invalidText(label) || invalidText(units)) {
                throw new IllegalArgumentException("Invalid analysis channel identity");
            }
        }
        private static boolean invalidText(String text) {
            return text.codePoints().anyMatch(value -> Character.isISOControl(value)
                    || value >= 0xd800 && value <= 0xdfff);
        }
        public static Channel of(LogChannel value) {
            if (value == null || value.isTimeChannel()) {
                throw new IllegalArgumentException("Map every required analysis channel");
            }
            return new Channel(value.getLabel(), value.getUnits());
        }
        /** Exact identity only. Duplicate headers are deliberately unresolved. */
        public LogChannel resolve(LogDataset data) {
            LogChannel match = null;
            for (LogChannel channel : data.getChannels()) {
                if (!channel.isTimeChannel() && label.equals(channel.getLabel())
                        && units.equals(channel.getUnits())) {
                    if (match != null) return null;
                    match = channel;
                }
            }
            return match;
        }
    }

    public record Filter(Channel channel, double minimum, double maximum) {
        public Filter {
            Objects.requireNonNull(channel, "Filter channel");
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
                throw new IllegalArgumentException("Filter limits must be finite and ordered");
            }
        }
    }

    public FuelAnalysisSetup {
        Objects.requireNonNull(kind, "Analysis kind");
        Objects.requireNonNull(x, "X channel"); Objects.requireNonNull(y, "Y channel");
        if (kind == Kind.MAF && correction == null || kind == Kind.INJECTOR && correction != null) {
            throw new IllegalArgumentException("Analysis mappings do not match the setup kind");
        }
        if (x.equals(y) || x.equals(correction) || y.equals(correction)) {
            throw new IllegalArgumentException("Choose distinct required analysis channels");
        }
        positive(binWidth, "Bin width"); positive(stoichAfr, "Stoichiometric AFR");
        positive(fuelDensity, "Fuel density");
        filters = List.copyOf(filters);
        if (filters.size() > 3) throw new IllegalArgumentException("At most three analysis filters are supported");
    }

    private static void positive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
